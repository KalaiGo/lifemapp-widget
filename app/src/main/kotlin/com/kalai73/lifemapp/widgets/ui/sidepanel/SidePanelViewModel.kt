package com.kalai73.lifemapp.widgets.ui.sidepanel

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.content.edit
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.api.FeedRequestException
import com.kalai73.lifemapp.widgets.core.data.api.LinksApi
import com.kalai73.lifemapp.widgets.core.data.repository.FeedState
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.model.Feed
import com.kalai73.lifemapp.widgets.core.model.FeedFilter
import com.kalai73.lifemapp.widgets.core.model.PlanLimits
import com.kalai73.lifemapp.widgets.core.presentation.panel.LinkExtractor
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelScope
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelSort
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelUi
import com.kalai73.lifemapp.widgets.core.presentation.panel.SidePanelPresenter
import kotlinx.coroutines.CancellationException
import com.kalai73.lifemapp.widgets.core.model.CachedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** The panel's tabs: the Links panel's three stream modes, and Feeds (LinksStreamModeToggle). */
enum class PanelMode { EDITOR, LINKS, ALERTS, FEEDS }

/** The pane shown on a phone: the tree (folders), the stream, or (Feeds) the reader. */
enum class PanelPane { SIDEBAR, STREAM, READER }

data class SidePanelState(
    val open: Boolean = false,
    val mode: PanelMode = PanelMode.LINKS,
    val pane: PanelPane = PanelPane.STREAM,
    val scope: PanelScope = PanelScope(),
    /** The quick links landing shows in the stream (QuickLinksLanding) */
    val landing: Boolean = false,
    val sort: PanelSort = PanelSort.STRUCTURE,
    /** Heading levels whose links are hidden (LinksStream heading toggles) */
    val hiddenHeadingLevels: Set<Int> = emptySet(),
    /** The Feeds reader's item */
    val feedItemId: String? = null,
)

/** Where an item's autosave stands (AutoSaveIndicator) */
enum class SaveStatus { IDLE, SAVING, SAVED, FAILED }

/**
 * The web's side panel (LinksPanel / FeedsPanel, app-store.ts): whether it is open and on which tab, what the Links
 * panel is scoped to (remembered like `lifemapp.links.lastView`), the decrypted tree it shows, and its edits. Feeds
 * live in [LifeMappGraph.feeds].
 */
class SidePanelViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = LifeMappGraph.get(app)
    private val prefs = app.getSharedPreferences("side_panel", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(restore())
    val state: StateFlow<SidePanelState> = _state.asStateFlow()

    private val _panel = MutableStateFlow(PanelUi())
    val panel: StateFlow<PanelUi> = _panel.asStateFlow()

    val feeds: StateFlow<FeedState> = graph.feeds.state

    /** A short message for the user (failed save, feed error), until shown */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() { _message.value = null }

    private val limits = MutableStateFlow(PlanLimits.forTier("basic"))
    private val customTags = MutableStateFlow<Map<String, String>>(emptyMap())
    private val titles = MutableStateFlow<Map<String, String>>(emptyMap())
    /** URLs already sent to /api/links/titles, so each is asked once per session */
    private val askedTitles = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(graph.cache.state, graph.widgetInvalidations, limits, customTags, titles) { cache, _, limits, tags, titles ->
                val key = runCatching { graph.secrets.read().dataKey }.getOrNull()
                SidePanelPresenter.present(cache.snapshot, key, limits, tags, titles)
            }.collect { ui ->
                _panel.value = ui
                fetchTitles(ui)
                dropStaleSelection(ui)
            }
        }
    }

    // ── Opening and tabs (setLinksPanelOpen, setLinksPanelMode, openLinksPanelOnDomain, switchSidePanel) ──

    /** Opens the panel on [mode], or on the last used tab; loads plan limits, tags and (Feeds) the feeds. */
    fun open(mode: PanelMode? = null) {
        _state.update { it.copy(open = true, mode = mode ?: it.mode, pane = PanelPane.STREAM, landing = it.landing && mode != PanelMode.ALERTS) }
        persist()
        onOpen()
    }

    /** A domain card header: the Editor tab scoped to that domain */
    fun openOnDomain(domainId: String) {
        _state.update {
            it.copy(open = true, mode = PanelMode.EDITOR, pane = PanelPane.STREAM, landing = false, scope = PanelScope(domainId = domainId))
        }
        persist()
        onOpen()
    }

    private fun onOpen() {
        viewModelScope.launch {
            limits.value = graph.items.planLimits()
            customTags.value = graph.items.customTags()
        }
        if (_state.value.mode == PanelMode.FEEDS) loadFeeds()
    }

    fun close() = _state.update { it.copy(open = false) }

    fun setMode(mode: PanelMode) {
        _state.update {
            it.copy(mode = mode, pane = PanelPane.STREAM, landing = if (mode == PanelMode.ALERTS) false else it.landing)
        }
        persist()
        if (mode == PanelMode.FEEDS) loadFeeds()
    }

    fun showPane(pane: PanelPane) = _state.update { it.copy(pane = pane) }

    /** System Back: the reader or the tree back to the stream, an open item back to its topic, else close. */
    fun back() {
        val s = _state.value
        when {
            s.pane != PanelPane.STREAM -> _state.update { it.copy(pane = PanelPane.STREAM, feedItemId = if (s.pane == PanelPane.READER) null else it.feedItemId) }
            s.mode != PanelMode.FEEDS && s.scope.itemId != null -> select(s.scope.copy(itemId = null))
            else -> close()
        }
    }

    // ── Scope (LinksContent handleSelect…) ────────────────────────────────────

    fun select(scope: PanelScope) {
        _state.update { it.copy(scope = scope.copy(query = it.scope.query), landing = false, pane = PanelPane.STREAM) }
        persist()
    }

    fun selectDomain(domainId: String) = select(PanelScope(domainId = domainId, tag = _state.value.scope.tag))

    fun selectTopic(domainId: String, topicId: String) = select(PanelScope(domainId, topicId, tag = _state.value.scope.tag))

    fun selectItem(domainId: String, topicId: String, itemId: String) = select(PanelScope(domainId, topicId, itemId, tag = _state.value.scope.tag))

    fun selectTag(tag: String?) {
        _state.update { it.copy(scope = it.scope.copy(tag = tag), landing = false) }
        persist()
    }

    fun showLanding() {
        _state.update { it.copy(landing = true, pane = PanelPane.STREAM) }
        persist()
    }

    fun clearFilters() {
        _state.update { it.copy(scope = PanelScope(), landing = false) }
        persist()
    }

    fun setQuery(query: String) = _state.update { it.copy(scope = it.scope.copy(query = query)) }

    fun setSort(sort: PanelSort) {
        _state.update { it.copy(sort = sort) }
        persist()
    }

    /** Off hides every link under that heading level; all off shows them again (LinksStream toggleHeadingLevel) */
    fun toggleHeadingLevel(level: Int?) {
        _state.update {
            val levels = when {
                level == null -> if (it.hiddenHeadingLevels.containsAll((1..6).toList())) emptySet() else (1..6).toSet()
                level in it.hiddenHeadingLevels -> it.hiddenHeadingLevels - level
                else -> it.hiddenHeadingLevels + level
            }
            it.copy(hiddenHeadingLevels = levels)
        }
        persist()
    }

    /** A selection that no longer exists is cleared; an item that moved is followed to its new topic. */
    private fun dropStaleSelection(ui: PanelUi) {
        if (ui.domains.isEmpty()) return
        val scope = _state.value.scope
        val itemId = scope.itemId
        val fixed = when {
            itemId != null && ui.item(itemId) == null -> scope.copy(itemId = null)
            itemId != null -> ui.locate(itemId)?.let { (d, t) -> scope.copy(domainId = d, topicId = t) } ?: scope
            scope.topicId != null && ui.topic(scope.topicId) == null -> scope.copy(topicId = null)
            scope.domainId != null && ui.domain(scope.domainId) == null -> PanelScope(tag = scope.tag, query = scope.query)
            // First time: the first domain with links (LinksContent's fallback), once
            !prefs.getBoolean(KEY_AUTO_SELECTED, false) -> {
                prefs.edit { putBoolean(KEY_AUTO_SELECTED, true) }
                if (scope.domainId != null) scope
                else ui.domains.firstOrNull { it.linkCount > 0 }?.let { scope.copy(domainId = it.id) } ?: scope
            }
            else -> scope
        }
        if (fixed != scope) {
            _state.update { it.copy(scope = fixed) }
            persist()
        }
    }

    // ── Edits ──────────────────────────────────────────────────────────────────

    private val cache = graph.cache.state.stateIn(viewModelScope, SharingStarted.Eagerly, CachedState())

    /** Whether the account is end-to-end encrypted, as the cached snapshot says */
    private val encrypted: Boolean get() = cache.value.snapshot?.settings?.encryptionEnabled == true

    suspend fun loadItemText(itemId: String): ItemText = graph.items.loadText(itemId)

    /** The inline editor's autosave: false when it didn't save (locked, network), with [message] saying why. */
    suspend fun saveItem(itemId: String, name: String?, html: String?): Boolean = attempt {
        graph.items.save(itemId, name, html, encrypted).also { if (!it) fail(R.string.error_item_locked) }
    } ?: false

    /** The editor's pending edits when it is left: saved even though the editor is gone. */
    fun saveItemDetached(itemId: String, name: String?, html: String?) {
        if (name == null && html == null) return
        viewModelScope.launch { saveItem(itemId, name, html) }
    }

    fun saveReminder(itemId: String, date: String?, rule: String?, leadDays: Int) = launch {
        graph.items.saveReminder(itemId, date, rule, leadDays)
    }

    fun renameTopic(topicId: String, name: String) = launch {
        if (!graph.items.renameTopic(topicId, name, encrypted)) fail(R.string.error_item_locked)
    }

    fun saveInfoBox(domainId: String, text: String) = launch {
        if (!graph.items.saveInfoBox(domainId, text, encrypted)) fail(R.string.error_item_locked)
    }

    fun moveItem(itemId: String, topicId: String, up: Boolean) = launch { graph.items.move(itemId, topicId, up) }

    fun deleteItem(itemId: String) = launch { graph.items.delete(itemId) }

    /** EditLinkDialog: rewrites the link in its item's whole stored text (not the cached, shortened copy). */
    fun updateLink(itemId: String, oldUrl: String, newUrl: String, label: String) = editHtml(itemId) {
        LinkExtractor.updateLinkInHtml(it, oldUrl, newUrl, label)
    }

    fun removeLink(itemId: String, url: String) = editHtml(itemId) { LinkExtractor.removeLinkFromHtml(it, url) }

    private fun editHtml(itemId: String, change: (String) -> String) = launch {
        when (val text = graph.items.loadText(itemId)) {
            is ItemText.Loaded -> {
                val next = change(text.html)
                if (next != text.html && !graph.items.save(itemId, null, next, encrypted)) fail(R.string.error_item_locked)
            }
            ItemText.Locked -> fail(R.string.error_item_locked)
            else -> fail(R.string.item_text_failed)
        }
    }

    private fun fetchTitles(ui: PanelUi) {
        val wanted = SidePanelPresenter.untitledUrls(ui).filter { it !in askedTitles }.take(LinksApi.MAX_TITLE_BATCH)
        if (wanted.isEmpty() || !_state.value.open) return
        askedTitles += wanted
        viewModelScope.launch {
            runCatching { graph.api.links.titles(wanted) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { found ->
                titles.update { it + found }
            }
        }
    }

    // ── Feeds ──────────────────────────────────────────────────────────────────

    private fun loadFeeds() = launch { graph.feeds.reload() }

    fun reloadFeeds() = loadFeeds()

    fun setFeedFilter(filter: FeedFilter) {
        _state.update { it.copy(pane = PanelPane.STREAM, feedItemId = null) }
        launch { graph.feeds.setFilter(filter) }
    }

    fun loadMoreFeedItems() = launch { graph.feeds.loadItems(append = true) }

    /** Opens an item in the reader and marks it read, as selecting it on the web does */
    fun openFeedItem(itemId: String) {
        _state.update { it.copy(feedItemId = itemId, pane = PanelPane.READER) }
        launch { graph.feeds.setRead(itemId, true) }
    }

    fun setFeedItemRead(itemId: String, read: Boolean) = launch { graph.feeds.setRead(itemId, read) }

    fun setFeedItemStarred(itemId: String, starred: Boolean) = launch { graph.feeds.setStarred(itemId, starred) }

    fun markAllFeedItemsRead(olderThanDays: Int?) = launch { graph.feeds.markAllRead(olderThanDays) }

    fun refreshFeeds(feedId: String? = null) = launch { graph.feeds.refresh(feedId) }

    /** Subscribes; [onDone] gets null on success, else the reason to show in the dialog. */
    fun subscribe(url: String, title: String?, folderId: String?, onDone: (String?) -> Unit) = viewModelScope.launch {
        onDone(feedCall { graph.feeds.subscribe(url, title, folderId) })
    }

    fun updateFeed(feed: Feed, title: String, folderId: String?, interval: Int, active: Boolean, onDone: (String?) -> Unit) = viewModelScope.launch {
        onDone(feedCall { graph.feeds.updateFeed(feed, title, folderId, interval, active) })
    }

    fun deleteFeed(feedId: String) = launch { graph.feeds.deleteFeed(feedId) }

    fun createFolder(name: String, parentId: String?, onDone: (String?) -> Unit) = viewModelScope.launch {
        onDone(feedCall { graph.feeds.createFolder(name, parentId) })
    }

    fun deleteFolder(folderId: String) = launch { graph.feeds.deleteFolder(folderId) }

    suspend fun exportOpml(): String? = attempt { graph.feeds.exportOpml() }

    fun importOpml(xml: String) = launch {
        val (imported, total) = graph.feeds.importOpml(xml)
        _message.value = getApplication<Application>().getString(R.string.panel_opml_imported, imported, total)
    }

    private val thumbnails = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** A feed item's thumbnail, downsampled to about [maxPx] wide; null when it can't be loaded. Never throws. */
    suspend fun feedImage(url: String, maxPx: Int = 720): ImageBitmap? {
        thumbnails.get(url)?.let { return it }
        return try {
            val bytes = graph.feeds.imageBytes(url) ?: return null
            withContext(Dispatchers.Default) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxPx) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }?.also { thumbnails.put(url, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** null when [block] worked, else the message for the dialog */
    private suspend fun feedCall(block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: FeedRequestException) {
        e.message
    } catch (e: IOException) {
        getApplication<Application>().getString(R.string.error_network)
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    // ── Plumbing ─────────────────────────────────────────────────────────────

    private fun fail(res: Int) { _message.value = getApplication<Application>().getString(res) }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: FeedRequestException) {
        _message.value = e.message
        null
    } catch (_: IOException) {
        fail(R.string.error_network)
        null
    } catch (e: Exception) {
        _message.value = e.message ?: e.javaClass.simpleName
        null
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { attempt(block) }

    private fun persist() {
        val s = _state.value
        prefs.edit {
            putString(KEY_MODE, s.mode.name)
            putString(KEY_DOMAIN, s.scope.domainId)
            putString(KEY_TOPIC, s.scope.topicId)
            putString(KEY_ITEM, s.scope.itemId)
            putString(KEY_TAG, s.scope.tag)
            putBoolean(KEY_LANDING, s.landing)
            putString(KEY_SORT, s.sort.name)
            putString(KEY_HEADINGS, s.hiddenHeadingLevels.joinToString(","))
        }
    }

    private fun restore(): SidePanelState = runCatching {
        fun <T : Enum<T>> enum(key: String, values: Array<T>, default: T) = prefs.getString(key, null)?.let { v -> values.firstOrNull { it.name == v } } ?: default
        SidePanelState(
            mode = enum(KEY_MODE, PanelMode.entries.toTypedArray(), PanelMode.LINKS),
            scope = PanelScope(prefs.getString(KEY_DOMAIN, null), prefs.getString(KEY_TOPIC, null), prefs.getString(KEY_ITEM, null), prefs.getString(KEY_TAG, null)),
            landing = prefs.getBoolean(KEY_LANDING, false),
            sort = enum(KEY_SORT, PanelSort.entries.toTypedArray(), PanelSort.STRUCTURE),
            hiddenHeadingLevels = prefs.getString(KEY_HEADINGS, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..6 }.toSet(),
        )
    }.getOrDefault(SidePanelState())

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_DOMAIN = "domain"
        const val KEY_TOPIC = "topic"
        const val KEY_ITEM = "item"
        const val KEY_TAG = "tag"
        const val KEY_LANDING = "landing"
        const val KEY_SORT = "sort"
        const val KEY_HEADINGS = "hidden_headings"
        const val KEY_AUTO_SELECTED = "auto_selected"
    }
}
