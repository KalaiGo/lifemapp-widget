package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Reads user life domains and their nested subdomains and items directly from Supabase PostgREST. */
class DomainsApi internal constructor(client: ApiClient) : ApiArea(client) {

    /**
     * Fetches all life domains for [userId] with nested subdomains and items in position order.
     * Encrypted values arrive normalized to self-contained JSON ({"encrypted","iv","tag"}).
     */
    suspend fun fetchDomains(userId: String): List<Integrity> {
        val select = "id,position,name,encrypted_name,info_box,encrypted_info_box,rating,encrypted_rating,color,encryption_metadata," +
            "subdomains:lm_life_subdomains(id,domain_id,name,encrypted_name,encryption_metadata,position,is_collapsed," +
            "subsubdomains:lm_life_subsubdomains(id,subdomain_id,name,encrypted_name,encryption_metadata,position,status,is_image,content,encrypted_content,is_collapsed))"

        val response = authorized { token ->
            http.get("${config.supabaseUrl}/rest/v1/lm_life_domains") {
                supabase(token)
                parameter("select", select)
                parameter("user_id", "eq.$userId")
                parameter("order", "position.asc")
            }
        }

        val rows = response.body<JsonArray>()
        return rows.mapNotNull { element ->
            val d = element as? JsonObject ?: return@mapNotNull null
            parseDomain(d)
        }
    }

    private fun parseDomain(d: JsonObject): Integrity? {
        val domainId = text(d["id"]) ?: return null
        val position = (d["position"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 1
        val domainMeta = blob(d["encryption_metadata"])

        val rawSubdomains = d["subdomains"] as? JsonArray ?: JsonArray(emptyList())
        val topics = rawSubdomains.mapNotNull { subElem ->
            val s = subElem as? JsonObject ?: return@mapNotNull null
            val subId = text(s["id"]) ?: return@mapNotNull null
            val subPos = (s["position"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
            val subMeta = blob(s["encryption_metadata"])
            val isCollapsed = (s["is_collapsed"] as? JsonPrimitive)?.contentOrNull == "true"

            val rawItems = s["subsubdomains"] as? JsonArray ?: JsonArray(emptyList())
            val items = rawItems.mapNotNull { itemElem ->
                val i = itemElem as? JsonObject ?: return@mapNotNull null
                val itemId = text(i["id"]) ?: return@mapNotNull null
                val itemPos = (i["position"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
                val itemMeta = blob(i["encryption_metadata"])
                val status = text(i["status"])
                val isImage = (i["is_image"] as? JsonPrimitive)?.contentOrNull == "true"
                val itemCollapsed = (i["is_collapsed"] as? JsonPrimitive)?.contentOrNull == "true"

                itemPos to TopicItem(
                    id = itemId,
                    name = text(i["name"]),
                    encryptedName = selfContained(text(i["encrypted_name"]), itemMeta),
                    warning = status == "warning",
                    isImage = isImage,
                    content = text(i["content"]),
                    encryptedContent = selfContained(text(i["encrypted_content"]), itemMeta),
                    collapsed = itemCollapsed,
                )
            }.sortedBy { it.first }.map { it.second }

            subPos to Topic(
                id = subId,
                name = text(s["name"]),
                encryptedName = selfContained(text(s["encrypted_name"]), subMeta),
                reminders = position == 1 && (text(s["name"])?.contains("reminder", ignoreCase = true) == true || subPos == 1),
                collapsed = isCollapsed,
                count = items.size,
                items = items,
            )
        }.sortedBy { it.first }.map { it.second }

        val warnings = topics.sumOf { t -> t.items.count { it.warning } }

        return Integrity(
            domainId = domainId,
            position = position,
            name = text(d["name"]),
            encryptedName = selfContained(text(d["encrypted_name"]), domainMeta),
            infoBox = text(d["info_box"]),
            encryptedInfoBox = selfContained(text(d["encrypted_info_box"]), domainMeta),
            rating = (d["rating"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull(),
            encryptedRating = text(d["encrypted_rating"]),
            color = text(d["color"]),
            topics = topics,
            warnings = warnings,
        )
    }

    private fun selfContained(value: String?, metadata: JsonObject?): String? {
        if (value == null) return null
        if (EncryptedField.parse(value) != null) return value
        val iv = (metadata?.get("iv") as? JsonPrimitive)?.contentOrNull ?: return null
        val tag = (metadata["tag"] as? JsonPrimitive)?.contentOrNull ?: return null
        return JsonObject(mapOf("encrypted" to JsonPrimitive(value), "iv" to JsonPrimitive(iv), "tag" to JsonPrimitive(tag))).toString()
    }
}

