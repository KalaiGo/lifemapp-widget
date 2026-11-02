package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.api.ServerException
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.presentation.ImageContent
import com.kalai73.lifemapp.widgets.core.data.presentation.ItemHtml
import com.kalai73.lifemapp.widgets.core.data.secure.DeviceLockedException
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.Sections
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.TimeZone

sealed interface SyncResult {
    data object Success : SyncResult
    /** Network or server trouble; worth retrying */
    data class Retry(val error: SyncError) : SyncResult
    /** Signed out or session revoked; retrying won't help */
    data object SignedOut : SyncResult
}

class SnapshotRepository(
    private val api: LifeMappApi,
    private val cache: CacheStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timezone: () -> String = { TimeZone.getDefault().id },
    /** The Today screen's scoring section state, read alongside the snapshot (not part of the web's payload) */
    private val scoring: suspend () -> Scoring? = { null },
) {
    suspend fun markSyncing() {
        val now = clock()
        cache.update { it.copy(syncing = true, syncStartedAt = now) }
    }

    /** Clears the spinner, even from a cancelled coroutine (a replaced or stopped worker). */
    suspend fun clearSyncing() = withContext(NonCancellable) {
        cache.update { it.copy(syncing = false) }
    }

    suspend fun refresh(daysAhead: Int = 7): SyncResult {
        val startedAt = clock()
        cache.update { it.copy(syncing = true, syncStartedAt = startedAt, lastAttemptAt = startedAt) }
        return try {
            val fresh = api.snapshot(timezone(), daysAhead)
            // A failed read keeps the cached copy, like a failed snapshot section
            val freshScoring = try {
                scoring()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure(e)
                null
            }
            cache.update { old ->
                old.copy(
                    snapshot = merge(old.snapshot, fresh),
                    // The hidden domains are this device's own choice: a sync never replaces them
                    scoring = freshScoring?.copy(hiddenDomainIds = old.scoring?.hiddenDomainIds) ?: old.scoring,
                    lastSuccessAt = clock(),
                    lastError = null,
                    syncing = false,
                    // A completion the server has applied no longer lists the task; drop stale entries too
                    pendingCompletions = old.pendingCompletions.filter { (id, at) ->
                        fresh.tasks.any { it.id == id } && clock() - at < PENDING_TTL_MS
                    },
                )
            }
            SyncResult.Success
        } catch (e: CancellationException) {
            clearSyncing()
            throw e
        } catch (e: NotSignedInException) {
            cache.update { it.copy(syncing = false, lastError = SyncError.UNAUTHORIZED) }
            SyncResult.SignedOut
        } catch (e: DeviceLockedException) {
            // Can't even read our secrets before the first unlock; nothing else to record
            clearSyncing()
            SyncResult.Retry(SyncError.LOCKED_DEVICE)
        } catch (e: ServerException) {
            logFailure(e)
            cache.update { it.copy(syncing = false, lastError = SyncError.SERVER) }
            SyncResult.Retry(SyncError.SERVER)
        } catch (e: IOException) {
            logFailure(e)
            cache.update { it.copy(syncing = false, lastError = SyncError.NETWORK) }
            SyncResult.Retry(SyncError.NETWORK)
        } catch (e: Exception) {
            // Unparseable payload and the like
            logFailure(e)
            cache.update { it.copy(syncing = false, lastError = SyncError.SERVER) }
            SyncResult.Retry(SyncError.SERVER)
        }
    }

    private fun logFailure(e: Exception) {
        // Messages only: the payload may hold personal data
        runCatching { android.util.Log.w("LifeMappSync", "Snapshot sync failed: ${e::class.simpleName}: ${e.message}") }
    }

    companion object {
        /** A checked task stays hidden this long even if the server still lists it (slow providers, retries) */
        const val PENDING_TTL_MS = 10 * 60 * 1000L

        /** Sections the server failed to load keep their previous values. */
        fun merge(old: Snapshot?, fresh: Snapshot): Snapshot {
            if (old == null || fresh.failedSections.isEmpty()) return fresh
            var merged = fresh
            val failed = fresh.failedSections.toSet()
            if (Sections.EVENTS in failed) {
                merged = merged.copy(events = old.events, counts = merged.counts.copy(events = old.counts.events))
            }
            if (Sections.TASKS in failed) {
                merged = merged.copy(tasks = old.tasks, counts = merged.counts.copy(tasks = old.counts.tasks))
            }
            if (Sections.MAIL in failed) {
                merged = merged.copy(
                    mail = old.mail,
                    counts = merged.counts.copy(mail = old.counts.mail, mailConnected = old.counts.mailConnected),
                )
            }
            if (Sections.DOMAINS in failed) merged = merged.copy(domains = old.domains)
            if (Sections.WEATHER in failed) merged = merged.copy(weather = old.weather)
            if (Sections.INTEGRITY in failed) merged = merged.copy(integrity = old.integrity)
            if (Sections.TODAY_DOMAINS in failed) merged = merged.copy(todayDomains = old.todayDomains)
            return merged
        }
    }
}

