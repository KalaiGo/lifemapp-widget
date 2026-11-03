package com.kalai73.lifemapp.widgets.core.data.api

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The account: encryption key material and the one-time web sign-in handoff. */
class AccountApi internal constructor(client: ApiClient) : ApiArea(client) {
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
            envelope = blob(row?.get("encryption_metadata")),
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
}
