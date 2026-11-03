package com.kalai73.lifemapp.widgets.core.data

import com.kalai73.lifemapp.widgets.core.model.LifeMappJson
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.datastore.core.DataStoreFactory
import com.kalai73.lifemapp.widgets.core.crypto.Aes
import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.SupabaseAuthApi
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.cache.CachedStateSerializer
import com.kalai73.lifemapp.widgets.core.data.content.ItemHtml
import com.kalai73.lifemapp.widgets.core.data.repository.ItemRepository
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import com.kalai73.lifemapp.widgets.core.data.secure.Session
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import io.ktor.client.request.HttpRequestData
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class ItemContentTest {
    private val dir = Files.createTempDirectory("items").toFile()
    private val cache = CacheStore(DataStoreFactory.create(CachedStateSerializer) { File(dir, "c.json") })
    private val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val secrets = SecretStore(context, FakeCipher, File(dir, "s.bin"))
    private val dataKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    /** PUT bodies sent to /api/subsubdomains, by item id */
    private val puts = mutableMapOf<String, JsonObject>()

    private fun repo(row: String, key: String? = dataKey): ItemRepository {
        val http = mockClient { req ->
            if (req.method.value == "PUT") {
                puts[req.url.encodedPath.substringAfterLast('/')] = body(req)
                json("""{"success":true}""")
            } else {
                assertEquals("/rest/v1/lm_life_subsubdomains", req.url.encodedPath)
                json(row)
            }
        }
        val api = LifeMappApi(http, TestConfig, AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { 0 }))
        return ItemRepository(api.items, cache, dataKey = { key })
    }

    private fun body(req: HttpRequestData): JsonObject =
        LifeMappJson.parseToJsonElement(String((req.body as OutgoingContent.ByteArrayContent).bytes())).jsonObject

    private suspend fun seed(item: TopicItem) {
        secrets.update { it.copy(session = Session("a", "r", 99_999, "u1")) }
        cache.update {
            CachedState(snapshot = Snapshot(integrity = Integrity("d1", topics = listOf(Topic("s1", "Notes", count = 1, items = listOf(item))))))
        }
    }

    private suspend fun cachedItem() = cache.current().snapshot!!.integrity!!.topics.single().items.single()

    @Test
    fun `the editor gets the whole stored text, not the snapshot's cut copy`() = runTest {
        val long = "<p>" + "a".repeat(5000) + "</p>"
        seed(TopicItem("i1", "Plan", content = long.take(4000)))
        val text = repo("""[{"content":"$long","encrypted_content":null,"encryption_metadata":null,"is_image":false}]""").loadText("i1")
        assertEquals(ItemText.Loaded(long), text)

        assertEquals(ItemText.Gone, repo("[]").loadText("i1"))
        assertEquals(ItemText.Image, repo("""[{"content":"[IMG:x]","is_image":true}]""").loadText("i1"))
    }

    @Test
    fun `the editor gets the creation date and the plan's character limit`() = runTest {
        seed(TopicItem("i1", "Plan"))
        fun repoFor(profile: String, config: String): ItemRepository {
            val http = mockClient { req ->
                when (req.url.encodedPath) {
                    "/rest/v1/lm_life_subsubdomains" -> json("""[{"content":"<p>x</p>","created_at":"2026-11-01T12:54:00+00:00"}]""")
                    "/rest/v1/lm_users_profile" -> json(profile)
                    "/rest/v1/lm_app_config" -> json(config)
                    else -> error("unexpected ${req.url}")
                }
            }
            val api = LifeMappApi(http, TestConfig, AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { 0 }))
            return ItemRepository(api.items, cache, dataKey = { null }, userId = { "u1" })
        }

        val pro = repoFor("""[{"subscription_type":"pro"}]""", "[]").loadText("i1") as ItemText.Loaded
        assertEquals("2026-11-01T12:54:00+00:00", pro.createdAt)
        assertEquals(7000, pro.maxChars)
        // The database row wins over the built-in table, and admins are "admin"
        val admin = repoFor("""[{"subscription_type":"basic","is_admin":true}]""", """[{"json_value":{"admin":{"max_subdomain_chars":12345}}}]""")
        assertEquals(12345, (admin.loadText("i1") as ItemText.Loaded).maxChars)
        assertEquals(5000, (repoFor("[]", "[]").loadText("i1") as ItemText.Loaded).maxChars)
    }

    @Test
    fun `moving an item rewrites the topic's order and the warning icon keeps the count in step`() = runTest {
        secrets.update { it.copy(session = Session("a", "r", 99_999, "u1")) }
        cache.update {
            CachedState(snapshot = Snapshot(integrity = Integrity("d1", warnings = 0, topics = listOf(
                Topic("s1", "Notes", count = 3, items = listOf(TopicItem("a"), TopicItem("b"))),
            ))))
        }
        var reorder: JsonObject? = null
        val http = mockClient { req ->
            when {
                req.url.encodedPath == "/rest/v1/lm_life_subsubdomains" -> json("""[{"id":"a"},{"id":"b"},{"id":"c"}]""")
                req.url.encodedPath.endsWith("/reorder") -> { reorder = body(req); json("{}") }
                else -> { puts[req.url.encodedPath.substringAfterLast('/')] = body(req); json("{}") }
            }
        }
        val api = LifeMappApi(http, TestConfig, AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { 0 }))
        val items = ItemRepository(api.items, cache, dataKey = { null })

        assertTrue(items.move("b", "s1", up = true))
        assertEquals(
            """{"items":[{"id":"b","position":0},{"id":"a","position":1},{"id":"c","position":2}]}""",
            reorder.toString(),
        )
        assertEquals(listOf("b", "a"), cache.current().snapshot!!.integrity!!.topics.single().items.map { it.id })
        // The last item (beyond the listed ones) can't go further down
        assertFalse(items.move("c", "s1", up = false))

        items.setWarning("a", true)
        assertEquals(JsonPrimitive("warning"), puts["a"]!!["status"])
        assertTrue(cache.current().snapshot!!.integrity!!.topics.single().items.first { it.id == "a" }.warning)
        assertEquals(1, cache.current().snapshot!!.integrity!!.warnings)
        items.setWarning("a", false)
        assertEquals(JsonNull, puts["a"]!!["status"])
        assertEquals(0, cache.current().snapshot!!.integrity!!.warnings)
    }

    @Test
    fun `encrypted text is decrypted, also legacy ciphertext with the IV and tag in the metadata`() = runTest {
        seed(TopicItem("i1"))
        val data = Aes.encrypt("<p>Secret</p>", dataKey)
        val selfContained = EncryptedField.encrypt("<p>Secret</p>", dataKey)
        val quoted = LifeMappJson.encodeToString(String.serializer(), selfContained)
        assertEquals(ItemText.Loaded("<p>Secret</p>"), repo("""[{"content":null,"encrypted_content":$quoted}]""").loadText("i1"))

        val legacy = """[{"encrypted_content":"${data.encrypted}","encryption_metadata":{"iv":"${data.iv}","tag":"${data.tag}"}}]"""
        assertEquals(ItemText.Loaded("<p>Secret</p>"), repo(legacy).loadText("i1"))

        assertEquals(ItemText.Locked, repo("""[{"encrypted_content":$quoted}]""", key = null).loadText("i1"))
        val otherKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        assertEquals(ItemText.Unreadable, repo("""[{"encrypted_content":$quoted}]""", key = otherKey).loadText("i1"))
    }

    @Test
    fun `saving plain text clears a stale encrypted copy and updates the cached item`() = runTest {
        seed(TopicItem("i1", "Plan", content = "<p>Old</p>"))
        assertTrue(repo("[]").save("i1", name = null, html = "<p>New</p>", encrypted = false))
        assertEquals(JsonObject(mapOf("content" to JsonPrimitive("<p>New</p>"), "encrypted_content" to JsonNull)), puts["i1"])
        assertEquals("<p>New</p>", cachedItem().content)
        assertEquals("Plan", cachedItem().name)

        // Emptied: an empty content column, and no text in the snapshot (no chevron)
        repo("[]").save("i1", name = null, html = "<p></p>", encrypted = false)
        assertEquals(JsonPrimitive(""), puts["i1"]!!["content"])
        assertNull(cachedItem().content)

        // Renaming alone leaves the text alone
        repo("[]").save("i1", name = " Plan B ", html = null, encrypted = false)
        assertEquals(JsonObject(mapOf("name" to JsonPrimitive("Plan B"))), puts["i1"])
    }

    @Test
    fun `with encryption on the text is encrypted and the plaintext column emptied`() = runTest {
        seed(TopicItem("i1", "Plan"))
        assertTrue(repo("[]").save("i1", name = null, html = "<p>Secret</p>", encrypted = true))
        val sent = puts["i1"]!!
        assertEquals(JsonNull, sent["content"])
        val ciphertext = (sent["encrypted_content"] as JsonPrimitive).content
        assertEquals("<p>Secret</p>", EncryptedField.decrypt(ciphertext, dataKey))
        assertNull(cachedItem().content)
        assertEquals(ciphertext, cachedItem().encryptedContent)

        // Locked: nothing sent
        puts.clear()
        assertFalse(repo("[]", key = null).save("i1", name = null, html = "<p>x</p>", encrypted = true))
        assertTrue(puts.isEmpty())
    }

    @Test
    fun `html round trips through the editor's spans`() {
        val spanned = ItemHtml.toSpanned("<p>Hello <strong>bold</strong> <a href=\"https://x.test\">link</a> café</p><p>Second</p>")
        assertEquals("Hello bold link café\nSecond", spanned.toString())
        val html = ItemHtml.toHtml(spanned)
        assertTrue(html, html.contains("<b>bold</b>"))
        assertTrue(html, html.contains("<a href=\"https://x.test\">link</a>"))
        assertTrue(html, html.contains("café"))
        assertFalse(html, html.contains("dir="))
        assertTrue(html, html.contains("Second"))

        // A span added by the format bar comes out as a tag
        val edited = SpannableStringBuilder("plain words")
        edited.setSpan(StyleSpan(Typeface.ITALIC), 6, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertTrue(ItemHtml.toHtml(edited).contains("plain <i>words</i>"))

        // What the toolbar's bullet list, text color and link write
        val styled = SpannableStringBuilder("one\ntwo\nred link")
        styled.setSpan(android.text.style.BulletSpan(24), 0, 4, Spanned.SPAN_PARAGRAPH)
        styled.setSpan(android.text.style.BulletSpan(24), 4, 8, Spanned.SPAN_PARAGRAPH)
        styled.setSpan(android.text.style.ForegroundColorSpan(0xFFEF4444.toInt()), 8, 11, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        styled.setSpan(android.text.style.URLSpan("https://x.test"), 12, 16, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val styledHtml = ItemHtml.toHtml(styled)
        assertTrue(styledHtml, styledHtml.contains("<li>one</li>") && styledHtml.contains("<li>two</li>") && styledHtml.contains("<ul"))
        assertTrue(styledHtml, styledHtml.contains("color:#ef4444", ignoreCase = true))
        assertTrue(styledHtml, styledHtml.contains("<a href=\"https://x.test\">link</a>"))
        // and reading it back keeps the bullets
        val reread = ItemHtml.toSpanned(styledHtml)
        assertEquals(2, reread.getSpans(0, reread.length, android.text.style.BulletSpan::class.java).size)

        assertEquals("", ItemHtml.toHtml(SpannableStringBuilder("  \n")))
        assertTrue(ItemHtml.isBlank("<p>&nbsp;</p>"))
        assertFalse(ItemHtml.isBlank("<p>x</p>"))
        assertTrue(ItemHtml.hasUnsupportedFormatting("<ol><li>a</li></ol>"))
        assertFalse(ItemHtml.hasUnsupportedFormatting("<ul><li>a</li></ul><p><strong>b</strong></p>"))
    }
}