class TaskRepository(
    private val api: LifeMappApi,
    private val cache: CacheStore,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The signed-in user, for reading their Todoist API key */
    private val userId: suspend () -> String? = { null },
    /** Decrypts the Todoist API key of an account with encryption on */
    private val dataKey: suspend () -> String? = { null },
) {
    /** Hides the task right away; [complete] then confirms it with the server. */
    suspend fun markCompletedLocally(taskId: String): CachedState =
        cache.update { it.copy(pendingCompletions = it.pendingCompletions + (taskId to clock()), completionFailed = false) }

    /**
     * LIFE MAPP tasks through the web API (which creates the next instance of a recurring task); Todoist tasks
     * ("todoist:<id>" in the snapshot) at Todoist with the user's own API key, as the web does.
     * @return true when it was accepted (or the task no longer exists there); false also without a usable Todoist key
     */
    suspend fun complete(taskId: String): Boolean = try {
        if (taskId.startsWith(TODOIST_PREFIX)) {
            val key = todoistKey() ?: return false
            api.closeTodoistTask(key, taskId.removePrefix(TODOIST_PREFIX))
        } else {
            api.completeTask(taskId)
        }
        confirm(taskId)
        true
    } catch (e: ServerException) {
        if (e.status == 404) {
            confirm(taskId)
            true
        } else {
            false
        }
    }

    private suspend fun todoistKey(): String? {
        val user = userId() ?: return null
        val stored = api.todoistApiKey(user)
        stored.plain?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val key = dataKey() ?: return null
        return EncryptedField.decrypt(stored.encrypted, key)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun confirm(taskId: String) {
        cache.update { state ->
            val snapshot = state.snapshot
            state.copy(
                pendingCompletions = state.pendingCompletions - taskId,
                snapshot = snapshot?.copy(
                    tasks = snapshot.tasks.filterNot { it.id == taskId },
                    counts = snapshot.counts.copy(tasks = (snapshot.counts.tasks - 1).coerceAtLeast(0)),
                ),
            )
        }
    }

    /**
     * A LIFE MAPP task's whole title and description to edit, fresh from the server (the snapshot's description is
     * cut and may be older than an edit on the web).
     */
    suspend fun loadText(taskId: String): TaskText {
        val stored = api.taskContent(taskId) ?: return TaskText.Gone
        if (stored.encryptedTitle == null && stored.encryptedDescription == null) {
            return TaskText.Loaded(stored.title.orEmpty(), stored.description.orEmpty())
        }
        val key = dataKey() ?: return TaskText.Locked
        val title = stored.encryptedTitle?.let { EncryptedField.decrypt(it, key) ?: return TaskText.Unreadable } ?: stored.title.orEmpty()
        val description = stored.encryptedDescription?.let { EncryptedField.decrypt(it, key) ?: return TaskText.Unreadable }
            ?: stored.description.orEmpty()
        return TaskText.Loaded(title, description)
    }

    /**
     * Saves a new [title] and/or [html] description (null keeps that field) as the web's task modal does: with
     * encryption on both are encrypted with the data key, the plain columns holding the web's placeholder title
     * and no description; otherwise a stale encrypted copy is cleared, since it would hide the new text.
     *
     * @return false when the account is encrypted and this device isn't unlocked
     */
    suspend fun saveText(taskId: String, title: String?, html: String?, encrypted: Boolean): Boolean {
        val key = if (encrypted) dataKey() ?: return false else null
        val newTitle = title?.trim()
        val blank = ItemHtml.isBlank(html)
        val description = if (html == null || blank) "" else html.trim()
        val encryptedTitle = if (key != null && newTitle != null) EncryptedField.encrypt(newTitle, key) else null
        val encryptedDescription = if (key != null && html != null) EncryptedField.encrypt(description, key) else null
        val fields = buildJsonObject {
            if (newTitle != null) {
                put("title", if (key != null) ENCRYPTED_TITLE else newTitle)
                put("encrypted_title", encryptedTitle?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            if (html != null) {
                put("description", if (key != null) "" else description)
                put("encrypted_description", encryptedDescription?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }
        if (fields.isEmpty()) return true
        api.updateTask(taskId, fields)
        cache.update { state ->
            val snapshot = state.snapshot ?: return@update state
            state.copy(
                snapshot = snapshot.copy(
                    tasks = snapshot.tasks.map { task ->
                        if (task.id != taskId) return@map task
                        var updated = task
                        if (newTitle != null) {
                            updated = if (key != null) updated.copy(title = null, encryptedTitle = encryptedTitle)
                            else updated.copy(title = newTitle, encryptedTitle = null)
                        }
                        if (html != null) {
                            updated = updated.copy(
                                // As the snapshot sends it: null when blank or encrypted, cut at its cap
                                description = if (key != null || blank) null else description.take(MAX_CACHED_DESCRIPTION),
                                encryptedDescription = if (blank) null else encryptedDescription,
                            )
                        }
                        updated
                    },
                ),
            )
        }
        return true
    }

    /** Puts the task back after the completion failed for good. */
    suspend fun revert(taskId: String) {
        cache.update { it.copy(pendingCompletions = it.pendingCompletions - taskId, completionFailed = true) }
    }

    suspend fun dismissFailure() {
        cache.update { it.copy(completionFailed = false) }
    }

    companion object {
        /** The snapshot's id prefix for Todoist tasks */
        const val TODOIST_PREFIX = "todoist:"

        /** Tasks the app can check off: LIFE MAPP's own, and Todoist's with the user's API key */
        fun canComplete(source: String): Boolean = source == "local" || source == "todoist"

        /** Tasks whose title and description the app can edit: LIFE MAPP's own */
        fun canEdit(source: String): Boolean = source == "local"

        /** What the web stores in the plain title column of an encrypted task */
        private const val ENCRYPTED_TITLE = "🔒 Encrypted Task"
        /** The snapshot's cap on a task's description (the web's WIDGET_MAX_ITEM_CONTENT) */
        private const val MAX_CACHED_DESCRIPTION = 4000
    }
}

/** A task's title and description as the editor gets them from [TaskRepository.loadText]. */
sealed interface TaskText {
    /** [description] as HTML, empty when the task has none */
    data class Loaded(val title: String, val description: String) : TaskText
    /** Encrypted, and this device isn't unlocked */
    data object Locked : TaskText
    /** Encrypted, but this device's key doesn't open it */
    data object Unreadable : TaskText
    /** Completed or removed on another device */
    data object Gone : TaskText
}

/**
 * Edits and removes items (subsubdomains) of the Today domain cards (Integrity and positions 2–5) from the app,
 * updating the cached snapshot right away.
 */
class ItemRepository(
    private val api: LifeMappApi,
    private val cache: CacheStore,
    private val dataKey: suspend () -> String?,
    private val userId: suspend () -> String? = { null },
) {
    /**
     * The item's whole text to edit, fresh from the server (the snapshot's copy is cut at
     * [MAX_CACHED_CONTENT] characters and may be older than an edit on the web).
     */
    suspend fun loadText(itemId: String): ItemText {
        val stored = api.itemContent(itemId) ?: return ItemText.Gone
        if (stored.isImage) return ItemText.Image
        val html = stored.encryptedContent?.let { encrypted ->
            val key = dataKey() ?: return ItemText.Locked
            EncryptedField.decrypt(encrypted, key) ?: return ItemText.Unreadable
        } ?: stored.content.orEmpty()
        return ItemText.Loaded(html, createdAt = stored.createdAt, maxChars = charLimit())
    }

    /**
     * Where an image item's picture is, from its stored content (`[IMG:<uri>]`, decrypted when encrypted). The
     * snapshot leaves image content out, so this reads the row.
     */
    suspend fun imageUri(itemId: String): ItemImageRef {
        val stored = api.itemContent(itemId) ?: return ItemImageRef.Missing
        val content = stored.encryptedContent?.let { encrypted ->
            val key = dataKey() ?: return ItemImageRef.Locked
            EncryptedField.decrypt(encrypted, key) ?: return ItemImageRef.Missing
        } ?: stored.content
        return ImageContent.uri(content)?.let { ItemImageRef.Found(it) } ?: ItemImageRef.Missing
    }

    /**
     * The picture's bytes: an http(s) link is downloaded, a base64 `data:` URI decoded. Null for URIs this device
     * can't reach (another device's file:// or content:// pictures). Throws on network errors.
     */
    suspend fun imageBytes(uri: String): ByteArray? = when {
        uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> api.downloadImage(uri)
        uri.startsWith("data:", ignoreCase = true) && uri.substringBefore(',').endsWith(";base64", ignoreCase = true) ->
            runCatching { java.util.Base64.getMimeDecoder().decode(uri.substringAfter(',')) }.getOrNull()
        else -> null
    }

    /**
     * Moves an item one place up or down in its topic, as the web menu's Move up / Move down: the topic's whole
     * order comes from the server (the snapshot lists only the first items), then every position is rewritten.
     *
     * @return false when the item is already first (or last)
     */
    suspend fun move(itemId: String, topicId: String, up: Boolean): Boolean {
        val ids = api.topicItemIds(topicId).toMutableList()
        val from = ids.indexOf(itemId)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in ids.indices) return false
        ids.add(to, ids.removeAt(from))
        api.reorderItems(ids)
        val order = ids.withIndex().associate { (i, id) -> id to i }
        mapTopic(topicId) { topic -> topic.copy(items = topic.items.sortedBy { order[it.id] ?: Int.MAX_VALUE }) }
        return true
    }

    /**
     * Flags or unflags an item with the warning icon (status "warning", as the web menu's Add Warning Icon), keeping
     * the Integrity card's warnings count in step.
     */
    suspend fun setWarning(itemId: String, warning: Boolean) {
        api.updateItem(itemId, buildJsonObject { put("status", if (warning) JsonPrimitive("warning") else JsonNull) })
        var changed = false
        mapItem(itemId) { if (it.warning != warning) { changed = true; it.copy(warning = warning) } else it }
        if (changed) {
            cache.update { state ->
                val snapshot = state.snapshot ?: return@update state
                val integrity = snapshot.integrity ?: return@update state
                state.copy(snapshot = snapshot.copy(integrity = integrity.copy(warnings = (integrity.warnings + if (warning) 1 else -1).coerceAtLeast(0))))
            }
        }
    }

    private var cachedCharLimit: Int? = null

    /** The plan's cap on an item's text, null when it can't be read (the editor then shows no counter) */
    private suspend fun charLimit(): Int? {
        cachedCharLimit?.let { return it }
        val user = userId() ?: return null
        return try {
            api.itemCharLimit(user).also { cachedCharLimit = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves a new [name] and/or a new [html] text (null keeps that field), as the web's subsubdomain store does:
     * with encryption on both are encrypted with the data key, the plaintext columns holding the sentinel name and
     * no content; otherwise a stale encrypted_content is cleared, since it would hide the new text.
     *
     * @return false when the account is encrypted and this device isn't unlocked
     */
    suspend fun save(itemId: String, name: String?, html: String?, encrypted: Boolean): Boolean {
        val key = if (encrypted) dataKey() ?: return false else null
        val newName = name?.trim()
        val encryptedName = if (key != null && newName != null) EncryptedField.encrypt(newName, key) else null
        val blank = ItemHtml.isBlank(html)
        val encryptedContent = if (key != null && html != null && !blank) EncryptedField.encrypt(html, key) else null
        val fields = buildJsonObject {
            if (newName != null) {
                put("name", if (key != null) ENCRYPTED_NAME else newName)
                if (encryptedName != null) put("encrypted_name", encryptedName)
            }
            if (html != null) {
                when {
                    key != null -> put("content", JsonNull)
                    else -> put("content", if (blank) "" else html)
                }
                put("encrypted_content", encryptedContent?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }
        if (fields.isEmpty()) return true
        api.updateItem(itemId, fields)
        mapItem(itemId) { item ->
            var updated = item
            if (newName != null) {
                updated = if (key != null) updated.copy(name = null, encryptedName = encryptedName)
                else updated.copy(name = newName, encryptedName = null)
            }
            if (html != null) {
                updated = updated.copy(
                    content = if (key != null || blank) null else html.take(MAX_CACHED_CONTENT),
                    encryptedContent = encryptedContent,
                )
            }
            updated
        }
        return true
    }

    suspend fun delete(itemId: String) {
        try {
            api.deleteItem(itemId)
        } catch (e: ServerException) {
            if (e.status != 404) throw e
        }
        mapItems { items -> items.filterNot { it.id == itemId } }
    }

    /**
     * Collapses or expands a topic: the cached snapshot first, so the widget redraws at once, then the server.
     * Puts the old state back when the server refuses it.
     */
    suspend fun setTopicCollapsed(topicId: String, collapsed: Boolean, onLocalChange: suspend () -> Unit = {}) {
        mapTopic(topicId) { it.copy(collapsed = collapsed) }
        onLocalChange()
        try {
            api.setTopicCollapsed(topicId, collapsed)
        } catch (e: Exception) {
            mapTopic(topicId) { it.copy(collapsed = !collapsed) }
            throw e
        }
    }

    /** Collapses or expands an item, like [setTopicCollapsed]. */
    suspend fun setItemCollapsed(itemId: String, collapsed: Boolean, onLocalChange: suspend () -> Unit = {}) {
        mapItem(itemId) { it.copy(collapsed = collapsed) }
        onLocalChange()
        try {
            api.setItemCollapsed(itemId, collapsed)
        } catch (e: Exception) {
            mapItem(itemId) { it.copy(collapsed = !collapsed) }
            throw e
        }
    }

    private suspend fun mapItem(itemId: String, transform: (TopicItem) -> TopicItem) =
        mapItems { items -> items.map { if (it.id == itemId) transform(it) else it } }

    private suspend fun mapTopic(topicId: String, transform: (Topic) -> Topic) {
        mapCards { card -> card.copy(topics = card.topics.map { if (it.id == topicId) transform(it) else it }) }
    }

    private suspend fun mapItems(transform: (List<TopicItem>) -> List<TopicItem>) {
        mapCards { card ->
            card.copy(
                topics = card.topics.map { topic ->
                    val items = transform(topic.items)
                    topic.copy(items = items, count = (topic.count - (topic.items.size - items.size)).coerceAtLeast(items.size))
                },
            )
        }
    }

    /** Applies [transform] to every domain card in the cached snapshot: Integrity and the other Today domains. */
    private suspend fun mapCards(transform: (Integrity) -> Integrity) {
        cache.update { state ->
            val snapshot = state.snapshot ?: return@update state
            state.copy(
                snapshot = snapshot.copy(
                    integrity = snapshot.integrity?.let(transform),
                    todayDomains = snapshot.todayDomains.map(transform),
                ),
            )
        }
    }

    private companion object {
        /** What the web stores in the plaintext column of an encrypted item */
        const val ENCRYPTED_NAME = "[encrypted]"
        /** The snapshot's cap on an item's text (the web's WIDGET_MAX_ITEM_CONTENT) */
        const val MAX_CACHED_CONTENT = 4000
    }
}

/** An item's text as the editor gets it from [ItemRepository.loadText]. */
sealed interface ItemText {
    /**
     * The text as HTML, empty when the item has none; [createdAt] as an ISO timestamp; [maxChars] the plan's cap
     * on the plain text, null when unknown
     */
    data class Loaded(val html: String, val createdAt: String? = null, val maxChars: Int? = null) : ItemText
    /** An image item: its content is the picture, edited on the web */
    data object Image : ItemText
    /** Encrypted, and this device isn't unlocked */
    data object Locked : ItemText
    /** Encrypted, but this device's key doesn't open it */
    data object Unreadable : ItemText
    /** Removed on another device */
    data object Gone : ItemText
}

/** Where an image item's picture is ([ItemRepository.imageUri]). */
sealed interface ItemImageRef {
    data class Found(val uri: String) : ItemImageRef
    /** Encrypted, and this device isn't unlocked */
    data object Locked : ItemImageRef
    /** Removed, no picture in its content, or not readable with this device's key */
    data object Missing : ItemImageRef
}
