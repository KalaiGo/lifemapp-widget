package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.LifeMappConfig
import com.kalai73.lifemapp.widgets.core.data.LifeMappJson
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.ScoringDomain
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
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
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

    /**
     * A LIFE MAPP task's whole title and description (GET /api/tasks/{id}): the snapshot cuts long descriptions,
     * so it's no base to edit from. Null when the task no longer exists.
     */
    suspend fun taskContent(taskId: String): StoredTaskContent? {
        val response = authorized(accept = { it.isSuccess() || it == HttpStatusCode.NotFound }) { token ->
            http.get("${config.webUrl}/api/tasks/$taskId") { bearerAuth(token) }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        val task = response.body<JsonObject>()["task"] as? JsonObject ?: return null
        fun text(name: String) = (task[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        return StoredTaskContent(
            title = text("title"),
            encryptedTitle = text("encrypted_title"),
            description = text("description"),
            encryptedDescription = text("encrypted_description"),
        )
    }

    /**
     * Updates fields of a LIFE MAPP task (PUT /api/tasks/{id}, the route the web's task modal saves through); a
     * JsonNull clears one.
     */
    suspend fun updateTask(taskId: String, fields: JsonObject) {
        authorized { token ->
            http.put("${config.webUrl}/api/tasks/$taskId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(fields)
            }
        }
    }

    /**
     * Updates fields of an item (subsubdomain): the route takes any subset of the columns, and a JsonNull clears
     * one (LifeMappJson leaves nulls out of serialized classes, hence the plain object).
     */
    suspend fun updateItem(itemId: String, fields: JsonObject) {
        authorized { token ->
            http.put("${config.webUrl}/api/subsubdomains/$itemId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(fields)
            }
        }
    }

    /** Ids of a topic's (subdomain's) items in their order, as the web card lists them (by position). */
    suspend fun topicItemIds(topicId: String): List<String> {
        val response = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_life_subsubdomains") {
                supabase(token)
                parameter("select", "id,position")
                parameter("subdomain_id", "eq.$topicId")
                parameter("order", "position.asc.nullsfirst,created_at.asc")
            }
        }
        return response.body<JsonArray>().mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }
    }

    @Serializable private data class ItemPosition(val id: String, val position: Int)
    @Serializable private data class ReorderBody(val items: List<ItemPosition>)

    /** Gives a topic's items the positions 0, 1, 2… in the order of [itemIds] (PUT /api/subsubdomains/reorder). */
    suspend fun reorderItems(itemIds: List<String>) {
        authorized { token ->
            http.put("${config.webUrl}/api/subsubdomains/reorder") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(ReorderBody(itemIds.mapIndexed { index, id -> ItemPosition(id, index) }))
            }
        }
    }

    /**
     * An item's whole stored text, read from the table: the snapshot cuts long text, so it's no base to edit from.
     * Null when the item no longer exists.
     */
    suspend fun itemContent(itemId: String): StoredItemContent? {
        val response = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_life_subsubdomains") {
                supabase(token)
                parameter("select", "content,encrypted_content,encryption_metadata,is_image,created_at")
                parameter("id", "eq.$itemId")
            }
        }
        val row = response.body<JsonArray>().firstOrNull() as? JsonObject ?: return null
        fun text(name: String) = (row[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        // JSONB can arrive as a string
        val metadata = when (val e = row["encryption_metadata"]) {
            is JsonObject -> e
            is JsonPrimitive -> e.contentOrNull?.let { runCatching { LifeMappJson.parseToJsonElement(it).jsonObject }.getOrNull() }
            else -> null
        }
        return StoredItemContent(
            content = text("content"),
            encryptedContent = selfContained(text("encrypted_content"), metadata),
            isImage = (row["is_image"] as? JsonPrimitive)?.contentOrNull == "true",
            createdAt = text("created_at"),
        )
    }

    /**
     * An image item's picture. The web stores public storage links (getPublicUrl), so a plain GET; a Supabase link
     * the bucket refuses anonymously is tried once more with the session, in case the bucket is private.
     */
    suspend fun downloadImage(url: String): ByteArray {
        var response = http.get(url)
        if (!response.status.isSuccess() && url.startsWith(config.supabaseUrl)) {
            response = authorized { token -> http.get(url) { supabase(token) } }
        }
        if (!response.status.isSuccess()) throw ServerException(response.status.value)
        return response.readRawBytes()
    }

    /**
     * The plan's cap on an item's text, as the web's editor enforces it (limits.max_subdomain_chars): the tier from
     * the profile (admins are "admin"), its limit from the lm_app_config subscription_tier_limits row, else the web's
     * built-in table (lib/stripe/subscription-features.ts).
     */
    suspend fun itemCharLimit(userId: String): Int {
        val profile = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_users_profile") {
                supabase(token)
                parameter("select", "subscription_type,subscription_tier,is_admin")
                parameter("id", "eq.$userId")
            }
        }.body<JsonArray>().firstOrNull() as? JsonObject
        fun field(name: String) = (profile?.get(name) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val tier = if (field("is_admin") == "true") "admin" else field("subscription_type") ?: field("subscription_tier") ?: "basic"
        val configured = runCatching {
            val row = authorized { token ->
                http.get("${config.supabaseUrl}/rest/v1/lm_app_config") {
                    supabase(token)
                    parameter("select", "json_value")
                    parameter("constraint_key", "eq.subscription_tier_limits")
                    parameter("is_active", "eq.true")
                }
            }.body<JsonArray>().firstOrNull() as? JsonObject
            val limits = when (val v = row?.get("json_value")) {
                is JsonObject -> v
                is JsonPrimitive -> v.contentOrNull?.let { LifeMappJson.parseToJsonElement(it).jsonObject }
                else -> null
            }
            ((limits?.get(tier) as? JsonObject)?.get("max_subdomain_chars") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        }.getOrNull()
        return configured ?: DEFAULT_ITEM_CHAR_LIMITS[tier] ?: DEFAULT_ITEM_CHAR_LIMITS.getValue("basic")
    }

    /**
     * Encrypted text as self-contained `{"encrypted","iv","tag"}` JSON, as the snapshot sends it: legacy rows keep
     * the bare ciphertext with the IV and tag in encryption_metadata (the web's build-widget-snapshot selfContained).
     */
    private fun selfContained(value: String?, metadata: JsonObject?): String? {
        if (value == null) return null
        if (EncryptedField.parse(value) != null) return value
        val iv = (metadata?.get("iv") as? JsonPrimitive)?.contentOrNull ?: return null
        val tag = (metadata["tag"] as? JsonPrimitive)?.contentOrNull ?: return null
        return JsonObject(mapOf("encrypted" to JsonPrimitive(value), "iv" to JsonPrimitive(iv), "tag" to JsonPrimitive(tag))).toString()
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

    /** Collapses or expands an Integrity item (subsubdomain), as the web card's item chevron does. */
    suspend fun setItemCollapsed(itemId: String, collapsed: Boolean) {
        authorized { token ->
            http.put("${config.webUrl}/api/subsubdomains/$itemId") {
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

    /**
     * The user's Todoist API key as the web settings store it: the plain column, or end-to-end encrypted
     * (EncryptedField JSON) when encryption is on; legacy rows keep the encrypted one in the encrypted_settings JSONB.
     * The legacy user_settings column is gone from the table: selecting it fails the whole read (400).
     */
    suspend fun todoistApiKey(userId: String): StoredApiKey {
        val response = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_user_settings") {
                supabase(token)
                parameter("select", "task_api_key,encrypted_api_key,encrypted_settings")
                parameter("user_id", "eq.$userId")
            }
        }
        val row = response.body<JsonArray>().firstOrNull() as? JsonObject ?: return StoredApiKey(null, null)
        fun text(element: kotlinx.serialization.json.JsonElement?): String? = when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> element.toString()
            else -> null
        }?.takeIf { it.isNotBlank() }
        // JSONB can arrive as a string
        fun blob(name: String): JsonObject? = when (val e = row[name]) {
            is JsonObject -> e
            is JsonPrimitive -> e.contentOrNull?.let { runCatching { LifeMappJson.parseToJsonElement(it).jsonObject }.getOrNull() }
            else -> null
        }
        return StoredApiKey(
            plain = text(row["task_api_key"]),
            encrypted = text(row["encrypted_api_key"]) ?: text(blob("encrypted_settings")?.get("task_manager_api_key")),
        )
    }

    /**
     * Closes a Todoist task with the user's own API key, straight at Todoist as the web's /api/todoist proxy does
     * (the browser needs the proxy for CORS only). Todoist moves a recurring task to its next date.
     */
    suspend fun closeTodoistTask(apiKey: String, taskId: String) {
        val response = http.post("$TODOIST_API_BASE/tasks/$taskId/close") { bearerAuth(apiKey) }
        if (!response.status.isSuccess()) throw ServerException(response.status.value)
    }

    /**
     * The scoring section's state, read as the web's user-settings-service does: the last scoring from its
     * column, and every domain's id, position, name and custom color. The web's hidden domains
     * (preferences.hidden_domain_ids) are not read: the app keeps its own list (hiddenDomainIds stays null here).
     * The legacy user_settings column is gone from the table: selecting it fails the whole read (400).
     */
    suspend fun scoring(userId: String): Scoring {
        val row = settingsRow(userId, "preferences,monthly_scoring_last_completed")
        val preferences = blob(row?.get("preferences"))
        val lastCompleted = text(row?.get("monthly_scoring_last_completed"))
        // The events card's limits live in the same preferences blob (calendar-settings-dialog.tsx)
        fun count(name: String): Int? = text(preferences?.get(name))?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
        val domains = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_life_domains") {
                supabase(token)
                parameter("select", "id,position,name,color")
                parameter("user_id", "eq.$userId")
            }
        }.body<JsonArray>().mapNotNull { element ->
            val d = element as? JsonObject ?: return@mapNotNull null
            ScoringDomain(
                id = text(d["id"]) ?: return@mapNotNull null,
                position = text(d["position"])?.toDoubleOrNull()?.toInt() ?: 0,
                name = text(d["name"]),
                color = text(d["color"]),
            )
        }
        return Scoring(
            lastCompleted = lastCompleted,
            domains = domains,
            eventsDaysAhead = count("days_ahead"),
            eventsMaxEvents = count("max_events"),
        )
    }

    /** Records the last scoring (monthly_scoring_last_completed) as an ISO instant, as the web does. */
    suspend fun setScoringCompleted(userId: String, isoInstant: String) {
        updateSettings(userId, JsonObject(mapOf("monthly_scoring_last_completed" to JsonPrimitive(isoInstant))))
    }

    /** The life domains (positions below 20) with their stored ratings, for the bulk rating's sibling list. */
    suspend fun lifeDomainRatings(userId: String): List<StoredRating> =
        authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_life_domains") {
                supabase(token)
                parameter("select", "id,position,rating,encrypted_rating")
                parameter("user_id", "eq.$userId")
                parameter("position", "lt.20")
            }
        }.body<JsonArray>().mapNotNull { element ->
            val d = element as? JsonObject ?: return@mapNotNull null
            StoredRating(
                id = text(d["id"]) ?: return@mapNotNull null,
                position = text(d["position"])?.toDoubleOrNull()?.toInt() ?: 0,
                rating = text(d["rating"])?.toDoubleOrNull() ?: 0.0,
                encryptedRating = text(d["encrypted_rating"]),
            )
        }

    @Serializable
    data class RatingEntry(
        val domainId: String,
        val rating: Int,
        @SerialName("encrypted_rating") val encryptedRating: String? = null,
    )

    @Serializable private data class RatingsBody(val entries: List<RatingEntry>)

    /** Updates the ratings and records score history in one request (PUT /api/domains/scores/bulk). */
    suspend fun saveRatings(entries: List<RatingEntry>) {
        authorized { token ->
            http.put("${config.webUrl}/api/domains/scores/bulk") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(RatingsBody(entries))
            }
        }
    }

    private suspend fun settingsRow(userId: String, columns: String): JsonObject? =
        authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_user_settings") {
                supabase(token)
                parameter("select", columns)
                parameter("user_id", "eq.$userId")
            }
        }.body<JsonArray>().firstOrNull() as? JsonObject

    private suspend fun updateSettings(userId: String, fields: JsonObject) {
        authorized { token ->
            http.patch("${config.supabaseUrl}/rest/v1/lm_user_settings") {
                supabase(token)
                parameter("user_id", "eq.$userId")
                header("Prefer", "return=minimal")
                contentType(ContentType.Application.Json)
                setBody(fields)
            }
        }
    }

    private fun text(element: kotlinx.serialization.json.JsonElement?): String? =
        (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** A JSONB column, which can arrive as a string */
    private fun blob(element: kotlinx.serialization.json.JsonElement?): JsonObject? = when (element) {
        is JsonObject -> element
        is JsonPrimitive -> element.contentOrNull?.let { runCatching { LifeMappJson.parseToJsonElement(it).jsonObject }.getOrNull() }
        else -> null
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
        const val TODOIST_API_BASE = "https://api.todoist.com/api/v1"
        /** max_subdomain_chars per tier when the database has no limits row (subscription-features.ts) */
        val DEFAULT_ITEM_CHAR_LIMITS = mapOf("basic" to 5000, "pro" to 7000, "master" to 10000, "business" to 10000, "admin" to 10000)
    }
}

/** An item's stored text: [content] (HTML) or, end-to-end encrypted, [encryptedContent] (EncryptedField JSON). */
data class StoredItemContent(
    val content: String?,
    val encryptedContent: String?,
    val isImage: Boolean,
    /** ISO timestamp */
    val createdAt: String? = null,
)

/**
 * A task's stored title and description (HTML): plain, or end-to-end encrypted (EncryptedField JSON), the plain
 * column then holding the web's placeholder.
 */
data class StoredTaskContent(
    val title: String?,
    val encryptedTitle: String?,
    val description: String?,
    val encryptedDescription: String?,
)

/** A life domain's stored rating: [rating], or (end-to-end encrypted) [encryptedRating] with the column at 0. */
data class StoredRating(val id: String, val position: Int, val rating: Double, val encryptedRating: String?)

/** An API key from the settings row: [plain], or [encrypted] with the data key. */
data class StoredApiKey(val plain: String?, val encrypted: String?)

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
