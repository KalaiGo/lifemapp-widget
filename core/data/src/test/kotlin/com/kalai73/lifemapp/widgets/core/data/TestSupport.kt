package com.kalai73.lifemapp.widgets.core.data

import com.kalai73.lifemapp.widgets.core.data.secure.SecretCipher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

/** XOR "cipher": the Keystore isn't available under Robolectric. */
object FakeCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray) = plaintext.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    override fun decrypt(blob: ByteArray) = encrypt(blob)
}

val TestConfig = LifeMappConfig(
    supabaseUrl = "https://supabase.test",
    supabaseAnonKey = "anon",
    webUrl = "https://web.test",
)

fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
    HttpClient(MockEngine(handler)) {
        expectSuccess = false
        install(ContentNegotiation) { json(LifeMappJson) }
    }

fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

fun tokenJson(access: String, refresh: String = "r-$access", expiresIn: Long = 3600) =
    """{"access_token":"$access","refresh_token":"$refresh","expires_in":$expiresIn,"user":{"id":"u1","email":"ana@example.com"}}"""
