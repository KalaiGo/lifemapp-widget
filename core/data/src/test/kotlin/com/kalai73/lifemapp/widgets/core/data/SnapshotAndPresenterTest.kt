package com.kalai73.lifemapp.widgets.core.data

import kotlinx.serialization.builtins.serializer
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
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
import com.kalai73.lifemapp.widgets.core.data.repository.ItemRepository
import com.kalai73.lifemapp.widgets.core.data.repository.ScoringRepository
import com.kalai73.lifemapp.widgets.core.data.repository.SnapshotRepository
import com.kalai73.lifemapp.widgets.core.data.repository.SyncResult
import com.kalai73.lifemapp.widgets.core.data.repository.TaskRepository
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import com.kalai73.lifemapp.widgets.core.data.secure.Session
import com.kalai73.lifemapp.widgets.core.model.MailTag
import com.kalai73.lifemapp.widgets.core.model.Mail
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.Counts
import com.kalai73.lifemapp.widgets.core.model.Domains
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.Event
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.ScoringDomain
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import com.kalai73.lifemapp.widgets.core.model.Shortcut
import com.kalai73.lifemapp.widgets.core.model.Shortcuts
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.SnapshotSettings
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.Task
import com.kalai73.lifemapp.widgets.core.model.Weather
import com.kalai73.lifemapp.widgets.core.model.WeatherDay
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
    fun `the other Today domains are presented in order, kept when their section fails and edited in the cache`() = runTest {
        val serenity = Integrity(
            domainId = "d2", position = 2, name = "SERENITY", rating = 6.0,
            topics = listOf(Topic("s2", name = "Calm", collapsed = false, count = 1, items = listOf(TopicItem("i2", name = "Breathe")))),
        )
        val tenacity = Integrity(domainId = "d5", position = 5, name = "TENACITY", rating = 2.0)
        val snapshot = Snapshot(today = "2026-10-31", integrity = Integrity(domainId = "d1", name = "INTEGRITY"), todayDomains = listOf(serenity, tenacity))

        val ui = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf("d2" to 2, "d5" to 5), ui.todayDomains.map { it.domainId to it.position })
        assertEquals(listOf("SERENITY", "TENACITY"), ui.todayDomains.map { it.name })
        assertEquals(6, ui.todayDomains[0].rating)
        assertEquals(listOf("Breathe"), ui.todayDomains[0].topics.single().items.map { it.name })
        assertEquals(1, ui.integrity!!.position)

        val failed = SnapshotRepository.merge(snapshot, Snapshot(failedSections = listOf("todayDomains")))
        assertEquals(listOf("d2", "d5"), failed.todayDomains.map { it.domainId })
        assertTrue(SnapshotRepository.merge(snapshot, Snapshot(failedSections = listOf("mail"))).todayDomains.isEmpty())

        // Collapsing a topic and deleting an item of Serenity update the cached card
        cache.update { CachedState(snapshot = snapshot) }
        signIn()
        val items = ItemRepository(api { json("{}") }, cache, dataKey = { null })
        items.setTopicCollapsed("s2", true)
        assertTrue(cache.current().snapshot!!.todayDomains[0].topics.single().collapsed)
        items.delete("i2")
        assertEquals(0, cache.current().snapshot!!.todayDomains[0].topics.single().items.size)
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
    fun `mail tags are listed as the web E-Mails widget sends them, later horizons dimmed`() {
        val snapshot = Snapshot(
            today = "2026-10-31",
            mail = Mail(
                inboxUrl = "https://mail.google.com/#inbox",
                tags = listOf(
                    MailTag(name = null, tier = MailTag.TIER_INBOX, icon = "inbox", color = "#4CAF50", unread = 3, total = 12, url = "https://mail.google.com/#inbox"),
                    MailTag(name = "In 7 days", tier = MailTag.TIER_7_DAYS, icon = "clock", color = "#FF9800", unread = 0, total = 4, url = ""),
                    MailTag(name = "Receipts", tier = MailTag.TIER_OTHER, icon = "tag", unread = 1, total = 40, url = "https://mail.google.com/#label/Receipts"),
                ),
            ),
        )
        val ui = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf(null, "In 7 days", "Receipts"), ui.mailTags.map { it.name })
        assertEquals(listOf(1f, 0.85f, 0.65f), ui.mailTags.map { it.fade })
        assertEquals(listOf(3 to 12, 0 to 4, 1 to 40), ui.mailTags.map { it.unread to it.total })
        // A blank link opens the web mail page instead
        assertEquals(null, ui.mailTags[1].url)
    }

    @Test
    fun `events and tasks carry what their details modals show, encrypted descriptions decrypted when unlocked`() {
        val dataKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val encrypted = Aes.encrypt("<p>Secret notes</p>", dataKey).let { """{"encrypted":"${it.encrypted}","iv":"${it.iv}","tag":"${it.tag}"}""" }
        val snapshot = Snapshot(
            today = "2026-10-31",
            settings = SnapshotSettings(encryptionEnabled = true),
            events = listOf(
                Event("e1", "Standup", start = "2026-10-31T09:00:00Z", end = "2026-10-31T09:15:00Z", date = "2026-10-31",
                    location = "Room 4", description = "Agenda: https://example.com", status = "tentative"),
                Event("e2", "Trip", start = "2026-11-01", end = "2026-11-04", allDay = true, date = "2026-11-01"),
            ),
            tasks = listOf(
                Task("t1", "Plain", dueDate = "2026-10-31", description = "<p>Bring the keys</p>"),
                Task("t2", "Secret", dueDate = "2026-11-01", segment = Task.SEGMENT_TOMORROW, encryptedDescription = encrypted),
            ),
        )
        val ui = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, dataKey, now, madrid)
        val events = ui.eventDays.flatMap { it.events }
        assertEquals("Room 4", events[0].location)
        assertEquals("Agenda: https://example.com", events[0].description)
        assertEquals("tentative", events[0].status)
        assertEquals("2026-10-31T09:00:00Z", events[0].start)
        assertEquals(DateLabels.dayAndDate(LocalDate.of(2026, 10, 31), DateLabels.DATE_FORMAT, "en"), events[0].dateLabel)
        // All-day end dates are exclusive: the trip lasts 1–3 November
        val trip = "${DateLabels.dayAndDate(LocalDate.of(2026, 11, 1), DateLabels.DATE_FORMAT, "en")} – " +
            DateLabels.dayAndDate(LocalDate.of(2026, 11, 3), DateLabels.DATE_FORMAT, "en")
        assertEquals(trip, events[1].dateLabel)

        assertEquals(listOf("<p>Bring the keys</p>", "<p>Secret notes</p>"), ui.tasks.map { it.description })
        assertEquals(listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1)), ui.tasks.map { it.dueDate })

        // Locked: the encrypted description is withheld and flagged
        val locked = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, now, madrid)
        assertNull(locked.tasks[1].description)
        assertTrue(locked.tasks[1].descriptionLocked)
        assertFalse(locked.tasks[0].descriptionLocked)
    }

    @Test
    fun `tasks from connected task managers show their local time, Todoist ones can be completed`() {
        val snapshot = Snapshot(
            today = "2026-10-31",
            tasks = listOf(
                Task("local:1", "Local", dueDate = "2026-10-31"),
                Task("todoist:7", "Call Bob", dueDate = "2026-10-31", dueTime = "18:00", source = "todoist"),
                Task("google-tasks:3", "Groceries", dueDate = "2026-10-31", source = "google-tasks"),
            ),
            counts = Counts(tasks = 2),
        )
        val ui = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf(null, "18:00", null), ui.tasks.map { it.dueLabel })
        assertEquals(listOf(true, true, false), ui.tasks.map { it.completable })
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
    fun `a Todoist task is closed at Todoist with the user's API key, decrypted when encryption is on`() = runTest {
        signIn()
        val dataKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val encryptedKey = Aes.encrypt("todoist-secret", dataKey).let { """{"encrypted":"${it.encrypted}","iv":"${it.iv}","tag":"${it.tag}"}""" }
        cache.update { CachedState(snapshot = Snapshot(tasks = listOf(Task("todoist:42", "Call Bob", source = "todoist")), counts = Counts(tasks = 1))) }
        val calls = mutableListOf<String>()
        val repo = TaskRepository(
            api { req ->
                calls += "${req.method.value} ${req.url.host}${req.url.encodedPath} ${req.headers[HttpHeaders.Authorization]?.takeIf { "todoist" in req.url.host }.orEmpty()}".trim()
                if (req.url.encodedPath.endsWith("lm_user_settings")) {
                    json("""[{"task_api_key":null,"encrypted_api_key":${LifeMappJson.encodeToString(String.serializer(), encryptedKey)}}]""")
                } else {
                    respond("", HttpStatusCode.NoContent)
                }
            },
            cache,
            userId = { "u1" },
            dataKey = { dataKey },
        )

        assertTrue(repo.complete("todoist:42"))
        assertEquals("POST api.todoist.com/api/v1/tasks/42/close Bearer todoist-secret", calls.last())
        assertTrue(cache.current().snapshot!!.tasks.isEmpty())

        // Locked (no data key): nothing to send, the task comes back after the retries
        cache.update { CachedState(snapshot = Snapshot(tasks = listOf(Task("todoist:43", "B", source = "todoist")))) }
        val locked = TaskRepository(api { json("""[{"encrypted_api_key":"x"}]""") }, cache, userId = { "u1" })
        assertFalse(locked.complete("todoist:43"))
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
        // The snapshot was made yesterday, so its count doesn't apply; the Standup is over, so only the Trip counts
        assertTrue(ui.eventDays[0].events[1].past)
        assertEquals(1, ui.eventsCount)
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
    fun `the weather forecast lists the days after today with short weekday names`() {
        val weather = Weather(
            unit = "celsius", high = 21, low = 12,
            forecast = listOf(
                WeatherDay("2026-10-31", 21, 12, "Stale: today", "rain"),
                WeatherDay("2026-11-01", 18, 10, "Rain", "rain"),
                WeatherDay("2026-11-02", 19, 11, "Clear", "clear-day"),
                WeatherDay("bad-date", 1, 1, "", ""),
            ),
        )
        val snapshot = Snapshot(settings = SnapshotSettings(language = "de"), weather = weather)
        val forecast = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, now, madrid).weather!!.forecast
        assertEquals(listOf("2026-11-01", "2026-11-02").map { java.time.LocalDate.parse(it) }.map { DateLabels.shortDayName(it, "de") }, forecast.map { it.day })
        assertEquals(listOf("18°", "19°"), forecast.map { it.high })
        assertEquals("clear-day", forecast[1].icon)
    }

    @Test
    fun `signed out renders nothing personal`() {
        val state = CachedState(snapshot = Snapshot(settings = SnapshotSettings(username = "Ana"), weather = Weather(high = 3)))
        val ui = WidgetPresenter.present(state, AuthState.SIGNED_OUT, null, now, madrid)
        assertEquals(AuthState.SIGNED_OUT, ui.authState)
        assertNull(ui.username)
        assertNull(ui.weather)
    }

    @Test
    fun `task color tier follows the web - priority, then horizon list, then due date`() {
        assertEquals(4, WidgetPresenter.colorTier(4, null, null))
        assertEquals(3, WidgetPresenter.colorTier(3, "Today", null)) // an explicit priority wins
        assertEquals(4, WidgetPresenter.colorTier(1, "Heute", null))
        assertEquals(3, WidgetPresenter.colorTier(1, "In 7 days", null))
        assertEquals(4, WidgetPresenter.colorTier(1, null, -2)) // overdue
        assertEquals(4, WidgetPresenter.colorTier(1, "LIFE MAPP", 0)) // a plain list name falls through to the due date
        assertEquals(3, WidgetPresenter.colorTier(1, null, 5))
        assertEquals(2, WidgetPresenter.colorTier(1, null, 20))
        assertEquals(1, WidgetPresenter.colorTier(1, null, 90))
        assertEquals(1, WidgetPresenter.colorTier(1, null, null))
    }

    @Test
    fun `task tag is the web provider badge`() {
        assertEquals("LIFE MAPP" to "#10B981", WidgetPresenter.providerTag("local", "Today"))
        assertEquals("Todoist-LIF" to "#EF4444", WidgetPresenter.providerTag("todoist", "LIFE MAPP"))
        assertEquals("Todoist" to "#EF4444", WidgetPresenter.providerTag("todoist", null))
    }

    @Test
    fun `device color scheme overrides the web's and survives unknown ids`() {
        val web = CachedState(snapshot = com.kalai73.lifemapp.widgets.core.model.Snapshot(
            settings = com.kalai73.lifemapp.widgets.core.model.SnapshotSettings(colorScheme = "teal"),
        ))
        assertEquals(com.kalai73.lifemapp.widgets.core.model.ColorSchemeName.TEAL, web.colorScheme)
        assertEquals(com.kalai73.lifemapp.widgets.core.model.ColorSchemeName.ROSE, web.copy(colorSchemeOverride = "rose").colorScheme)
        assertEquals(com.kalai73.lifemapp.widgets.core.model.ColorSchemeName.FOREST, CachedState(colorSchemeOverride = "forest").colorScheme)
        assertEquals(com.kalai73.lifemapp.widgets.core.model.ColorSchemeName.DEFAULT, CachedState().colorScheme)
    }

    @Test
    fun `the scoring section follows the web's hidden rules, last scoring and colors`() {
        val snapshot = Snapshot(
            today = "2026-10-31",
            integrity = Integrity(domainId = "d1", name = "INTEGRITY", rating = 8.0),
            todayDomains = listOf(
                Integrity(domainId = "d2", position = 2, name = "SERENITY", rating = 7.0),
                Integrity(domainId = "d3", position = 3, name = "VITALITY", rating = 0.0),
                Integrity(domainId = "d5", position = 5, name = "TENACITY", rating = 4.0),
            ),
        )
        val domains = listOf(ScoringDomain("d1", 1, "INTEGRITY"), ScoringDomain("d2", 2, "SERENITY", color = "violet"), ScoringDomain("d3", 3, "VITALITY"), ScoringDomain("d5", 5, "TENACITY"))

        // Not read yet: nothing hidden, nothing toggles
        val unread = WidgetPresenter.present(CachedState(snapshot = snapshot), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(false, unread.scoring!!.ready)
        assertEquals(listOf("d2", "d3", "d5"), unread.todayDomains.map { it.domainId })

        // Never set: every hidable domain hidden; Integrity and Tenacity stay
        val never = WidgetPresenter.present(CachedState(snapshot = snapshot, scoring = Scoring(domains = domains)), AuthState.SIGNED_IN, null, now, madrid)
        assertEquals(listOf(false, true, true, false), never.scoring!!.domains.map { it.hidden })
        assertEquals(listOf("d5"), never.todayDomains.map { it.domainId })
        assertTrue(never.scoring!!.allHidableHidden)
        assertEquals(null, never.scoring!!.daysSinceScored)
        assertTrue(never.scoring!!.scoringDue)

        // A list: only its domains hidden; custom color, else the score's, none when unrated
        val listed = WidgetPresenter.present(
            CachedState(snapshot = snapshot, scoring = Scoring(hiddenDomainIds = listOf("d3", "d1"), lastCompleted = "2026-10-29T22:30:00.000Z", domains = domains)),
            AuthState.SIGNED_IN, null, now, madrid,
        )
        val s = listed.scoring!!
        assertEquals(listOf(false, false, true, false), s.domains.map { it.hidden })
        assertEquals(listOf("green", "violet", null, "orange"), s.domains.map { it.colorName })
        assertEquals(listOf("d2", "d5"), listed.todayDomains.map { it.domainId })
        assertEquals(1, s.urgency)
    }

    @Test
    fun `days since the last scoring count local days and accept the old month format`() {
        val today = java.time.LocalDate.of(2026, 11, 1)
        // 23:30 UTC on Oct 31 is already Nov 1 in Madrid
        assertEquals(0, WidgetPresenter.daysSince("2026-10-31T23:30:00.000Z", today, madrid))
        assertEquals(3, WidgetPresenter.daysSince("2026-10-29T08:00:00+00:00", today, madrid))
        assertEquals(31, WidgetPresenter.daysSince("2026-10", today, madrid))
        assertEquals(null, WidgetPresenter.daysSince("garbage", today, madrid))
    }

    @Test
    fun `hiding a domain materializes a never-set list and keeps the other preferences`() = runTest {
        signIn()
        val domains = listOf(ScoringDomain("d1", 1, "INTEGRITY"), ScoringDomain("d2", 2, "SERENITY"), ScoringDomain("d3", 3, "VITALITY"), ScoringDomain("d5", 5, "TENACITY"))
        cache.update { it.copy(scoring = Scoring(domains = domains)) }
        val patches = mutableListOf<String>()
        val repo = ScoringRepository(
            api { request ->
                if (request.method == io.ktor.http.HttpMethod.Patch) {
                    patches += (request.body as io.ktor.http.content.TextContent).text
                    json("")
                } else {
                    json("""[{"preferences":{"theme":"x"}}]""")
                }
            },
            cache, userId = { "u1" }, dataKey = { null },
        )
        // Never set: Serenity and Vitality hidden; showing Serenity leaves Vitality hidden
        repo.setHidden(mapOf("d2" to false))
        assertEquals(listOf("d3"), cache.current().scoring!!.hiddenDomainIds)
        assertEquals("""{"preferences":{"theme":"x","hidden_domain_ids":["d3"]}}""", patches.single())
        // Integrity can't be hidden
        repo.setHidden(mapOf("d1" to true, "d2" to true))
        assertEquals(listOf("d3", "d2"), cache.current().scoring!!.hiddenDomainIds)
    }

    @Test
    fun `rating sends every readable life domain, records the time and updates the cache`() = runTest {
        signIn()
        cache.update {
            it.copy(snapshot = Snapshot(integrity = Integrity(domainId = "d1", rating = 3.0), domains = Domains(averageRating = 4.0, ratings = listOf(3, 5))))
        }
        var bulk: String? = null
        var completed: String? = null
        val repo = ScoringRepository(
            api { request ->
                when {
                    request.url.encodedPath.endsWith("/scores/bulk") -> { bulk = (request.body as io.ktor.http.content.TextContent).text; json("{}") }
                    request.method == io.ktor.http.HttpMethod.Patch -> { completed = (request.body as io.ktor.http.content.TextContent).text; json("") }
                    else -> json("""[{"id":"d1","position":1,"rating":3},{"id":"d12","position":12,"rating":5},{"id":"dx","position":13,"rating":0,"encrypted_rating":"sealed"}]""")
                }
            },
            cache, userId = { "u1" }, dataKey = { null }, clock = { 0 },
        )
        assertTrue(repo.rate(mapOf("d1" to 9), encrypted = false))
        // The encrypted sibling this device can't read is left out
        assertEquals("""{"entries":[{"domainId":"d1","rating":9},{"domainId":"d12","rating":5}]}""", bulk)
        assertEquals("""{"monthly_scoring_last_completed":"1970-01-01T00:00:00Z"}""", completed)
        val state = cache.current()
        assertEquals(9.0, state.snapshot!!.integrity!!.rating!!, 0.0)
        assertEquals(7.0, state.snapshot!!.domains.averageRating!!, 0.0)
        assertEquals("1970-01-01T00:00:00Z", state.scoring!!.lastCompleted)
        // Encrypted account without the key: nothing sent
        assertFalse(repo.rate(mapOf("d1" to 2), encrypted = true))
    }

    @Test
    fun `the scoring read skips the dropped user_settings column and reads the events limits`() = runTest {
        signIn()
        val selects = mutableListOf<String>()
        val api = api { request ->
            if (request.url.encodedPath.endsWith("lm_user_settings")) {
                selects += request.url.parameters["select"].orEmpty()
                json("""[{"preferences":{"hidden_domain_ids":["d2"],"days_ahead":5,"max_events":4},"monthly_scoring_last_completed":"2026-10-29T08:00:00Z"}]""")
            } else json("[]")
        }
        val scoring = api.scoring("u1")
        assertFalse(selects.single().contains("user_settings"))
        assertEquals(listOf("d2"), scoring.hiddenDomainIds)
        assertEquals("2026-10-29T08:00:00Z", scoring.lastCompleted)
        assertEquals(5, scoring.eventsDaysAhead)
        assertEquals(4, scoring.eventsMaxEvents)
    }

    @Test
    fun `the events card keeps the user's days, the latest 3 past events of today and the event cap`() {
        val today = java.time.LocalDate.of(2026, 11, 2)
        fun ev(id: String, past: Boolean = false) =
            com.kalai73.lifemapp.widgets.core.data.presentation.EventUi(id, id, null, false, "#000", null, false, null, past)
        fun day(offset: Long, kind: DayKind, vararg events: com.kalai73.lifemapp.widgets.core.data.presentation.EventUi) =
            com.kalai73.lifemapp.widgets.core.data.presentation.EventDayUi(today.plusDays(offset), kind, "d$offset", events.toList())
        val days = listOf(
            day(0, DayKind.TODAY, ev("p1", true), ev("p2", true), ev("p3", true), ev("p4", true), ev("n1")),
            day(1, DayKind.TOMORROW, ev("t1"), ev("t2")),
            day(4, DayKind.OTHER, ev("o1")),
        )
        val limits = com.kalai73.lifemapp.widgets.core.data.presentation.EventLimits

        val wide = limits.apply(days, today, daysAhead = 7, maxEvents = 20)
        assertEquals(listOf("p2", "p3", "p4", "n1", "t1", "t2", "o1"), wide.days.flatMap { d -> d.events.map { it.id } })
        assertEquals(0, wide.more)

        // 3 days: day 4 is out of range; a cap of 5 cuts tomorrow and counts the rest
        val capped = limits.apply(days, today, daysAhead = 3, maxEvents = 5)
        assertEquals(listOf("p2", "p3", "p4", "n1", "t1"), capped.days.flatMap { d -> d.events.map { it.id } })
        assertEquals(1, capped.more)

        // Today is always listed, even when empty
        val none = limits.apply(days.drop(1), today, daysAhead = 1, maxEvents = 7)
        assertEquals(listOf(DayKind.TODAY), none.days.map { it.kind })
    }
}
