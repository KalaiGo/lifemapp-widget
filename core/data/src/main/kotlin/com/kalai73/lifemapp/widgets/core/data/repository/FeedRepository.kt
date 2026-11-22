package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.data.api.FeedsApi
import com.kalai73.lifemapp.widgets.core.model.Feed
import com.kalai73.lifemapp.widgets.core.model.FeedFilter
import com.kalai73.lifemapp.widgets.core.model.FeedFolder
import com.kalai73.lifemapp.widgets.core.model.FeedItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** What the Feeds tab shows; online only, like the web's feed store (nothing is cached on the device). */
data class FeedState(
    val folders: List<FeedFolder> = emptyList(),
    val feeds: List<Feed> = emptyList(),
    val items: List<FeedItem> = emptyList(),
    val filter: FeedFilter = FeedFilter(),
    val hasMore: Boolean = false,
    val loadingFeeds: Boolean = false,
    val loadingItems: Boolean = false,
    val loadingMore: Boolean = false,
    /** The feed being synced by a refresh, if any */
    val syncingFeedId: String? = null,
    /** Whether active non-social feeds have already been synced on this session load */
    val hasAutoSyncedOnLoad: Boolean = false,
    /** Feeds and items were loaded at least once */
    val loaded: Boolean = false,
    /** The last load failed (offline, server error) */
    val failed: Boolean = false,
)

/**
 * The web's feed store (stores/feed-store.ts) for the side panel's Feeds tab: folders, feeds and a page of items for
 * the current filter. A newer items request makes older answers stale ([latestRequest]); read and star change at
 * once and are put back when the server refuses.
 */
class FeedRepository(private val api: FeedsApi, private val zone: () -> ZoneId = ZoneId::systemDefault) {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val latestRequest = AtomicInteger()

