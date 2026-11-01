package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.data.LifeMappConfig
import com.kalai73.lifemapp.widgets.core.data.LifeMappJson
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException

/** The server answered with an error (5xx, unexpected 4xx); worth retrying later. */
class ServerException(val status: Int) : IOException("Server error $status")

fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(ContentNegotiation) { json(LifeMappJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
        requestTimeoutMillis = 25_000
    }
}

/** Key material for unlocking end-to-end encryption (see KeyEnvelope). */
data class KeyMaterial(val encryptionEnabled: Boolean, val salt: String?, val envelope: JsonObject?)

/**
 * The LIFE MAPP web API (Next.js routes, Bearer auth) and the few Supabase REST reads the app needs.
 * Every call retries once with a refreshed token on 401.
 */
class LifeMappApi(
    private val http: HttpClient,
    private val config: LifeMappConfig,
    private val auth: AuthRepository,
) {
    suspend fun snapshot(timezone: String, daysAhead: Int = 7): Snapshot {
        val response = authorized { token ->
            http.get("${config.webUrl}/api/widget/snapshot") {
                bearerAuth(token)
                parameter("tz", timezone)
                parameter("daysAhead", daysAhead)
                // Also the Task List Widget's Tomorrow / Tomorrow ++ 1 tasks, shown dimmed as on the web
                parameter("upcomingTasks", 1)
            }
        }
        return response.body()
    }

    @Serializable private data class CompleteBody(val completed: Boolean)

    /** Marks a LIFE MAPP task completed; the server creates the next instance of recurring tasks. */
    suspend fun completeTask(taskId: String) {
        authorized { token ->
            http.put("${config.webUrl}/api/tasks/$taskId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(CompleteBody(true))
            }
        }
    }

    @Serializable private data class ItemNameBody(
        val name: String,
        @SerialName("encrypted_name") val encryptedName: String? = null,
    )

    /** Renames an Integrity item (subsubdomain). Encrypted accounts store the sentinel name plus the ciphertext. */
    suspend fun renameItem(itemId: String, name: String, encryptedName: String?) {
        authorized { token ->
            http.put("${config.webUrl}/api/subsubdomains/$itemId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(ItemNameBody(name, encryptedName))
            }
        }
    }

    @Serializable private data class CollapsedBody(@SerialName("is_collapsed") val collapsed: Boolean)

    /** Collapses or expands an Integrity topic (subdomain), as the web card's chevron does. */
    suspend fun setTopicCollapsed(topicId: String, collapsed: Boolean) {
        authorized { token ->
            http.put("${config.webUrl}/api/subdomains/$topicId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(CollapsedBody(collapsed))
            }
        }
    }

    suspend fun deleteItem(itemId: String) {
        authorized { token -> http.delete("${config.webUrl}/api/subsubdomains/$itemId") { bearerAuth(token) } }
    }

    @Serializable private data class HandoffBody(val redirect: String)
    @Serializable private data class HandoffResponse(val url: String)

    /** One-time sign-in URL that opens [redirect] in the browser already signed in. */
    suspend fun sessionHandoff(redirect: String): String {
        val response = authorized { token ->
            http.post("${config.webUrl}/api/widget/session-handoff") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(HandoffBody(redirect))
            }
        }
        return response.body<HandoffResponse>().url
    }

    @Serializable private data class ParseVoiceBody(val transcript: String, val chargeCredits: Boolean)
    @Serializable private data class ParseVoiceResponse(val items: List<VoiceItem> = emptyList(), val limitReached: Boolean = false)

    /**
     * Splits a transcript recognized on the device into tasks and reminders (Gemini, on the server). Charged as
     * the voice input's AI credits — the device skipped the web's /api/transcribe, which charges them otherwise.
     * An empty list means nothing was understood (not charged).
     */
    suspend fun parseVoiceItems(transcript: String): VoiceParse {
        val response = authorized(accept = { it.isSuccess() || it == HttpStatusCode.TooManyRequests }) { token ->
            http.post("${config.webUrl}/api/parse-voice-items") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(ParseVoiceBody(transcript.trim().take(MAX_VOICE_TRANSCRIPT_CHARS), chargeCredits = true))
            }
        }
        val body = response.body<ParseVoiceResponse>()
        if (response.status == HttpStatusCode.TooManyRequests) {
            if (body.limitReached) return VoiceParse.NoCredits
            throw ServerException(response.status.value)
        }
        return VoiceParse.Items(body.items.filter { it.text.isNotBlank() })
    }

    @Serializable private data class VoiceItemsBody(val items: List<VoiceItemToFile>)
    @Serializable private data class VoiceItemsResponse(val results: List<VoiceItemResult> = emptyList())

    /** Files voice items in the user's task manager and Reminders (/api/widget/voice-items); one result per item. */
    suspend fun fileVoiceItems(items: List<VoiceItemToFile>): List<VoiceItemResult> {
        val response = authorized { token ->
            http.post("${config.webUrl}/api/widget/voice-items") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(VoiceItemsBody(items))
            }
        }
        return response.body<VoiceItemsResponse>().results
    }

    suspend fun keyMaterial(userId: String): KeyMaterial {
        val response = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_user_settings") {
                supabase(token)
                parameter("select", "encryption_enabled,passphrase_salt,encryption_metadata")
                parameter("user_id", "eq.$userId")
            }
        }
        val row = response.body<JsonArray>().firstOrNull() as? JsonObject
        return KeyMaterial(
            encryptionEnabled = (row?.get("encryption_enabled") as? JsonPrimitive)?.contentOrNull == "true",
            salt = (row?.get("passphrase_salt") as? JsonPrimitive)?.contentOrNull,
            envelope = row?.get("encryption_metadata")?.let { element ->
                // JSONB can arrive as a string
                when (element) {
                    is JsonObject -> element
                    is JsonPrimitive -> element.contentOrNull?.let { runCatching { LifeMappJson.parseToJsonElement(it).jsonObject }.getOrNull() }
                    else -> null
                }
            },
        )
    }

    /** A few encrypted task titles, to verify a legacy account's key that has no key_check. */
    suspend fun encryptedSamples(userId: String): List<String> {
        val response = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_tasks") {
                supabase(token)
                parameter("select", "encrypted_title")
                parameter("user_id", "eq.$userId")
                parameter("encrypted_title", "not.is.null")
                parameter("limit", 3)
            }
        }
        return response.body<JsonArray>().mapNotNull { ((it as? JsonObject)?.get("encrypted_title") as? JsonPrimitive)?.contentOrNull }
    }

    private fun HttpRequestBuilder.supabase(token: String) {
        header("apikey", config.supabaseAnonKey)
        bearerAuth(token)
    }

    /** [accept]: statuses handed back to the caller instead of thrown as [ServerException] */
    private suspend fun authorized(
        accept: (HttpStatusCode) -> Boolean = { it.isSuccess() },
        request: suspend (token: String) -> HttpResponse,
    ): HttpResponse {
        var response = request(auth.validAccessToken())
        if (response.status == HttpStatusCode.Unauthorized) {
            response = request(auth.validAccessToken(forceRefresh = true))
            if (response.status == HttpStatusCode.Unauthorized) throw NotSignedInException()
        }
        if (!accept(response.status)) throw ServerException(response.status.value)
        return response
    }

    companion object {
        /** As /api/parse-voice-items accepts */
        const val MAX_VOICE_TRANSCRIPT_CHARS = 5000
    }
}

