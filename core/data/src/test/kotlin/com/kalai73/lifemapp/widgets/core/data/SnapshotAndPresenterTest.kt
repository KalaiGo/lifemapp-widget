package com.kalai73.lifemapp.widgets.core.data

import androidx.datastore.core.DataStoreFactory
import com.kalai73.lifemapp.widgets.core.crypto.Aes
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.SupabaseAuthApi
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.cache.CachedStateSerializer
import com.kalai73.lifemapp.widgets.core.data.presentation.DateLabels
import com.kalai73.lifemapp.widgets.core.data.presentation.DayKind
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetPresenter
import com.kalai73.lifemapp.widgets.core.data.repository.SnapshotRepository
import com.kalai73.lifemapp.widgets.core.data.repository.SyncResult
import com.kalai73.lifemapp.widgets.core.data.repository.TaskRepository
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import com.kalai73.lifemapp.widgets.core.data.secure.Session
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.Counts
import com.kalai73.lifemapp.widgets.core.model.Domains
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.Event
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import com.kalai73.lifemapp.widgets.core.model.Shortcut
import com.kalai73.lifemapp.widgets.core.model.Shortcuts
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.SnapshotSettings
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.Task
import com.kalai73.lifemapp.widgets.core.model.Weather
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class SnapshotAndPresenterTest {
    private val dir = Files.createTempDirectory("cache").toFile()
    private val cache = CacheStore(DataStoreFactory.create(CachedStateSerializer) { File(dir, "c.json") })
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val secrets = SecretStore(context, FakeCipher, File(dir, "s.bin"))
    private val madrid = ZoneId.of("Europe/Madrid")
    /** Sat 31 Oct 2026, 10:00 in Madrid */
    private val now = ZonedDateTime.of(2026, 10, 31, 10, 0, 0, 0, madrid).toInstant().toEpochMilli()

    private fun api(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): LifeMappApi {
        val http = mockClient(handler)
        return LifeMappApi(http, TestConfig, AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { 0 }))
    }

    private suspend fun signIn() = secrets.update { it.copy(session = Session("a", "r", 99_999, "u1")) }

    @Test
    fun `failed sections keep their cached values`() {
        val old = Snapshot(
            events = listOf(Event("e", "Old event", "2026-10-31T09:00:00Z", date = "2026-10-31")),
            counts = Counts(events = 1, mail = 4, mailConnected = true),
            weather = Weather(high = 20),
        )
        val fresh = Snapshot(counts = Counts(tasks = 2), failedSections = listOf("events", "mail"))
        val merged = SnapshotRepository.merge(old, fresh)
        assertEquals("Old event", merged.events.single().title)
        assertEquals(Counts(events = 1, tasks = 2, mail = 4, mailConnected = true), merged.counts)
        assertNull(merged.weather) // weather didn't fail: the fresh (empty) value wins
    }

    @Test
    fun `refresh stores the snapshot and records network errors`() = runTest {
        signIn()
        var fail = false
        val repo = SnapshotRepository(api { if (fail) throw java.io.IOException("offline") else json("""{"today":"2026-10-31"}""") }, cache, clock = { 5 })
        assertEquals(SyncResult.Success, repo.refresh())
        assertEquals("2026-10-31", cache.current().snapshot?.today)
        assertEquals(5, cache.current().lastSuccessAt)
        fail = true
        assertEquals(SyncResult.Retry(SyncError.NETWORK), repo.refresh())
        assertEquals(SyncError.NETWORK, cache.current().lastError)
        assertFalse(cache.current().syncing)
        assertEquals("2026-10-31", cache.current().snapshot?.today)
    }

    @Test
    fun `a syncing flag left behind by a killed worker expires`() {
        val state = CachedState(snapshot = Snapshot(), syncing = true, syncStartedAt = now - CachedState.SYNC_TIMEOUT_MS - 1)
        assertFalse(WidgetPresenter.present(state, AuthState.SIGNED_IN, null, now, madrid).syncing)
        val running = state.copy(syncStartedAt = now - 5_000)
        assertTrue(WidgetPresenter.present(running, AuthState.SIGNED_IN, null, now, madrid).syncing)
    }

    @Test
    fun `a cancelled sync clears the spinner`() = runTest {
        signIn()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = SnapshotRepository(api {
            started.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }, cache)
        val job = launch { repo.refresh() }
        started.await()
        assertTrue(cache.current().syncing)
        job.cancel()
        job.join()
        assertFalse(cache.current().syncing)
    }

    @Test
    fun `refresh without a session reports signed out`() = runTest {
        val repo = SnapshotRepository(api { json("{}") }, cache)
        assertEquals(SyncResult.SignedOut, repo.refresh())
    }

    @Test
    fun `upcoming tasks follow today's in the web's segments and roll over at midnight`() {
        val snapshot = Snapshot(
            today = "2026-10-31",
            tasks = listOf(
                Task("later", "In five days", dueDate = "2026-11-05", segment = Task.SEGMENT_LATER),
                Task("tomorrow", "Tomorrow", dueDate = "2026-11-01", segment = Task.SEGMENT_TOMORROW),
                Task("today", "Today", dueDate = "2026-10-31"),
                Task("week", "Undated in In 7 days", segment = Task.SEGMENT_LATER),
            ),
            counts = Counts(tasks = 1),
        )
        val ui = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf("today", "tomorrow", "later", "week"), ui.tasks.map { it.id })
        assertEquals(listOf(DayKind.TODAY, DayKind.TOMORROW, DayKind.OTHER, DayKind.OTHER), ui.tasks.map { it.day })
        assertEquals(listOf(false, true, true, true), ui.tasks.map { it.upcoming })
        assertEquals(1, ui.tasksCount)

        // The next morning, before a sync: tomorrow's task is today's
        val nextDay = ZonedDateTime.of(2026, 11, 1, 8, 0, 0, 0, madrid).toInstant().toEpochMilli()
        val rolled = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, nextDay, madrid)
        assertEquals(listOf("today", "tomorrow", "later", "week"), rolled.tasks.map { it.id })
        assertEquals(listOf(DayKind.TODAY, DayKind.TODAY, DayKind.OTHER, DayKind.OTHER), rolled.tasks.map { it.day })
        assertEquals(2, rolled.tasksCount)
    }

    @Test
    fun `tasks from connected task managers show their local time and can't be completed`() {
        val snapshot = Snapshot(
            today = "2026-10-31",
            tasks = listOf(
                Task("local:1", "Local", dueDate = "2026-10-31"),
                Task("todoist:7", "Call Bob", dueDate = "2026-10-31", dueTime = "18:00", source = "todoist"),
            ),
            counts = Counts(tasks = 2),
        )
        val ui = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf(null, "18:00"), ui.tasks.map { it.dueLabel })
        assertEquals(listOf(true, false), ui.tasks.map { it.completable })
    }

    @Test
    fun `completing a task hides it at once and removes it once confirmed`() = runTest {
        signIn()
        cache.update { CachedState(snapshot = Snapshot(tasks = listOf(Task("t1", "A"), Task("t2", "B")), counts = Counts(tasks = 2))) }
        val calls = mutableListOf<String>()
        val repo = TaskRepository(api { req -> calls += "${req.method.value} ${req.url.encodedPath}"; json("{}") }, cache)

        repo.markCompletedLocally("t1")
        val hidden = WidgetPresenter.present(cache.current(), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf("t2"), hidden.tasks.map { it.id })
        assertEquals(1, hidden.tasksCount)

        assertTrue(repo.complete("t1"))
        assertEquals(listOf("PUT /api/tasks/t1"), calls)
        assertEquals(listOf("t2"), cache.current().snapshot!!.tasks.map { it.id })
        assertEquals(1, cache.current().snapshot!!.counts.tasks)
        assertTrue(cache.current().pendingCompletions.isEmpty())
    }

    @Test
    fun `a failed completion brings the task back and flags the failure`() = runTest {
        signIn()
        cache.update { CachedState(snapshot = Snapshot(tasks = listOf(Task("t1", "A")), counts = Counts(tasks = 1))) }
        val repo = TaskRepository(api { json("{}", HttpStatusCode.InternalServerError) }, cache)
        repo.markCompletedLocally("t1")
        assertFalse(repo.complete("t1"))
        repo.revert("t1")
        val ui = WidgetPresenter.present(cache.current(), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf("t1"), ui.tasks.map { it.id })
        assertTrue(ui.completionFailed)
    }

    @Test
    fun `presenter decrypts titles, ratings and quick links when unlocked and masks them when locked`() {
        val key = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        fun enc(text: String) = Aes.encrypt(text, key).let { """{"encrypted":"${it.encrypted}","iv":"${it.iv}","tag":"${it.tag}"}""" }
        val links = LifeMappJson.encodeToString(ListSerializer(Shortcut.serializer()), listOf(
            Shortcut("b", "Second", "https://b", position = 2), Shortcut("a", "First", "https://a", position = 1),
        ))
        val state = CachedState(
            lastSuccessAt = now - 60_000,
            snapshot = Snapshot(
                today = "2026-10-31",
                settings = SnapshotSettings(encryptionEnabled = true, timezone = "Europe/Madrid", language = "es", dateFormat = "dd/MM/yyyy"),
                tasks = listOf(Task("t1", null, encryptedTitle = enc("Secret task"), dueDate = "2026-10-29", overdue = true)),
                domains = Domains(ratings = listOf(8), encryptedRatings = listOf(enc("6"))),
                shortcuts = Shortcuts(encrypted = enc(links)),
            ),
        )

        val unlocked = WidgetPresenter.present(state, AuthState.SIGNED_IN, key, now, madrid)
        assertEquals(EncryptionState.UNLOCKED, unlocked.encryption)
        assertEquals("Secret task", unlocked.tasks.single().title)
        assertEquals("Ju. 29.10.2026", unlocked.tasks.single().dueLabel)
        assertTrue(unlocked.tasks.single().overdue)
        assertEquals(7.0, unlocked.averageRating!!, 0.0)
        assertEquals(listOf("a", "b"), unlocked.shortcuts.map { it.id })
        assertEquals("Sá. 31.10.2026", unlocked.todayLabel)
        assertFalse(unlocked.stale)

        val locked = WidgetPresenter.present(state, AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(EncryptionState.LOCKED, locked.encryption)
        assertNull(locked.tasks.single().title)
        assertEquals(8.0, locked.averageRating!!, 0.0)
        assertTrue(locked.shortcutsLocked)
        assertTrue(locked.shortcuts.isEmpty())
    }

    @Test
    fun `presenter builds the Integrity card, decrypting names and the rating when unlocked`() {
        val key = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        fun enc(text: String) = Aes.encrypt(text, key).let { """{"encrypted":"${it.encrypted}","iv":"${it.iv}","tag":"${it.tag}"}""" }
        val integrity = Integrity(
            domainId = "d1",
            encryptedName = enc("INTEGRITY"),
            infoBox = "Capture & Organize",
            encryptedRating = enc("8"),
            topics = listOf(
                Topic("s1", name = "Reminders", reminders = true, collapsed = true, count = 0),
                Topic("s2", encryptedName = enc("Topic 1"), collapsed = true, count = 11, items = listOf(
                    TopicItem("i1", encryptedName = enc("Item"), warning = true),
                    TopicItem("i2", name = "Plain"),
                )),
            ),
            warnings = 3,
        )
        val state = CachedState(snapshot = Snapshot(settings = SnapshotSettings(encryptionEnabled = true), integrity = integrity))

        val unlocked = WidgetPresenter.present(state, AuthState.SIGNED_IN, key, now, madrid).integrity!!
        assertEquals("INTEGRITY", unlocked.name)
        assertEquals(8, unlocked.rating)
        assertEquals(listOf(1, 2), unlocked.topics.map { it.number })
        assertEquals("Topic 1", unlocked.topics[1].name)
        assertEquals(listOf("Item", "Plain"), unlocked.topics[1].items.map { it.name })
        assertTrue(unlocked.topics[1].items[0].warning)
        assertEquals(9, unlocked.topics[1].more)
        assertEquals(3, unlocked.warnings)
        // Collapsed as on the web, except Reminders, which is always expanded
        assertEquals(listOf(false, true), unlocked.topics.map { it.collapsed })

        val locked = WidgetPresenter.present(state, AuthState.SIGNED_IN, null, now, madrid).integrity!!
        assertNull(locked.name)
        assertNull(locked.rating)
        assertNull(locked.topics[1].name)
        assertEquals(listOf(null, "Plain"), locked.topics[1].items.map { it.name })
        assertEquals("Capture & Organize", locked.infoBox)

        // A failed integrity section keeps the cached card
        val merged = SnapshotRepository.merge(Snapshot(integrity = integrity), Snapshot(failedSections = listOf("integrity")))
        assertEquals(integrity, merged.integrity)
    }

    @Test
    fun `events are grouped by day and yesterday's drop out after midnight`() {
        val state = CachedState(
            lastSuccessAt = now - 3 * 60 * 60 * 1000L,
            snapshot = Snapshot(
                today = "2026-10-30",
                settings = SnapshotSettings(timezone = "Europe/Madrid", timeFormat = "24h"),
                counts = Counts(events = 9),
                events = listOf(
                    Event("y", "Yesterday", "2026-10-30T08:00:00Z", date = "2026-10-30"),
                    Event("trip", "Trip", "2026-10-29", end = "2026-11-02", allDay = true, date = "2026-10-30"),
                    Event("s", "Standup", "2026-10-31T08:00:00Z", end = "2026-10-31T08:15:00Z", date = "2026-10-31"),
                    Event("n", "Dinner", "2026-11-01T19:00:00Z", date = "2026-11-01", htmlLink = "https://cal/n"),
                ),
            ),
        )
        val ui = WidgetPresenter.present(state, AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf(DayKind.TODAY, DayKind.TOMORROW), ui.eventDays.map { it.kind })
        assertEquals(listOf("trip", "s"), ui.eventDays[0].events.map { it.id })
        assertEquals("09:00 – 09:15", ui.eventDays[0].events[1].timeLabel)
        assertNull(ui.eventDays[0].events[0].timeLabel)
        assertEquals("https://cal/n", ui.eventDays[1].events.single().link)
        // The snapshot was made yesterday, so its count doesn't apply
        assertEquals(2, ui.eventsCount)
        assertTrue(ui.stale)
    }

    @Test
    fun `date labels follow the web format tokens`() {
        val d = LocalDate.of(2026, 9, 23)
        assertEquals("We. 23/09/2026", DateLabels.dayAndDate(d, "dd/MM/yyyy", "en"))
        assertEquals("Mi. 23 Sep 2026", DateLabels.dayAndDate(d, "dd MMM yyyy", "de"))
        assertEquals("We. 09/23/2026", DateLabels.dayAndDate(d, "MM/dd/yyyy", "fr"))
        assertEquals("We. 2026-09-23", DateLabels.dayAndDate(d, "yyyy-MM-dd", null))
    }

    @Test
    fun `signed out renders nothing personal`() {
        val state = CachedState(snapshot = Snapshot(settings = SnapshotSettings(username = "Ana"), weather = Weather(high = 3)))
        val ui = WidgetPresenter.present(state, AuthState.SIGNED_OUT, null, now, madrid)
        assertEquals(AuthState.SIGNED_OUT, ui.authState)
        assertNull(ui.username)
        assertNull(ui.weather)
    }
}
