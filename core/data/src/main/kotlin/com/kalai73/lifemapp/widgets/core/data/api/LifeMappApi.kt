package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.LifeMappConfig
import com.kalai73.lifemapp.widgets.core.model.LifeMappJson
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
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

/** What every area of the API shares: the HTTP client, the build configuration and the session's tokens. */
internal class ApiClient(val http: HttpClient, val config: LifeMappConfig, val auth: AuthRepository)

/**
 * Base of each area of the API: Bearer auth with one retry on a refreshed token after a 401, the Supabase REST
 * headers, and the JSON column readers.
 */
abstract class ApiArea internal constructor(private val client: ApiClient) {
    protected val http: HttpClient get() = client.http
    protected val config: LifeMappConfig get() = client.config
    private val auth: AuthRepository get() = client.auth

    protected fun text(element: kotlinx.serialization.json.JsonElement?): String? =
        (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** A JSONB column, which can arrive as a string */
    protected fun blob(element: kotlinx.serialization.json.JsonElement?): JsonObject? = when (element) {
        is JsonObject -> element
        is JsonPrimitive -> element.contentOrNull?.let { runCatching { LifeMappJson.parseToJsonElement(it).jsonObject }.getOrNull() }
        else -> null
    }

    protected fun HttpRequestBuilder.supabase(token: String) {
        header("apikey", config.supabaseAnonKey)
        bearerAuth(token)
    }

    /** [accept]: statuses handed back to the caller instead of thrown as [ServerException] */
    protected suspend fun authorized(
        accept: (HttpStatusCode) -> Boolean = { it.isSuccess() },
        request: suspend (token: String) -> HttpResponse,
    ): HttpResponse {
        var response = request(auth.validAccessToken())
        if (response.status == HttpStatusCode.Unauthorized) {
            response = request(auth.validAccessToken(forceRefresh = true))
            if (response.status == HttpStatusCode.Unauthorized) throw NotSignedInException()
        }
        if (response.status == HttpStatusCode.Forbidden && auth.owesSecondStep()) throw NotSignedInException()
        if (!accept(response.status)) throw ServerException(response.status.value)
        return response
    }
}

/**
 * The LIFE MAPP web API (Next.js routes, Bearer auth) and the few Supabase REST reads the app needs, one class per
 * area so each repository depends only on the routes it uses.
 */
class LifeMappApi(http: HttpClient, config: LifeMappConfig, auth: AuthRepository) {
    private val client = ApiClient(http, config, auth)

    val snapshots = SnapshotApi(client)
    val tasks = TasksApi(client)
    val items = ItemsApi(client)
    val scoring = ScoringApi(client)
    val account = AccountApi(client)
    val voice = VoiceApi(client)
    val domains = DomainsApi(client)
}
