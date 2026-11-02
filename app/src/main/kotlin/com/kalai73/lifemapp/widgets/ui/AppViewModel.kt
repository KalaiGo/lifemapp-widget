package com.kalai73.lifemapp.widgets.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.auth.AuthException
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetPresenter
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
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
import kotlinx.coroutines.CancellationException

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

/** Opening the app syncs when the cached snapshot is older than this */
private const val OPEN_SYNC_AFTER_MS = 5 * 60 * 1000L

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = LifeMappGraph.get(app)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val cache: StateFlow<CachedState> = graph.cache.state.stateIn(viewModelScope, SharingStarted.Eagerly, CachedState())

    /** What the Today screen draws: decrypted and formatted like the widgets (shared presenter). */
    private val _today = MutableStateFlow(WidgetUi.SignedOut)
    val today: StateFlow<WidgetUi> = _today.asStateFlow()

    /** The item (subsubdomain) a widget tap asked to open in the Today screen's item modal, until it's shown */
    private val _openItem = MutableStateFlow<String?>(null)
    val openItem: StateFlow<String?> = _openItem.asStateFlow()

    fun openItem(itemId: String) { _openItem.value = itemId }

    fun itemOpened() { _openItem.value = null }

    /** The Today section (a LifeMappWidgets.SECTION_ value) a widget tap asked to scroll to, until it's shown */
    private val _openSection = MutableStateFlow<String?>(null)
    val openSection: StateFlow<String?> = _openSection.asStateFlow()

    fun openSection(section: String) { _openSection.value = section }

    fun sectionOpened() { _openSection.value = null }

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
        // The encryption password is usually the account password: try it so the unlock screen isn't shown
        // right after typing it. Silent on failure — the unlock screen then asks as before.
        tryUnlockWith(password)
    }

    private suspend fun tryUnlockWith(password: String) {
        val encrypted = graph.cache.current().snapshot?.settings?.encryptionEnabled == true
        if (!encrypted || graph.encryption.dataKey() != null) return
        val result = runCatching { graph.encryption.unlock(password) }.getOrNull()
        if (result == UnlockResult.Unlocked) LifeMappWidgets.updateAll(getApplication())
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

    /**
     * Opening the app (each time it comes to the foreground) syncs when the cached snapshot is older than
     * [OPEN_SYNC_AFTER_MS], so new data (e.g. after a server update) shows without waiting for the 30-minute
     * worker or a tap on reload.
     */
    fun syncIfStale(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            if (runCatching { graph.secrets.read() }.getOrNull()?.session == null) return@launch
            val state = graph.cache.current()
            if (state.isSyncing(now) || now - state.lastSuccessAt < OPEN_SYNC_AFTER_MS) return@launch
            SyncScheduler.syncNow(getApplication())
        }
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

    /** An item's whole text for the edit dialog, fresh from the server. Throws on network errors. */
    suspend fun loadItemText(itemId: String): ItemText = graph.items.loadText(itemId)

    /**
     * Saves an item's new [name] and/or [html] text (null keeps it) on the server and in the cached snapshot,
     * encrypted when the account is.
     */
    fun saveItem(itemId: String, name: String?, html: String?) {
        if (name != null && name.isBlank()) return
        if (name == null && html == null) return
        run {
            val encrypted = cache.value.snapshot?.settings?.encryptionEnabled == true
            if (!graph.items.save(itemId, name, html, encrypted)) fail(R.string.error_item_locked)
            LifeMappWidgets.updateAll(getApplication())
        }
    }

    /** Moves an item one place up or down in its topic (the item menu's Move up / Move down). */
    fun moveItem(itemId: String, topicId: String, up: Boolean) = run {
        graph.items.move(itemId, topicId, up)
        LifeMappWidgets.updateAll(getApplication())
    }

    /** Adds or removes an item's warning icon. */
    fun setItemWarning(itemId: String, warning: Boolean) = run {
        graph.items.setWarning(itemId, warning)
        LifeMappWidgets.updateAll(getApplication())
    }

    fun deleteItem(itemId: String) = run {
        graph.items.delete(itemId)
        LifeMappWidgets.updateAll(getApplication())
    }

    /**
     * Collapses or expands an Integrity topic: the cached snapshot (app and widgets) at once, then the server.
     * Not behind [run]'s busy gate, so quick taps on several headers all land; the repository undoes a refused change.
     */
    fun setTopicCollapsed(topicId: String, collapsed: Boolean) =
        toggle { graph.items.setTopicCollapsed(topicId, collapsed) { LifeMappWidgets.updateAll(getApplication()) } }

    /** A widget topic header's tap: the topic opens expanded (saved like a header tap in the app); no-op if it already is. */
    fun expandTopic(topicId: String) {
        viewModelScope.launch {
            val snapshot = graph.cache.current().snapshot ?: return@launch
            val topic = (listOfNotNull(snapshot.integrity) + snapshot.todayDomains)
                .flatMap { it.topics }.firstOrNull { it.id == topicId } ?: return@launch
            if (topic.collapsed) setTopicCollapsed(topicId, false)
        }
    }

    /** Hides or shows a domain on Today (the scoring section's tap); Integrity and Tenacity always stay. */
    fun toggleDomainHidden(domainId: String) {
        val domain = _today.value.scoring?.domains?.firstOrNull { it.id == domainId } ?: return
        if (domain.unhidable) return
        toggle { graph.scoring.setHidden(mapOf(domainId to !domain.hidden)) }
    }

    /** Hides (true) or shows every domain that can be hidden (the scoring section's eye button). */
    fun setAllDomainsHidden(hidden: Boolean) {
        val domains = _today.value.scoring?.domains?.filterNot { it.unhidable } ?: return
        toggle { graph.scoring.setHidden(domains.associate { it.id to hidden }) }
    }

    /**
     * Saves new ratings (domain id → 1–10) with the web's bulk route, encrypted when the account is; the cached
     * snapshot takes them at once, so the cards and the widgets' average follow without a sync.
     */
    fun rateDomains(ratings: Map<String, Int>) = run {
        val encrypted = cache.value.snapshot?.settings?.encryptionEnabled == true
        if (!graph.scoring.rate(ratings, encrypted)) fail(R.string.error_rating_locked)
        LifeMappWidgets.updateAll(getApplication())
    }

    private fun toggle(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                fail(R.string.error_network)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
            LifeMappWidgets.updateAll(getApplication())
        }
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

    /** Color scheme for the app and every widget on this device; null follows the web's choice. */
    fun setColorScheme(scheme: ColorSchemeName?) {
        viewModelScope.launch {
            graph.cache.update { it.copy(colorSchemeOverride = scheme?.id) }
            LifeMappWidgets.updateAll(getApplication())
        }
    }
}
