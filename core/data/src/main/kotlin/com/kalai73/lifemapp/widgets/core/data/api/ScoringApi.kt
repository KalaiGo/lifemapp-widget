package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.ScoringDomain
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The scoring section: life domains, their ratings and the last scoring. */
class ScoringApi internal constructor(client: ApiClient) : ApiArea(client) {
    /**
     * The scoring section's state, read as the web's user-settings-service does: the last scoring from its
     * column, and every domain's id, position, name and custom color. The web's hidden domains
     * (preferences.hidden_domain_ids) are not read: the app keeps its own list (hiddenDomainIds stays null here).
     * The legacy user_settings column is gone from the table: selecting it fails the whole read (400).
     */
    suspend fun read(userId: String): Scoring {
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
}
