package com.kalai73.lifemapp.widgets.core.data

import androidx.test.core.app.ApplicationProvider
import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.api.VoiceFailure
import com.kalai73.lifemapp.widgets.core.data.api.VoiceIntent
import com.kalai73.lifemapp.widgets.core.data.api.VoiceItem
import com.kalai73.lifemapp.widgets.core.data.api.VoiceItemResult
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.SupabaseAuthApi
import com.kalai73.lifemapp.widgets.core.data.repository.VoiceRepository
import com.kalai73.lifemapp.widgets.core.data.repository.VoiceRepository.Understood
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import com.kalai73.lifemapp.widgets.core.data.secure.Session
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class VoiceRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val secrets = SecretStore(context, FakeCipher, File(Files.createTempDirectory("secrets").toFile(), "s.bin"))
    private val bodies = mutableMapOf<String, String>()
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    private suspend fun repository(
        dataKey: String? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): VoiceRepository {
        secrets.update { it.copy(session = Session("a", "r", expiresAt = 99_999, userId = "u1")) }
        val http = mockClient { req ->
            bodies[req.url.encodedPath] = (req.body as TextContent).text
            handler(req)
        }
        val auth = AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { 0 })
        return VoiceRepository(LifeMappApi(http, TestConfig, auth), dataKey = { dataKey })
    }

    @Test
    fun `the transcript is parsed and charged on the server`() = runTest {
        val repo = repository {
            json("""{"items":[{"text":"Call John","intent":"reminder"},{"text":"Fix bug","intent":"task"},{"text":" ","intent":"task"}],"creditsUsed":3}""")
        }
        val result = repo.understand(" remind me to call John and add a task to fix the bug ", asReminders = false)
        assertEquals(
            Understood.Items(listOf(VoiceItem("Call John", VoiceIntent.REMINDER), VoiceItem("Fix bug", VoiceIntent.TASK))),
            result,
        )
        val sent = LifeMappJson.parseToJsonElement(bodies.getValue("/api/parse-voice-items")).jsonObject
        assertEquals("remind me to call John and add a task to fix the bug", sent["transcript"]!!.jsonPrimitive.content)
        assertEquals("true", sent["chargeCredits"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the Reception files everything as reminders`() = runTest {
        val repo = repository { json("""{"items":[{"text":"Fix bug","intent":"task"}]}""") }
        assertEquals(Understood.Items(listOf(VoiceItem("Fix bug", VoiceIntent.REMINDER))), repo.understand("fix the bug", asReminders = true))
    }

    @Test
    fun `used-up credits add nothing`() = runTest {
        val repo = repository { json("""{"error":"used up","limitReached":true}""", HttpStatusCode.TooManyRequests) }
        assertEquals(Understood.NoCredits, repo.understand("call John", asReminders = false))
    }

    @Test
    fun `a failing parser makes the transcript one reminder, as on the web`() = runTest {
        val repo = repository { json("""{"error":"Gemini down"}""", HttpStatusCode.InternalServerError) }
        assertEquals(
            Understood.Items(listOf(VoiceItem("call the dentist", VoiceIntent.REMINDER))),
            repo.understand("Remind me to call the dentist", asReminders = false),
        )
        assertEquals(Understood.Nothing, repo.understand("   ", asReminders = false))
    }

    @Test
    fun `items are filed with their text encrypted when this device is unlocked`() = runTest {
        val repo = repository(dataKey = key) {
            json("""{"results":[{"ok":true,"kind":"task"},{"ok":false,"kind":"reminder","reason":"limit"}]}""")
        }
        val results = repo.file(listOf(VoiceItem(" Fix bug ", VoiceIntent.TASK), VoiceItem("Call John", VoiceIntent.REMINDER)))
        assertEquals(
            listOf(VoiceItemResult(true, VoiceIntent.TASK), VoiceItemResult(false, VoiceIntent.REMINDER, VoiceFailure.LIMIT)),
            results,
        )
        val sent = LifeMappJson.parseToJsonElement(bodies.getValue("/api/widget/voice-items")).jsonObject["items"]!!.jsonArray
        val first = sent[0].jsonObject
        assertEquals("Fix bug", first["text"]!!.jsonPrimitive.content)
        assertEquals("task", first["intent"]!!.jsonPrimitive.content)
        assertEquals("Fix bug", EncryptedField.decrypt(first["encryptedText"]!!.jsonPrimitive.content, key))
        assertEquals("reminder", sent[1].jsonObject["intent"]!!.jsonPrimitive.content)
    }

    @Test
    fun `without a key the text goes as it is`() = runTest {
        val repo = repository { json("""{"results":[{"ok":false,"kind":"task","reason":"locked"}]}""") }
        val results = repo.file(listOf(VoiceItem("Fix bug", VoiceIntent.TASK)))
        assertEquals(VoiceFailure.LOCKED, results.single().reason)
        val sent = LifeMappJson.parseToJsonElement(bodies.getValue("/api/widget/voice-items")).jsonObject["items"]!!.jsonArray
        assertFalse(sent[0].jsonObject.containsKey("encryptedText"))
    }

    @Test
    fun `nothing to file sends nothing`() = runTest {
        val repo = repository { error("no request expected") }
        assertEquals(emptyList<VoiceItemResult>(), repo.file(listOf(VoiceItem("  ", VoiceIntent.TASK))))
        assertNull(bodies["/api/widget/voice-items"])
    }

    @Test
    fun `command phrases are stripped from the fallback reminder`() {
        assertEquals("buy milk", VoiceRepository.stripCommandPhrases("Add a reminder to buy milk"))
        assertEquals("call Ana", VoiceRepository.stripCommandPhrases("remind me to call Ana"))
        assertEquals("new task", VoiceRepository.stripCommandPhrases("new task"))
    }
}
