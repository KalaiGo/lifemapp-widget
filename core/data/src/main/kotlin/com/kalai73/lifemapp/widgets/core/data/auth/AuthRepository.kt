package com.kalai73.lifemapp.widgets.core.data.auth

import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import com.kalai73.lifemapp.widgets.core.data.secure.Session
import com.kalai73.lifemapp.widgets.core.model.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Signed out, or the refresh token was revoked: the user has to sign in again. */
class NotSignedInException : Exception("Not signed in")

class AuthRepository(
    private val api: SupabaseAuthApi,
    private val secrets: SecretStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val refreshMutex = Mutex()
    private val _state = MutableStateFlow(AuthState.SIGNED_OUT)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = if (secrets.read().session != null) AuthState.SIGNED_IN else AuthState.SIGNED_OUT
    }

    suspend fun currentSession(): Session? = secrets.read().session

    suspend fun signIn(email: String, password: String) = finishSignIn(api.signInWithPassword(email, password))

    suspend fun startGoogleSignIn(): String {
        val pkce = api.newPkce()
        secrets.update { it.copy(pendingPkceVerifier = pkce.verifier) }
        return api.googleAuthorizeUrl(pkce)
    }

    /** Handles lifemappwidgets://auth/callback?code=… */
    suspend fun completeGoogleSignIn(code: String) {
        val verifier = secrets.read().pendingPkceVerifier ?: throw AuthException("Sign-in expired, please try again")
        secrets.update { it.copy(pendingPkceVerifier = null) }
        finishSignIn(api.exchangeCode(code, verifier))
    }

    private suspend fun finishSignIn(session: Session) {
        // Beta access is enforced only at registration (Supabase "Before User Created" hook), like the web app
        secrets.update { old ->
            // The data key belongs to one account
            if (old.dataKeyUserId != null && old.dataKeyUserId != session.userId) {
                old.copy(session = session, dataKey = null, dataKeyUserId = null, lastWebHandoffAt = 0)
            } else {
                old.copy(session = session)
            }
        }
        _state.value = AuthState.SIGNED_IN
    }

    /**
     * An access token valid for at least another minute, refreshing it first when needed.
     * @throws NotSignedInException when there's no session or the refresh token was revoked
     */
    suspend fun validAccessToken(forceRefresh: Boolean = false): String = refreshMutex.withLock {
        val session = secrets.read().session ?: throw NotSignedInException()
        if (!forceRefresh && session.expiresAt - clock() > 60) return session.accessToken
        val refreshed = try {
            api.refresh(session.refreshToken)
        } catch (e: AuthException) {
            // Refresh token revoked or already used: the session is gone
            secrets.update { it.copy(session = null) }
            _state.value = AuthState.SIGNED_OUT
            throw NotSignedInException()
        }
        secrets.update { it.copy(session = refreshed) }
        refreshed.accessToken
    }

    /** Signs out and forgets the data key; the caller clears the widget cache. */
    suspend fun signOut() {
        secrets.read().session?.let { api.signOut(it.accessToken) }
        secrets.clear()
        _state.value = AuthState.SIGNED_OUT
    }
}
