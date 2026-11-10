package com.kalai73.lifemapp.widgets.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.auth.AuthException
import com.kalai73.lifemapp.widgets.core.data.repository.UnlockResult
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.ThemeMode
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import com.kalai73.lifemapp.widgets.core.weblauncher.WebLauncher
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class Screen { LOADING, SIGN_IN, SECOND_STEP, UNLOCK, HOME }

data class AppUiState(
    val screen: Screen = Screen.LOADING,
    val busy: Boolean = false,
    val error: String? = null,
    val email: String? = null,
    val encryptionEnabled: Boolean = false,
    val unlocked: Boolean = false,
    /** The user chose "Not now" on the unlock screen this session */
    val unlockSkipped: Boolean = false,
    val placedWidgets: Int = 0,
)

/** Opening the app syncs when the cached snapshot is older than this */
internal const val OPEN_SYNC_AFTER_MS = 5 * 60 * 1000L

/**
 * The account side of the app: which screen shows (sign-in, unlock, home), sign-in and sign-out, the encryption
 * unlock, sync and this device's settings. The Today screen itself is [TodayViewModel]'s.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = LifeMappGraph.get(app)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val cache: StateFlow<CachedState> = graph.cache.state.stateIn(viewModelScope, SharingStarted.Eagerly, CachedState())

    val colorScheme: ColorSchemeName get() = cache.value.colorScheme
    val uiStyle: UiStyle get() = UiStyle.from(cache.value.snapshot?.settings?.uiStyle)

    init {
        viewModelScope.launch {
            graph.auth.load()
            refreshScreen()
        }
        viewModelScope.launch {
            graph.cache.state.collect { refreshScreen() }
        }
    }

    private suspend fun refreshScreen() {
        val secrets = runCatching { graph.secrets.read() }.getOrNull()
        val session = secrets?.session
        val encryptionEnabled = graph.cache.current().snapshot?.settings?.encryptionEnabled == true
        val unlocked = secrets?.dataKey != null
        _state.update { s ->
            s.copy(
                screen = screenFor(
                    signedIn = session != null,
                    encryptionEnabled,
                    unlocked,
                    s.unlockSkipped,
                    awaitingSecondStep = session?.secondStepFactorId != null,
                ),
                email = session?.email,
                encryptionEnabled = encryptionEnabled,
                unlocked = unlocked,
                placedWidgets = runCatching { LifeMappWidgets.placedCount(getApplication()) }.getOrDefault(0),
            )
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: AuthException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: IOException) {
                fail(R.string.error_network)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(busy = false) }
                // Widgets (and the Today screen, through the same invalidation) follow every action, failed ones too
                refreshWidgets()
                refreshScreen()
            }
        }
    }

    /** Re-renders every placed widget (installed by the widget module; no Glance knowledge needed here). */
    private suspend fun refreshWidgets() = graph.refreshWidgets()

    private fun fail(res: Int) = _state.update { it.copy(error = getApplication<Application>().getString(res)) }

    /** Kept in memory from the password sign-in to the second login step, for [tryUnlockWith]. */
    private var passwordAwaitingSecondStep: String? = null

    fun signIn(email: String, password: String) = run {
        graph.auth.signIn(email, password)
        if (awaitingSecondStep()) {
            passwordAwaitingSecondStep = password
        } else {
            afterSignIn()
            tryUnlockWith(password)
        }
    }

    private suspend fun awaitingSecondStep() = graph.auth.currentSession()?.secondStepFactorId != null

    fun verifySecondStep(code: String) = run {
        try {
            graph.auth.verifySecondStep(code)
        } catch (e: AuthException) {
            if (e.isRejectedCode) {
                fail(R.string.error_invalid_code)
                return@run
            }
            throw e
        }
        afterSignIn()
        passwordAwaitingSecondStep?.let { tryUnlockWith(it) }
        passwordAwaitingSecondStep = null
    }

    /**
     * The encryption password is usually the account password: try it so the unlock screen isn't shown right after
     * typing it. Silent on failure — the unlock screen then asks as before.
     */
    private suspend fun tryUnlockWith(password: String) {
        val encrypted = graph.cache.current().snapshot?.settings?.encryptionEnabled == true
        if (!encrypted || graph.encryption.dataKey() != null) return
        runCatching { graph.encryption.unlock(password) }
    }

    suspend fun googleSignInUrl(): String = graph.auth.startGoogleSignIn()

    fun completeGoogleSignIn(code: String) = run {
        graph.auth.completeGoogleSignIn(code)
        if (!awaitingSecondStep()) afterSignIn()
    }

    fun reportError(message: String) = _state.update { it.copy(error = message) }

    private suspend fun afterSignIn() {
        // First snapshot tells whether the account is end-to-end encrypted
        graph.snapshots.refresh()
        SyncScheduler.schedulePeriodic(getApplication())
    }

    fun unlock(password: String) = run {
        when (graph.encryption.unlock(password)) {
            UnlockResult.Unlocked -> Unit
            UnlockResult.WrongPassword -> fail(R.string.error_wrong_password)
            UnlockResult.NotConfigured -> fail(R.string.error_not_configured)
        }
    }

    fun skipUnlock() {
        _state.update { it.copy(unlockSkipped = true, error = null) }
        viewModelScope.launch { refreshScreen() }
    }

    fun showUnlock() {
        _state.update { it.copy(unlockSkipped = false, error = null) }
        viewModelScope.launch { refreshScreen() }
    }

    fun lock() = run {
        graph.encryption.lock()
        _state.update { it.copy(unlockSkipped = true) }
    }

    fun syncNow() {
        SyncScheduler.syncNow(getApplication(), manual = true)
    }

    /**
     * Opening the app (each time it comes to the foreground) syncs when the cached snapshot is older than
     * [OPEN_SYNC_AFTER_MS], so new data (e.g. after a server update) shows without waiting for the 30-minute
     * worker or a tap on reload.
     */
    fun syncIfStale(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val session = runCatching { graph.secrets.read() }.getOrNull()?.session ?: return@launch
            if (session.secondStepFactorId != null) {
                // Back from the web, perhaps with two-factor login turned off by a recovery code
                val waiting = runCatching { graph.auth.recheckSecondStep() }.getOrDefault(true)
                refreshScreen()
                if (waiting) return@launch
                SyncScheduler.schedulePeriodic(getApplication())
                refreshWidgets()
            }
            if (shouldSyncOnOpen(graph.cache.current(), now)) SyncScheduler.syncNow(getApplication())
        }
    }

    fun relinkWeb() = run { WebLauncher.resetHandoff(getApplication()) }

    fun signOut() = run {
        SyncScheduler.cancelAll(getApplication())
        graph.signOut()
        passwordAwaitingSecondStep = null
        _state.update { it.copy(unlockSkipped = false) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Light / dark / system for the app and every widget on this device. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            graph.cache.update { it.copy(themeMode = mode) }
            refreshWidgets()
        }
    }

    /** Color scheme for the app and every widget on this device; null follows the web's choice. */
    fun setColorScheme(scheme: ColorSchemeName?) {
        viewModelScope.launch {
            graph.cache.update { it.copy(colorSchemeOverride = scheme?.id) }
            refreshWidgets()
        }
    }
}

/** Which screen the app shows: sign-in, the second login step, the encryption unlock (until skipped this session), or home. */
internal fun screenFor(
    signedIn: Boolean,
    encryptionEnabled: Boolean,
    unlocked: Boolean,
    unlockSkipped: Boolean,
    awaitingSecondStep: Boolean = false,
): Screen = when {
    !signedIn -> Screen.SIGN_IN
    awaitingSecondStep -> Screen.SECOND_STEP
    encryptionEnabled && !unlocked && !unlockSkipped -> Screen.UNLOCK
    else -> Screen.HOME
}

/** Opening the app syncs unless a sync is running or the last success is under [OPEN_SYNC_AFTER_MS] old. */
internal fun shouldSyncOnOpen(state: CachedState, now: Long): Boolean =
    !state.isSyncing(now) && now - state.lastSuccessAt >= OPEN_SYNC_AFTER_MS