/** A task or reminder in what was said, as /api/parse-voice-items splits it. */
@Serializable
data class VoiceItem(val text: String, val intent: VoiceIntent)

@Serializable
enum class VoiceIntent {
    @SerialName("task") TASK,
    @SerialName("reminder") REMINDER,
}

sealed interface VoiceParse {
    data class Items(val items: List<VoiceItem>) : VoiceParse
    /** The month's AI credits are used up; nothing was parsed */
    data object NoCredits : VoiceParse
}

/** [encryptedText]: the text encrypted with the data key (EncryptedField), for accounts with encryption on. */
@Serializable
data class VoiceItemToFile(val text: String, val intent: VoiceIntent, val encryptedText: String? = null)

@Serializable
data class VoiceItemResult(
    val ok: Boolean,
    /** Where it went: a reminder becomes a task when the account has no Reminders topic */
    val kind: VoiceIntent = VoiceIntent.TASK,
    val reason: VoiceFailure? = null,
)

@Serializable
enum class VoiceFailure {
    /** Encryption is on and this device isn't unlocked */
    @SerialName("locked") LOCKED,
    /** The plan's reminders limit is reached */
    @SerialName("limit") LIMIT,
    /** Todoist / Google Tasks isn't connected (or its access expired) */
    @SerialName("not-connected") NOT_CONNECTED,
    /** A task manager the widget can't file into yet (Microsoft To Do, TickTick) */
    @SerialName("unsupported") UNSUPPORTED,
    @SerialName("failed") FAILED,
}
