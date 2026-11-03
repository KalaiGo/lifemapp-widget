package com.kalai73.lifemapp.widgets.core.data

import com.kalai73.lifemapp.widgets.core.model.LifeMappJson
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
class RepositoriesTest {
    private val dir = Files.createTempDirectory("cache").toFile()
    private val cache = CacheStore(DataStoreFactory.create(CachedStateSerializer) { File(dir, "c.json") })
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val secrets = SecretStore(context, FakeCipher, File(dir, "s.bin"))

    private val serenity = Integrity(
        domainId = "d2", position = 2, name = "SERENITY", rating = 6.0,
        topics = listOf(Topic("s2", name = "Calm", collapsed = false, count = 1, items = listOf(TopicItem("i2", name = "Breathe")))),
    )
    private val tenacity = Integrity(domainId = "d5", position = 5, name = "TENACITY", rating = 2.0)
    private val todaySnapshot = Snapshot(today = "2026-10-31", integrity = Integrity(domainId = "d1", name = "INTEGRITY"), todayDomains = listOf(serenity, tenacity))

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
        val repo = SnapshotRepository(api { if (fail) throw java.io.IOException("offline") else json("""{"today":"2026-10-31"}""") }.snapshots, cache, clock = { 5 })
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
    fun `a sync keeps the app's own hidden domains`() = runTest {
        signIn()
        cache.update { it.copy(scoring = Scoring(hiddenDomainIds = listOf("d3"), lastCompleted = "old")) }
        val repo = SnapshotRepository(api { json("""{"today":"2026-10-31"}""") }.snapshots, cache, scoring = { Scoring(lastCompleted = "new") })
        assertEquals(SyncResult.Success, repo.refresh())
        assertEquals(listOf("d3"), cache.current().scoring!!.hiddenDomainIds)
        assertEquals("new", cache.current().scoring!!.lastCompleted)
    }

    @Test
    fun `a cancelled sync clears the spinner`() = runTest {
        signIn()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = SnapshotRepository(api {
            started.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }.snapshots, cache)
        val job = launch { repo.refresh() }
        started.await()
        assertTrue(cache.current().syncing)
        job.cancel()
        job.join()
        assertFalse(cache.current().syncing)
    }

    @Test
    fun `refresh without a session reports signed out`() = runTest {
        val repo = SnapshotRepository(api { json("{}") }.snapshots, cache)
        assertEquals(SyncResult.SignedOut, repo.refresh())
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
            }.tasks,
            cache,
            userId = { "u1" },
            dataKey = { dataKey },
        )

        assertTrue(repo.complete("todoist:42"))
        assertEquals("POST api.todoist.com/api/v1/tasks/42/close Bearer todoist-secret", calls.last())
        assertTrue(cache.current().snapshot!!.tasks.isEmpty())

        // Locked (no data key): nothing to send, the task comes back after the retries
        cache.update { CachedState(snapshot = Snapshot(tasks = listOf(Task("todoist:43", "B", source = "todoist")))) }
        val locked = TaskRepository(api { json("""[{"encrypted_api_key":"x"}]""") }.tasks, cache, userId = { "u1" })
        assertFalse(locked.complete("todoist:43"))
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
    fun `hiding a domain materializes a never-set list locally and never touches the web`() = runTest {
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
            }.scoring,
            cache, userId = { "u1" }, dataKey = { null },
        )
        // Never set: Serenity and Vitality hidden; showing Serenity leaves Vitality hidden
        repo.setHidden(mapOf("d2" to false))
        assertEquals(listOf("d3"), cache.current().scoring!!.hiddenDomainIds)
        // Integrity can't be hidden
        repo.setHidden(mapOf("d1" to true, "d2" to true))
        assertEquals(listOf("d3", "d2"), cache.current().scoring!!.hiddenDomainIds)
        // The app's own list: nothing is saved to the web's preferences
        assertTrue(patches.isEmpty())
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
            }.scoring,
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
        val scoring = api.scoring.read("u1")
        assertFalse(selects.single().contains("user_settings"))
        // The web's hidden domains are not read: the app keeps its own
        assertNull(scoring.hiddenDomainIds)
        assertEquals("2026-10-29T08:00:00Z", scoring.lastCompleted)
        assertEquals(5, scoring.eventsDaysAhead)
        assertEquals(4, scoring.eventsMaxEvents)
    }

    @Test
    fun `a failed integrity section keeps the cached card`() {
        val integrity = Integrity(domainId = "d1", name = "INTEGRITY", warnings = 3)
        val merged = SnapshotRepository.merge(Snapshot(integrity = integrity), Snapshot(failedSections = listOf("integrity")))
        assertEquals(integrity, merged.integrity)
    }

    @Test
    fun `the other Today domains are kept when their section fails and edited in the cache`() = runTest {
        val failed = SnapshotRepository.merge(todaySnapshot, Snapshot(failedSections = listOf("todayDomains")))
        assertEquals(listOf("d2", "d5"), failed.todayDomains.map { it.domainId })
        assertTrue(SnapshotRepository.merge(todaySnapshot, Snapshot(failedSections = listOf("mail"))).todayDomains.isEmpty())

        // Collapsing a topic and deleting an item of Serenity update the cached card
        cache.update { CachedState(snapshot = todaySnapshot) }
        signIn()
        val items = ItemRepository(api { json("{}") }.items, cache, dataKey = { null })
        items.setTopicCollapsed("s2", true)
        assertTrue(cache.current().snapshot!!.todayDomains[0].topics.single().collapsed)
        items.delete("i2")
        assertEquals(0, cache.current().snapshot!!.todayDomains[0].topics.single().items.size)
    }

    @Test
    fun `completing a task hides it at once and removes it once confirmed`() = runTest {
        signIn()
        cache.update { CachedState(snapshot = Snapshot(tasks = listOf(Task("t1", "A"), Task("t2", "B")), counts = Counts(tasks = 2))) }
        val calls = mutableListOf<String>()
        val repo = TaskRepository(api { req -> calls += "${req.method.value} ${req.url.encodedPath}"; json("{}") }.tasks, cache)

        repo.markCompletedLocally("t1")
        assertEquals(setOf("t1"), cache.current().pendingCompletions.keys)

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
        val repo = TaskRepository(api { json("{}", HttpStatusCode.InternalServerError) }.tasks, cache)
        repo.markCompletedLocally("t1")
        assertFalse(repo.complete("t1"))
        repo.revert("t1")
        assertTrue(cache.current().pendingCompletions.isEmpty())
        assertTrue(cache.current().completionFailed)
        assertEquals(listOf("t1"), cache.current().snapshot!!.tasks.map { it.id })
    }
}
