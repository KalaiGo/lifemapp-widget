package com.kalai73.lifemapp.widgets.core.data.auth

import com.kalai73.lifemapp.widgets.core.data.LifeMappConfig
import com.kalai73.lifemapp.widgets.core.data.secure.Session
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Thrown for credential problems (wrong password, revoked refresh token); not for network failures. */
class AuthException(message: String, val code: String? = null) : Exception(message)

/**
 * Minimal Supabase GoTrue client over Ktor: password and refresh-token grants, Google OAuth with PKCE, sign-out.
 * The web app uses the same Supabase project, so these sessions are accepted by its API (Bearer tokens).
 */
class SupabaseAuthApi(private val http: HttpClient, private val config: LifeMappConfig) {

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long = 3600,
        @SerialName("expires_at") val expiresAt: Long? = null,
        val user: User,
    )

    @Serializable
    private data class User(val id: String, val email: String? = null)

    @Serializable
    private data class ErrorResponse(
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
        @SerialName("error_code") val errorCode: String? = null,
        val msg: String? = null,
        val message: String? = null,
    )

    @Serializable private data class PasswordGrant(val email: String, val password: String)
    @Serializable private data class RefreshGrant(@SerialName("refresh_token") val refreshToken: String)
    @Serializable private data class PkceGrant(@SerialName("auth_code") val authCode: String, @SerialName("code_verifier") val codeVerifier: String)

    suspend fun signInWithPassword(email: String, password: String): Session =
        token("password", PasswordGrant(email.trim(), password))

    suspend fun refresh(refreshToken: String): Session = token("refresh_token", RefreshGrant(refreshToken))

    suspend fun exchangeCode(authCode: String, codeVerifier: String): Session = token("pkce", PkceGrant(authCode, codeVerifier))

    /** Revokes the refresh token on the server. Best effort: the local session is dropped either way. */
    suspend fun signOut(accessToken: String) {
        runCatching {
            http.post("${config.supabaseUrl}/auth/v1/logout") {
                header("apikey", config.supabaseAnonKey)
                bearerAuth(accessToken)
                parameter("scope", "local")
            }
        }
    }

    /** PKCE pair for one OAuth attempt. The verifier stays on the device until the callback. */
    data class Pkce(val verifier: String, val challenge: String)

    fun newPkce(): Pkce {
        val bytes = ByteArray(48).also(SecureRandom()::nextBytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Pkce(verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(digest))
    }

    /** Google sign-in page; Supabase redirects back to [LifeMappConfig.authRedirectUri] with `?code=`. */
    fun googleAuthorizeUrl(pkce: Pkce): String = URLBuilder("${config.supabaseUrl}/auth/v1/authorize").apply {
        parameters.append("provider", "google")
        parameters.append("redirect_to", config.authRedirectUri)
        parameters.append("code_challenge", pkce.challenge)
        parameters.append("code_challenge_method", "s256")
    }.buildString()

    private suspend inline fun <reified T : Any> token(grantType: String, body: T): Session {
        val response = http.post("${config.supabaseUrl}/auth/v1/token") {
            parameter("grant_type", grantType)
            header("apikey", config.supabaseAnonKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
        val token = response.body<TokenResponse>()
        val now = System.currentTimeMillis() / 1000
        return Session(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAt = token.expiresAt ?: (now + token.expiresIn),
            userId = token.user.id,
            email = token.user.email,
        )
    }

    private suspend fun HttpResponse.toAuthException(): Exception {
        val text = runCatching { bodyAsText() }.getOrDefault("")
        val parsed = runCatching { com.kalai73.lifemapp.widgets.core.data.LifeMappJson.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
        val message = parsed?.errorDescription ?: parsed?.msg ?: parsed?.message ?: parsed?.error ?: "HTTP ${status.value}"
        // 5xx is the server's problem, not the credentials'
        if (status.value >= 500) return java.io.IOException(message)
        return AuthException(message, parsed?.errorCode ?: parsed?.error)
    }
}
