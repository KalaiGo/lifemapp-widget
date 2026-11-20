package com.kalai73.lifemapp.widgets.core.data

import androidx.datastore.core.DataStoreFactory
import com.kalai73.lifemapp.widgets.core.data.api.FeedRequestException
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.SupabaseAuthApi
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.cache.CachedStateSerializer
import com.kalai73.lifemapp.widgets.core.data.repository.FeedRepository
import com.kalai73.lifemapp.widgets.core.data.repository.ItemRepository
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import com.kalai73.lifemapp.widgets.core.data.secure.Session
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.FeedFilter
import com.kalai73.lifemapp.widgets.core.model.FeedFolder
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.LifeMappJson
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.SmartView
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/** The side panel's data: reminders, renames and the info box on the items API, and the feed repository. */
@RunWith(RobolectricTestRunner::class)
class SidePanelDataTest {
    private val dir = Files.createTempDirectory("panel").toFile()
    private val cache = CacheStore(DataStoreFactory.create(CachedStateSerializer) { File(dir, "c.json") })
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val secrets = SecretStore(context, FakeCipher, File(dir, "s.bin"))
    private val requests = mutableListOf<Triple<String, String, JsonObject?>>()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { json("""{"success":true}""") }): LifeMappApi {
        val http = mockClient { req ->
            val body = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.let { LifeMappJson.parseToJsonElement(String(it)).jsonObject }
            requests += Triple(req.method.value, req.url.encodedPath + (req.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""), body)
            handler(req)
        }
        return LifeMappApi(http, TestConfig, AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { 0 }))
    }

    private suspend fun seed(item: TopicItem) {
        secrets.update { it.copy(session = Session("a", "r", 99_999, "u1")) }
        val card = Integrity("d13", position = 13, infoBox = "Old", topics = listOf(Topic("s1", "Reading", count = 1, items = listOf(item))))
        // The panel reads monthlyDomains: edits must reach it, not only the Today cards
        cache.update { CachedState(snapshot = Snapshot(monthlyDomains = listOf(card))) }
    }

    private suspend fun cached() = cache.current().snapshot!!.monthlyDomains.single()

    @Test
    fun `a new reminder date re-arms its task, and the full domain list follows`() = runTest {
        seed(TopicItem("i1", "Pay", reminderDate = "2026-11-01T09:00:00Z", reminderTaskCreated = true))
        val items = ItemRepository(api().items, cache, dataKey = { null })
        items.saveReminder("i1", "2026-12-01T09:00:00Z", "FREQ=MONTHLY;INTERVAL=1", 2)
        val (method, path, body) = requests.single()
        assertEquals("PUT" to "/api/subsubdomains/i1", method to path)
        assertEquals(JsonPrimitive("2026-12-01T09:00:00Z"), body!!["reminder_date"])
        assertEquals(JsonPrimitive(2), body["reminder_lead_days"])
        assertEquals(JsonNull, body["reminder_task_created_at"])
        val item = cached().topics.single().items.single()
        assertEquals("FREQ=MONTHLY;INTERVAL=1", item.reminderRule)
        assertFalse(item.reminderTaskCreated)
    }

    @Test
    fun `changing only the lead days keeps the created task`() = runTest {
        seed(TopicItem("i1", "Pay", reminderDate = "2026-11-01T09:00:00Z", reminderTaskCreated = true))
        ItemRepository(api().items, cache, dataKey = { null }).saveReminder("i1", "2026-11-01T09:00:00Z", null, 3)
        assertFalse("reminder_task_created_at" in requests.single().third!!)
        assertTrue(cached().topics.single().items.single().reminderTaskCreated)
    }

    @Test
    fun `topic renames and info boxes are saved plain, or encrypted with the sentinel`() = runTest {
        seed(TopicItem("i1", "Pay"))
        val key = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val items = ItemRepository(api().items, cache, dataKey = { key })
        assertTrue(items.renameTopic("s1", " Books ", encrypted = false))
        assertEquals("/api/subdomains/s1", requests.last().second)
        assertEquals(JsonObject(mapOf("name" to JsonPrimitive("Books"), "encrypted_name" to JsonNull)), requests.last().third)
        assertEquals("Books", cached().topics.single().name)

        assertTrue(items.saveInfoBox("d13", "Secret", encrypted = true))
        assertEquals("/api/domains/d13", requests.last().second)
        assertEquals(JsonPrimitive("[encrypted]"), requests.last().third!!["info_box"])
        assertNull(cached().infoBox)
        assertTrue(cached().encryptedInfoBox!!.contains("\"iv\""))

        // Locked: nothing is sent
        val sent = requests.size
        assertFalse(ItemRepository(api().items, cache, dataKey = { null }).renameTopic("s1", "X", encrypted = true))
        assertEquals(sent, requests.size)
    }

    private val feedsJson = """{"folders":[{"id":"f1","name":"News","position":0},{"id":"f2","name":"Tech","parent_id":"f1","position":0}],
        "feeds":[{"id":"a","folder_id":"f2","title":"A","url":"https://a","unread_count":2},{"id":"b","title":"B","url":"https://b","unread_count":1}]}"""
    private val itemsJson = """{"items":[{"id":"i1","feed_id":"a","title":"One","url":"https://a/1","published_at":"2026-11-20T08:00:00Z"}],"hasMore":true}"""

    private suspend fun signIn() = secrets.update { it.copy(session = Session("a", "r", 99_999, "u1")) }

    private suspend fun feedRepo(failPatch: Boolean = false) = signIn().let { FeedRepository(api { req ->
        when {
            req.method.value == "PATCH" && failPatch -> json("""{"error":"nope"}""", HttpStatusCode.InternalServerError)
            req.url.encodedPath == "/api/feeds/items" -> json(itemsJson)
            req.url.encodedPath == "/api/feeds" && req.method.value == "POST" -> json("""{"error":"Feed limit reached"}""", HttpStatusCode.BadRequest)
            req.url.encodedPath == "/api/feeds" -> json(feedsJson)
            else -> json("""{"success":true}""")
        }
    }.feeds) }

    @Test
    fun `the filter becomes the items query, and pages append`() = runTest {
        val feeds = feedRepo()
        feeds.reload()
        assertEquals(2, feeds.state.value.feeds.size)
        assertTrue(requests.any { it.second.startsWith("/api/feeds?since=") })
        assertTrue(requests.last().second.contains("unreadOnly=true"))

        feeds.setFilter(FeedFilter(SmartView.STARRED, folderId = "f1", search = " rust "))
        val query = requests.last().second
        assertTrue(query, query.contains("folderId=f1") && query.contains("starredOnly=true") && query.contains("q=rust") && query.contains("offset=0"))

        feeds.loadItems(append = true)
        assertTrue(requests.last().second.contains("offset=1"))
        assertEquals(1, feeds.state.value.items.size) // the same item again is not listed twice
        assertTrue(feeds.state.value.hasMore)
    }

    @Test
    fun `reading an item updates its feed's unread count, and a refused change is put back`() = runTest {
        val feeds = feedRepo()
        feeds.reload()
        feeds.setRead("i1", true)
        assertTrue(feeds.state.value.items.single().isRead)
        assertEquals(1, feeds.state.value.feeds.first { it.id == "a" }.unreadCount)
        assertEquals(JsonObject(mapOf("is_read" to JsonPrimitive(true))), requests.last().third)

        val failing = feedRepo(failPatch = true)
        failing.reload()
        runCatching { failing.setStarred("i1", true) }
        assertFalse(failing.state.value.items.single().isStarred)
    }

    @Test
    fun `a refused subscription says why`() = runTest {
        try {
            feedRepo().subscribe("https://x.test/rss", null, null)
            fail("expected a refusal")
        } catch (e: FeedRequestException) {
            assertEquals("Feed limit reached", e.message)
            assertEquals(400, e.status)
        }
    }

    @Test
    fun `a folder's subtree takes in nested folders`() {
        val folders = listOf(FeedFolder("f1"), FeedFolder("f2", parentId = "f1"), FeedFolder("f3", parentId = "f2"), FeedFolder("x"))
        assertEquals(setOf("f1", "f2", "f3"), FeedRepository.folderSubtree(folders, "f1"))
    }
}
