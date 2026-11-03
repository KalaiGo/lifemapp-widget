package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.model.LifeMappJson
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Items (subsubdomains) and topics (subdomains) of the Today domain cards. */
class ItemsApi internal constructor(client: ApiClient) : ApiArea(client) {
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
        val metadata = blob(row["encryption_metadata"])
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
            val limits = blob(row?.get("json_value"))
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

    companion object {
        /** max_subdomain_chars per tier when the database has no limits row (subscription-features.ts) */
        val DEFAULT_ITEM_CHAR_LIMITS = mapOf("basic" to 5000, "pro" to 7000, "master" to 10000, "business" to 10000, "admin" to 10000)
    }
}
