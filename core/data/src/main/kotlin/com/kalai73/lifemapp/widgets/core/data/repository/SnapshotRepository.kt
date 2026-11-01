package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.api.ServerException
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.secure.DeviceLockedException
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.Sections
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
            cache.update { old ->
                old.copy(
                    snapshot = merge(old.snapshot, fresh),
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
            return merged
        }
    }
}

class TaskRepository(
    private val api: LifeMappApi,
    private val cache: CacheStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Hides the task right away; [complete] then confirms it with the server. */
    suspend fun markCompletedLocally(taskId: String): CachedState =
        cache.update { it.copy(pendingCompletions = it.pendingCompletions + (taskId to clock()), completionFailed = false) }

    /** @return true when the server accepted it (or the task no longer exists there) */
    suspend fun complete(taskId: String): Boolean = try {
        api.completeTask(taskId)
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

    /** Puts the task back after the completion failed for good. */
    suspend fun revert(taskId: String) {
        cache.update { it.copy(pendingCompletions = it.pendingCompletions - taskId, completionFailed = true) }
    }

    suspend fun dismissFailure() {
        cache.update { it.copy(completionFailed = false) }
    }
}

/** Edits and removes Integrity items (subsubdomains) from the app, updating the cached snapshot right away. */
class ItemRepository(
    private val api: LifeMappApi,
    private val cache: CacheStore,
    private val dataKey: suspend () -> String?,
) {
    /** @return false when the account is encrypted and this device isn't unlocked */
    suspend fun rename(itemId: String, text: String, encrypted: Boolean): Boolean {
        val name = text.trim()
        val encryptedName = if (encrypted) {
            val key = dataKey() ?: return false
            EncryptedField.encrypt(name, key)
        } else {
            null
        }
        api.renameItem(itemId, if (encrypted) ENCRYPTED_NAME else name, encryptedName)
        mapItems { items ->
            items.map {
                if (it.id != itemId) it
                else if (encrypted) it.copy(name = null, encryptedName = encryptedName)
                else it.copy(name = name, encryptedName = null)
            }
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

    private suspend fun mapTopic(topicId: String, transform: (Topic) -> Topic) {
        cache.update { state ->
            val snapshot = state.snapshot ?: return@update state
            val integrity = snapshot.integrity ?: return@update state
            state.copy(
                snapshot = snapshot.copy(
                    integrity = integrity.copy(topics = integrity.topics.map { if (it.id == topicId) transform(it) else it }),
                ),
            )
        }
    }

    private suspend fun mapItems(transform: (List<TopicItem>) -> List<TopicItem>) {
        cache.update { state ->
            val snapshot = state.snapshot ?: return@update state
            val integrity = snapshot.integrity ?: return@update state
            state.copy(
                snapshot = snapshot.copy(
                    integrity = integrity.copy(
                        topics = integrity.topics.map { topic ->
                            val items = transform(topic.items)
                            topic.copy(items = items, count = (topic.count - (topic.items.size - items.size)).coerceAtLeast(items.size))
                        },
                    ),
                ),
            )
        }
    }

    private companion object {
        /** What the web stores in the plaintext column of an encrypted item */
        const val ENCRYPTED_NAME = "[encrypted]"
    }
}
