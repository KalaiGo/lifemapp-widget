package com.kalai73.lifemapp.widgets.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.auth.AuthException
import com.kalai73.lifemapp.widgets.core.data.auth.NotWhitelistedException
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetPresenter
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.data.repository.UnlockResult
import com.kalai73.lifemapp.widgets.core.model.AuthState
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

enum class Screen { LOADING, SIGN_IN, UNLOCK, HOME }

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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = LifeMappGraph.get(app)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val cache: StateFlow<CachedState> = graph.cache.state.stateIn(viewModelScope, SharingStarted.Eagerly, CachedState())

    /** What the Today screen draws: decrypted and formatted like the widgets (shared presenter). */
    private val _today = MutableStateFlow(WidgetUi.SignedOut)
    val today: StateFlow<WidgetUi> = _today.asStateFlow()

    val colorScheme: ColorSchemeName get() = ColorSchemeName.from(cache.value.snapshot?.settings?.colorScheme)
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
        _today.value = WidgetPresenter.present(
            graph.cache.current(),
            if (session != null) AuthState.SIGNED_IN else AuthState.SIGNED_OUT,
            secrets?.dataKey,
        )
        _state.update { s ->
            s.copy(
                screen = when {
                    session == null -> Screen.SIGN_IN
                    encryptionEnabled && !unlocked && !s.unlockSkipped -> Screen.UNLOCK
                    else -> Screen.HOME
                },
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
            } catch (e: NotWhitelistedException) {
                fail(R.string.error_not_whitelisted)
            } catch (e: AuthException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: IOException) {
                fail(R.string.error_network)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(busy = false) }
                refreshScreen()
            }
        }
    }

    private fun fail(res: Int) = _state.update { it.copy(error = getApplication<Application>().getString(res)) }

    fun signIn(email: String, password: String) = run {
        graph.auth.signIn(email, password)
        afterSignIn()
    }

    suspend fun googleSignInUrl(): String = graph.auth.startGoogleSignIn()

    fun completeGoogleSignIn(code: String) = run {
        graph.auth.completeGoogleSignIn(code)
        afterSignIn()
    }

    fun reportError(message: String) = _state.update { it.copy(error = message) }

    private suspend fun afterSignIn() {
        // First snapshot tells whether the account is end-to-end encrypted
        graph.snapshots.refresh()
        SyncScheduler.schedulePeriodic(getApplication())
        LifeMappWidgets.updateAll(getApplication())
    }

    fun unlock(password: String) = run {
        when (graph.encryption.unlock(password)) {
            UnlockResult.Unlocked -> LifeMappWidgets.updateAll(getApplication())
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
        LifeMappWidgets.updateAll(getApplication())
    }

    fun syncNow() {
        SyncScheduler.syncNow(getApplication(), manual = true)
    }

    /** Reload button / pull-to-refresh on the Today screen (debounced like the widgets' reload). */
    fun reload() {
        if (!SyncScheduler.canSyncManually()) return
        viewModelScope.launch {
            graph.snapshots.markSyncing()
            SyncScheduler.syncNow(getApplication(), manual = true)
        }
    }

    /** Hides the task at once, then a worker confirms it with the server (the widgets' path). */
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            graph.tasks.markCompletedLocally(taskId)
            LifeMappWidgets.updateAll(getApplication())
            SyncScheduler.completeTask(getApplication(), taskId)
        }
    }

    /** Renames an Integrity item on the server and in the cached snapshot. */
    fun renameItem(itemId: String, text: String) {
        if (text.isBlank()) return
        run {
            val encrypted = cache.value.snapshot?.settings?.encryptionEnabled == true
            if (!graph.items.rename(itemId, text, encrypted)) fail(R.string.error_item_locked)
            LifeMappWidgets.updateAll(getApplication())
        }
    }

    fun deleteItem(itemId: String) = run {
        graph.items.delete(itemId)
        LifeMappWidgets.updateAll(getApplication())
    }

    fun dismissCompletionFailure() {
        viewModelScope.launch { graph.tasks.dismissFailure() }
    }

    fun relinkWeb() = run { WebLauncher.resetHandoff(getApplication()) }

    fun signOut() = run {
        SyncScheduler.cancelAll(getApplication())
        graph.signOut()
        _state.update { it.copy(unlockSkipped = false) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Light / dark / system for the app and every widget on this device. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            graph.cache.update { it.copy(themeMode = mode) }
            LifeMappWidgets.updateAll(getApplication())
        }
    }
}
