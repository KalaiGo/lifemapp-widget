package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.TasksApi
import com.kalai73.lifemapp.widgets.core.data.api.ServerException
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.content.ItemHtml
import com.kalai73.lifemapp.widgets.core.model.CachedState
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TaskRepository(
    private val api: TasksApi,
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