    /** Folders and feeds with counts, today counted from the device's local midnight. */
    suspend fun loadFeeds(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loadingFeeds = true) }
        try {
            val midnight = LocalDate.now(zone()).atStartOfDay(zone()).toInstant().toString()
            val data = api.feeds(midnight)
            _state.update { it.copy(folders = data.folders, feeds = data.feeds, failed = false) }
        } catch (e: Exception) {
            if (!silent) _state.update { it.copy(failed = true) }
            throw e
        } finally {
            if (!silent) _state.update { it.copy(loadingFeeds = false) }
        }
    }

    /** The first page for the current filter, or ([append]) the next one. */
    suspend fun loadItems(append: Boolean = false, silent: Boolean = false) {
        val request = latestRequest.incrementAndGet()
        val current = _state.value
        if (!silent) _state.update { if (append) it.copy(loadingMore = true) else it.copy(loadingItems = true) }
        try {
            val page = api.items(current.filter, offset = if (append) current.items.size else 0)
            if (request != latestRequest.get()) return
            _state.update {
                it.copy(
                    items = if (append) it.items + page.items.filterNot { new -> it.items.any { old -> old.id == new.id } } else page.items,
                    hasMore = page.hasMore,
                    loaded = true,
                    failed = false,
                )
            }
        } catch (e: Exception) {
            if (!silent && request == latestRequest.get()) _state.update { it.copy(failed = true) }
            throw e
        } finally {
            if (!silent && request == latestRequest.get()) _state.update { it.copy(loadingItems = false, loadingMore = false) }
        }
    }

    /** Syncs all active non-social feeds (RSS, YouTube) once per session load (feed-store.ts syncNonSocialFeedsOnLoad). */
    suspend fun syncNonSocialFeedsOnLoad() {
        val s = _state.value
        if (s.hasAutoSyncedOnLoad || s.syncingFeedId != null) return
        _state.update { it.copy(hasAutoSyncedOnLoad = true) }
        // Social feeds (billed per scrape) only sync on a manual refresh
        val targets = s.feeds.filter { it.isActive && it.sourceType != "social" }
        if (targets.isEmpty()) return

        val anyInserted = AtomicBoolean(false)
        val nextIndex = AtomicInteger(0)
        val concurrency = minOf(4, targets.size)
        try {
            coroutineScope {
                List(concurrency) {
                    async {
                        while (true) {
                            val idx = nextIndex.getAndIncrement()
                            if (idx >= targets.size) break
                            val feed = targets[idx]
                            _state.update { it.copy(syncingFeedId = feed.id) }
                            runCatching { api.refresh(feed.id) }.getOrNull()?.let { inserted ->
                                if (inserted > 0) anyInserted.set(true)
                            }
                        }
                    }
                }.awaitAll()
            }
        } finally {
            _state.update { it.copy(syncingFeedId = null) }
        }

        runCatching { loadFeeds(silent = true) }
        if (anyInserted.get()) runCatching { loadItems(append = false, silent = true) }
    }

    suspend fun reload() {
        loadFeeds()
        loadItems()
    }

    /** Changes what the stream lists and loads its first page (feed-store.ts setFilter). */
    suspend fun setFilter(filter: FeedFilter) {
        _state.update { it.copy(filter = filter, items = emptyList(), hasMore = false) }
        loadItems()
    }

    suspend fun setRead(itemId: String, read: Boolean) {
        val item = _state.value.items.firstOrNull { it.id == itemId } ?: return
        if (item.isRead == read) return
        applyRead(item, read)
        try {
            api.setRead(itemId, read)
        } catch (e: Exception) {
            applyRead(item.copy(isRead = read), !read)
            throw e
        }
    }

    private fun applyRead(item: FeedItem, read: Boolean) = _state.update { s ->
        s.copy(
            items = s.items.map { if (it.id == item.id) it.copy(isRead = read) else it },
            feeds = s.feeds.map { if (it.id == item.feedId) it.copy(unreadCount = (it.unreadCount + if (read) -1 else 1).coerceAtLeast(0)) else it },
        )
    }

    suspend fun setStarred(itemId: String, starred: Boolean) {
        _state.update { s -> s.copy(items = s.items.map { if (it.id == itemId) it.copy(isStarred = starred) else it }) }
        try {
            api.setStarred(itemId, starred)
        } catch (e: Exception) {
            _state.update { s -> s.copy(items = s.items.map { if (it.id == itemId) it.copy(isStarred = !starred) else it }) }
            throw e
        }
    }

    /** Marks the current view read; with [olderThanDays], only items published before that many days ago. */
    suspend fun markAllRead(olderThanDays: Int? = null) {
        val before = olderThanDays?.let { Instant.now().minusSeconds(it * 86_400L).toString() }
        api.markAllRead(_state.value.filter, before)
        reload()
    }

    /** Syncs one feed, or every active feed of the current folder (subfolders included), then reloads. */
    suspend fun refresh(feedId: String? = null) {
        val s = _state.value
        val selectedFeed = s.filter.feedId
        val selectedFolder = s.filter.folderId
        val targets = when {
            feedId != null -> listOf(feedId)
            selectedFeed != null -> listOf(selectedFeed)
            selectedFolder != null -> {
                val ids = folderSubtree(s.folders, selectedFolder)
                s.feeds.filter { it.isActive && it.folderId in ids }.map { it.id }
            }
            else -> s.feeds.filter { it.isActive }.map { it.id }
        }
        try {
            for (id in targets) {
                _state.update { it.copy(syncingFeedId = id) }
                try {
                    api.refresh(id)
                } catch (e: Exception) {
                    // One feed: say why (plan, quota); several: carry on with the rest, as the web does
                    if (targets.size == 1) throw e
                }
            }
        } finally {
            _state.update { it.copy(syncingFeedId = null) }
        }
        reload()
    }

    suspend fun subscribe(url: String, title: String?, folderId: String?): Feed {
        val feed = api.subscribe(url, title, folderId)
        reload()
        return feed
    }

    suspend fun updateFeed(feed: Feed, title: String, folderId: String?, intervalMinutes: Int, active: Boolean) {
        val updated = api.updateFeed(
            feed.id, title = title, folderId = folderId, clearFolder = folderId == null,
            intervalMinutes = intervalMinutes, active = active,
        )
        _state.update { s ->
            s.copy(feeds = s.feeds.map { if (it.id == feed.id) updated.copy(unreadCount = it.unreadCount, todayCount = it.todayCount) else it })
        }
    }

    suspend fun deleteFeed(feedId: String) {
        api.deleteFeed(feedId)
        val wasSelected = _state.value.filter.feedId == feedId
        _state.update { s ->
            s.copy(feeds = s.feeds.filterNot { it.id == feedId }, items = s.items.filterNot { it.feedId == feedId })
        }
        if (wasSelected) setFilter(_state.value.filter.copy(feedId = null))
    }

    suspend fun createFolder(name: String, parentId: String?): FeedFolder {
        val folder = api.createFolder(name, parentId)
        _state.update { it.copy(folders = it.folders + folder) }
        return folder
    }

    /** Deletes a folder; its subfolders and feeds move up to its parent, as on the server. */
    suspend fun deleteFolder(folderId: String) {
        api.deleteFolder(folderId)
        val wasSelected = _state.value.filter.folderId == folderId
        _state.update { s ->
            val parent = s.folders.firstOrNull { it.id == folderId }?.parentId
            s.copy(
                folders = s.folders.filterNot { it.id == folderId }.map { if (it.parentId == folderId) it.copy(parentId = parent) else it },
                feeds = s.feeds.map { if (it.folderId == folderId) it.copy(folderId = parent) else it },
            )
        }
        if (wasSelected) setFilter(_state.value.filter.copy(folderId = null))
    }

    /** A feed item's picture (http/https only), null when it can't be downloaded */
    suspend fun imageBytes(url: String): ByteArray? =
        if (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) api.downloadImage(url) else null

    suspend fun exportOpml(): String = api.exportOpml()

    suspend fun importOpml(xml: String): Pair<Int, Int> = api.importOpml(xml).also { reload() }

    /** Forgets everything (sign-out). */
    fun clear() {
        latestRequest.incrementAndGet()
        _state.value = FeedState()
    }

    companion object {
        /** The folder and every folder below it (lib/feeds/folder-tree.ts getFolderSubtreeIds) */
        fun folderSubtree(folders: List<FeedFolder>, rootId: String): Set<String> {
            val ids = mutableSetOf(rootId)
            var grew = true
            while (grew) {
                grew = false
                for (f in folders) if (f.parentId in ids && ids.add(f.id)) grew = true
            }
            return ids
        }
    }
}
