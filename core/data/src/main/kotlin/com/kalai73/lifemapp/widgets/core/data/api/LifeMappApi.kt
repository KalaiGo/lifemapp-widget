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

    @Serializable private data class WhitelistBody(val email: String)
    @Serializable private data class WhitelistResponse(val allowed: Boolean = true)

    /** Beta access check, like the web sign-in. Fails open on network or server errors, like the route itself. */
    suspend fun isWhitelisted(email: String): Boolean = runCatching {
        val response = http.post("${config.webUrl}/api/auth/check-whitelist") {
            contentType(ContentType.Application.Json)
            setBody(WhitelistBody(email))
        }
        if (!response.status.isSuccess()) return@runCatching true
        response.body<WhitelistResponse>().allowed
    }.getOrDefault(true)

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

    private suspend fun authorized(request: suspend (token: String) -> HttpResponse): HttpResponse {
        var response = request(auth.validAccessToken())
        if (response.status == HttpStatusCode.Unauthorized) {
            response = request(auth.validAccessToken(forceRefresh = true))
            if (response.status == HttpStatusCode.Unauthorized) throw NotSignedInException()
        }
        if (!response.status.isSuccess()) throw ServerException(response.status.value)
        return response
    }
}
