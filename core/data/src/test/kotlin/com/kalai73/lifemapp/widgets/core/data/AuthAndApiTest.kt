package com.kalai73.lifemapp.widgets.core.data

import androidx.test.core.app.ApplicationProvider
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.data.auth.SupabaseAuthApi
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import com.kalai73.lifemapp.widgets.core.data.secure.Session
import com.kalai73.lifemapp.widgets.core.model.AuthState
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class AuthAndApiTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val secrets = SecretStore(context, FakeCipher, File(Files.createTempDirectory("secrets").toFile(), "s.bin"))
    private val requests = mutableListOf<String>()

    @Test
    fun `password sign-in stores the session`() = runTest {
        val http = mockClient { req ->
            requests += "${req.method.value} ${req.url.encodedPath}?${req.url.encodedQuery}"
            json(tokenJson("a1"))
        }
        val auth = AuthRepository(SupabaseAuthApi(http, TestConfig), secrets)
        auth.signIn("ana@example.com", "pw")
        assertEquals(AuthState.SIGNED_IN, auth.state.value)
        assertEquals("a1", secrets.read().session?.accessToken)
        assertEquals("POST /auth/v1/token?grant_type=password", requests.single())
    }

    @Test
    fun `an expiring token is refreshed before use`() = runTest {
        var now = 1_000L
        secrets.update { it.copy(session = Session("old", "r-old", expiresAt = 1_030, userId = "u1")) }
        val http = mockClient { req ->
            requests += req.url.encodedQuery
            json(tokenJson("new"))
        }
        val auth = AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { now })
        assertEquals("new", auth.validAccessToken())
        assertEquals(listOf("grant_type=refresh_token"), requests)
        now = 1_100
        assertEquals("new", auth.validAccessToken())
        assertEquals(1, requests.size)
    }

    @Test
    fun `a revoked refresh token signs out`() = runTest {
        secrets.update { it.copy(session = Session("old", "r-old", expiresAt = 0, userId = "u1"), dataKey = "k") }
        val http = mockClient { json("""{"error":"invalid_grant","error_description":"Invalid Refresh Token"}""", HttpStatusCode.BadRequest) }
        val auth = AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { 10 })
        try {
            auth.validAccessToken()
            fail("expected NotSignedInException")
        } catch (_: NotSignedInException) {
        }
        assertNull(secrets.read().session)
        assertEquals(AuthState.SIGNED_OUT, auth.state.value)
    }

    @Test
    fun `api retries once with a refreshed token on 401`() = runTest {
        secrets.update { it.copy(session = Session("stale", "r", expiresAt = 99_999, userId = "u1")) }
        val http = mockClient { req ->
            when (req.url.encodedPath) {
                "/auth/v1/token" -> json(tokenJson("fresh"))
                "/api/widget/snapshot" -> {
                    requests += req.headers["Authorization"]!!
                    if (req.headers["Authorization"] == "Bearer stale") json("{}", HttpStatusCode.Unauthorized)
                    else json("""{"today":"2026-10-31","tasks":[{"id":"t1","title":"Hi"}],"unknownField":1}""")
                }
                else -> error("unexpected ${req.url}")
            }
        }
        val auth = AuthRepository(SupabaseAuthApi(http, TestConfig), secrets, clock = { 0 })
        val snapshot = LifeMappApi(http, TestConfig, auth).snapshots.snapshot("Europe/Madrid")
        assertEquals(listOf("Bearer stale", "Bearer fresh"), requests)
        assertEquals("Hi", snapshot.tasks.single().title)
    }

    @Test
    fun `google sign-in uses PKCE and survives a new repository instance`() = runTest {
        val http = mockClient { req ->
            requests += (req.body as io.ktor.http.content.TextContent).text
            json(tokenJson("g1"))
        }
        val url = AuthRepository(SupabaseAuthApi(http, TestConfig), secrets).startGoogleSignIn()
        assertTrue(url, url.contains("provider=google") && url.contains("code_challenge_method=s256") && url.contains("lifemappwidgets"))
        // Process died behind the Custom Tab: a fresh repository completes it
        val fresh = AuthRepository(SupabaseAuthApi(http, TestConfig), secrets)
        fresh.completeGoogleSignIn("the-code")
        assertTrue(requests.single().contains("\"auth_code\":\"the-code\""))
        assertEquals("g1", secrets.read().session?.accessToken)
        assertNull(secrets.read().pendingPkceVerifier)
    }

    @Test
    fun `signing in as another user drops the previous data key`() = runTest {
        secrets.update { it.copy(dataKey = "k", dataKeyUserId = "someone-else") }
        val auth = AuthRepository(SupabaseAuthApi(mockClient { json(tokenJson("a")) }, TestConfig), secrets)
        auth.signIn("ana@example.com", "pw")
        assertNull(secrets.read().dataKey)
    }
}
