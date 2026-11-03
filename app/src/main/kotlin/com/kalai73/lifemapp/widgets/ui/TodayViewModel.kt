package com.kalai73.lifemapp.widgets.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.data.repository.TaskText
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.DashboardTab
import com.kalai73.lifemapp.widgets.core.presentation.MonthlyPresenter
import com.kalai73.lifemapp.widgets.core.presentation.MonthlyUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetPresenter
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.widget.DeepLink
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The Today screen: what it draws (the widgets' [WidgetUi], from the shared presenter), what widget taps ask it to
 * show, and the edits made on it (tasks, items, topics, scoring). Sign-in, unlock and settings are [AppViewModel]'s.
 */
class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = LifeMappGraph.get(app)

    /** What the Today screen draws: decrypted and formatted like the widgets (shared presenter). */
    private val _today = MutableStateFlow(WidgetUi.SignedOut)
    val today: StateFlow<WidgetUi> = _today.asStateFlow()

    /** What the Monthly screen draws: decrypted and formatted. */
    private val _monthly = MutableStateFlow(MonthlyUi.SignedOut)
    val monthly: StateFlow<MonthlyUi> = _monthly.asStateFlow()

    val activeTab = MutableStateFlow(DashboardTab.TODAY)
    fun selectTab(tab: DashboardTab) { activeTab.value = tab }

    private val _crossTabScroll = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val crossTabScroll: SharedFlow<String> = _crossTabScroll.asSharedFlow()

    fun requestTodaySection(section: String) {
        activeTab.value = DashboardTab.TODAY
        _crossTabScroll.tryEmit(section)
    }

    private val _busy = MutableStateFlow(false)

    /** The last failed edit's message, until the next edit starts or [clearError] */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Sign-in, unlock and lock change the secrets, which aren't observable: they bump the widgets' invalidation
        viewModelScope.launch {
            combine(graph.cache.state, graph.widgetInvalidations) { _, _ ->
                presentToday() to presentMonthly()
            }.collect { (t, m) ->
                _today.value = t
                _monthly.value = m
            }
        }
    }

    private suspend fun presentToday(): WidgetUi {
        val secrets = runCatching { graph.secrets.read() }.getOrNull()
        val auth = if (secrets?.session != null) AuthState.SIGNED_IN else AuthState.SIGNED_OUT
        return WidgetPresenter.present(graph.cache.current(), auth, secrets?.dataKey)
    }

    private suspend fun presentMonthly(): MonthlyUi {
        val secrets = runCatching { graph.secrets.read() }.getOrNull()
        val auth = if (secrets?.session != null) AuthState.SIGNED_IN else AuthState.SIGNED_OUT
        return MonthlyPresenter.present(graph.cache.current(), auth, secrets?.dataKey)
    }

    // ── Widget taps ──────────────────────────────────────────────────────────

    /** The item (subsubdomain) a widget tap asked to open in the item modal, until it's shown */
    private val _openItem = MutableStateFlow<String?>(null)
    val openItem: StateFlow<String?> = _openItem.asStateFlow()

    fun itemOpened() { _openItem.value = null }

    /** The event or task a widget tap asked to show in the details modal, until it's shown */
    private val _openEntry = MutableStateFlow<TodayEntry?>(null)
    val openEntry: StateFlow<TodayEntry?> = _openEntry.asStateFlow()

    fun entryOpened() { _openEntry.value = null }

    /** The section (a LifeMappWidgets.SECTION_ value or "domain-<id>") a widget tap asked to scroll to, until it's shown */
    private val _openSection = MutableStateFlow<String?>(null)
    val openSection: StateFlow<String?> = _openSection.asStateFlow()

    fun sectionOpened() { _openSection.value = null }

    /** A widget tap: what it points at opens on the Today screen. */
    fun open(link: DeepLink) {
        when (link) {
            is DeepLink.Item -> _openItem.value = link.itemId
            is DeepLink.Event -> _openEntry.value = TodayEntry.Event(link.eventId, link.start)
            is DeepLink.Task -> _openEntry.value = TodayEntry.Task(link.taskId)
            is DeepLink.Section -> {
                link.topicId?.let(::expandTopic)
                _openSection.value = link.section
            }
        }
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    /** Reload button / pull-to-refresh (debounced like the widgets' reload). */
    fun reload() {
        if (!SyncScheduler.canSyncManually()) return
        viewModelScope.launch {
            graph.snapshots.markSyncing()
            SyncScheduler.syncNow(getApplication(), manual = true)
        }
    }

    // ── Tasks ────────────────────────────────────────────────────────────────

    /** Hides the task at once, then a worker confirms it with the server (the widgets' path). */
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            graph.tasks.markCompletedLocally(taskId)
            refreshWidgets()
            SyncScheduler.completeTask(getApplication(), taskId)
        }
    }

    fun dismissCompletionFailure() {
        viewModelScope.launch { graph.tasks.dismissFailure() }
    }

    /** A LIFE MAPP task's whole title and description for the task dialog's editor. Throws on network errors. */
    suspend fun loadTaskText(taskId: String): TaskText = graph.tasks.loadText(taskId)

    /**
     * Saves a task's new [title] and/or [html] description (null keeps it) on the server and in the cached
     * snapshot, encrypted when the account is.
     */
    fun saveTask(taskId: String, title: String?, html: String?) {
        if (title != null && title.isBlank()) return
        if (title == null && html == null) return
        run {
            if (!graph.tasks.saveText(taskId, title, html, encrypted)) fail(R.string.error_task_locked)
        }
    }

    // ── Items and topics ─────────────────────────────────────────────────────

    /** An item's whole text for the edit dialog, fresh from the server. Throws on network errors. */
    suspend fun loadItemText(itemId: String): ItemText = graph.items.loadText(itemId)

    private val images = ItemImageLoader(graph.items)

    /** An image item's picture. Never throws: failures are [ItemImage.Failed]. */
    suspend fun loadItemImage(itemId: String): ItemImage = images.load(itemId)

    /**
     * Saves an item's new [name] and/or [html] text (null keeps it) on the server and in the cached snapshot,
     * encrypted when the account is.
     */
    fun saveItem(itemId: String, name: String?, html: String?) {
        if (name != null && name.isBlank()) return
        if (name == null && html == null) return
        run {
            if (!graph.items.save(itemId, name, html, encrypted)) fail(R.string.error_item_locked)
        }
    }

    /** Moves an item one place up or down in its topic (the item menu's Move up / Move down). */
    fun moveItem(itemId: String, topicId: String, up: Boolean) = run { graph.items.move(itemId, topicId, up) }

    /** Adds or removes an item's warning icon. */
    fun setItemWarning(itemId: String, warning: Boolean) = run { graph.items.setWarning(itemId, warning) }

    fun deleteItem(itemId: String) = run { graph.items.delete(itemId) }

    /** Collapses or expands an item (an image item's header tap), like [setTopicCollapsed]. */
    fun setItemCollapsed(itemId: String, collapsed: Boolean) =
        toggle { graph.items.setItemCollapsed(itemId, collapsed) { refreshWidgets() } }

    /**
     * Collapses or expands a topic: the cached snapshot (app and widgets) at once, then the server. Not behind
     * [run]'s busy gate, so quick taps on several headers all land; the repository undoes a refused change.
     */
    fun setTopicCollapsed(topicId: String, collapsed: Boolean) =
        toggle { graph.items.setTopicCollapsed(topicId, collapsed) { refreshWidgets() } }

    /** A widget topic header's tap: the topic opens expanded (saved like a header tap here); no-op if it already is. */
    private fun expandTopic(topicId: String) {
        viewModelScope.launch {
            val snapshot = graph.cache.current().snapshot ?: return@launch
            val topic = (listOfNotNull(snapshot.integrity) + snapshot.todayDomains)
                .flatMap { it.topics }.firstOrNull { it.id == topicId } ?: return@launch
            if (topic.collapsed) setTopicCollapsed(topicId, false)
        }
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    /** Hides or shows a domain on Today or Monthly; unhidable domains (1, 5, 10) always stay. */
    fun toggleDomainHidden(domainId: String) {
        val domain = (_today.value.scoring?.domains ?: _monthly.value.scoring?.domains)?.firstOrNull { it.id == domainId }
            ?: _monthly.value.scoring?.domains?.firstOrNull { it.id == domainId }
            ?: return
        if (domain.unhidable) return
        toggle { graph.scoring.setHidden(mapOf(domainId to !domain.hidden)) }
    }

    /** Hides (true) or shows every domain that can be hidden (the scoring section's eye button). */
    fun setAllDomainsHidden(hidden: Boolean) {
        val domains = _today.value.scoring?.domains?.filterNot { it.unhidable } ?: return
        toggle { graph.scoring.setHidden(domains.associate { it.id to hidden }) }
    }

    /** Hides (true) or shows every hidable monthly domain (11–19, exempting Trayectory 10). */
    fun setAllMonthlyDomainsHidden(hidden: Boolean) {
        val domains = _monthly.value.scoring?.domains?.filterNot { it.unhidable } ?: return
        toggle { graph.scoring.setHidden(domains.associate { it.id to hidden }) }
    }

    /**
     * Saves new ratings (domain id → 1–10) with the web's bulk route, encrypted when the account is; the cached
     * snapshot takes them at once, so the cards and the widgets' average follow without a sync.
     */
    fun rateDomains(ratings: Map<String, Int>) = run {
        if (!graph.scoring.rate(ratings, encrypted)) fail(R.string.error_rating_locked)
    }

    // ── Running edits ────────────────────────────────────────────────────────

    fun clearError() { _error.value = null }

    private val cache: StateFlow<CachedState> = graph.cache.state.stateIn(viewModelScope, SharingStarted.Eagerly, CachedState())

    /** Whether the account is end-to-end encrypted, as the cached snapshot says */
    private val encrypted: Boolean get() = cache.value.snapshot?.settings?.encryptionEnabled == true

    /** Re-renders every placed widget (installed by the widget module; no Glance knowledge needed here). */
    private suspend fun refreshWidgets() = graph.refreshWidgets()

    private fun fail(res: Int) { _error.value = getApplication<Application>().getString(res) }

    private fun report(e: Exception) {
        if (e is IOException) fail(R.string.error_network) else _error.value = e.message ?: e.javaClass.simpleName
    }

    /** One edit at a time; the widgets follow every edit, failed ones too (an optimistic change may have been undone). */
    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report(e)
            } finally {
                _busy.update { false }
                refreshWidgets()
            }
        }
    }

    /** Like [run] without the busy gate, for quick toggles. */
    private fun toggle(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report(e)
            }
            refreshWidgets()
        }
    }
}
