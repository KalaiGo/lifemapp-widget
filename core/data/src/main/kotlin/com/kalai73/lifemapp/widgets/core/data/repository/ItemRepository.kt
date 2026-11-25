package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.ItemsApi
import com.kalai73.lifemapp.widgets.core.data.api.ServerException
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.content.ImageContent
import com.kalai73.lifemapp.widgets.core.data.content.ItemHtml
import com.kalai73.lifemapp.widgets.core.model.CustomTag
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.PlanLimits
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Edits and removes items (subsubdomains) of the Today domain cards (Integrity and positions 2–5) from the app,
 * updating the cached snapshot right away.
 */
class ItemRepository(
    private val api: ItemsApi,
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

    /** The signed-in user's admin flag, read once per user */
    private var cachedAdmin: Pair<String, Boolean>? = null

    /** Whether the signed-in user is an admin: false when signed out, null when it can't be read */
    suspend fun isAdmin(): Boolean? {
        val user = userId() ?: return false
        cachedAdmin?.takeIf { it.first == user }?.let { return it.second }
        return try {
            api.isAdmin(user).also { cachedAdmin = user to it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
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

    /**
     * Sets or clears an item's reminder (the side panel's ReminderPicker). As the web's subsubdomain store does, a new
     * date or repeat rule re-arms the reminder (reminder_task_created_at cleared) so its task is created again.
     */
    suspend fun saveReminder(itemId: String, date: String?, rule: String?, leadDays: Int) {
        val current = findItem(itemId)
        val rearm = current == null || current.reminderDate != date || current.reminderRule != rule
        api.updateItem(itemId, buildJsonObject {
            put("reminder_date", date?.let(::JsonPrimitive) ?: JsonNull)
            put("reminder_recurrence_rule", rule?.let(::JsonPrimitive) ?: JsonNull)
            put("reminder_lead_days", leadDays)
            if (rearm) put("reminder_task_created_at", JsonNull)
        })
        mapItem(itemId) {
            it.copy(
                reminderDate = date, reminderRule = rule, reminderLeadDays = leadDays,
                reminderTaskCreated = it.reminderTaskCreated && !rearm,
            )
        }
    }

    /** Replaces an item's own tags, those on top of its topic's (the web route also keeps the legacy `tag` column). */
    suspend fun saveTags(itemId: String, tags: List<String>) {
        api.updateItem(itemId, buildJsonObject { put("tags", JsonArray(tags.map(::JsonPrimitive))) })
        mapItem(itemId) { it.copy(tags = tags) }
    }

    /**
     * Renames a topic (subdomain), encrypted like an item name when the account is (subdomain-store.ts).
     * @return false when the account is encrypted and this device isn't unlocked
     */
    suspend fun renameTopic(topicId: String, name: String, encrypted: Boolean): Boolean {
        val newName = name.trim().takeIf { it.isNotEmpty() } ?: return true
        val key = if (encrypted) dataKey() ?: return false else null
        val encryptedName = key?.let { EncryptedField.encrypt(newName, it) }
        api.updateTopic(topicId, buildJsonObject {
            put("name", if (encryptedName != null) ENCRYPTED_NAME else newName)
            put("encrypted_name", encryptedName?.let(::JsonPrimitive) ?: JsonNull)
        })
        mapTopic(topicId) {
            if (encryptedName != null) it.copy(name = null, encryptedName = encryptedName) else it.copy(name = newName, encryptedName = null)
        }
        return true
    }

    /**
     * Changes a domain's info box, encrypted when the account is; blank clears it (domain-store.ts updateDomain).
     * @return false when the account is encrypted and this device isn't unlocked
     */
    suspend fun saveInfoBox(domainId: String, text: String, encrypted: Boolean): Boolean {
        val value = text.trim().takeIf { it.isNotEmpty() }
        val key = if (encrypted && value != null) dataKey() ?: return false else null
        val encryptedValue = if (key != null && value != null) EncryptedField.encrypt(value, key) else null
        api.updateDomain(domainId, buildJsonObject {
            put("info_box", if (encryptedValue != null) JsonPrimitive(ENCRYPTED_NAME) else value?.let(::JsonPrimitive) ?: JsonNull)
            put("encrypted_info_box", encryptedValue?.let(::JsonPrimitive) ?: JsonNull)
        })
        mapCards { card ->
            if (card.domainId != domainId) card
            else if (encryptedValue != null) card.copy(infoBox = null, encryptedInfoBox = encryptedValue)
            else card.copy(infoBox = value, encryptedInfoBox = null)
        }
        return true
    }

    private var cachedLimits: Pair<String, PlanLimits>? = null

    /** The plan's limits for the signed-in user (read once per user); the basic plan's when they can't be read. */
    suspend fun planLimits(): PlanLimits {
        val user = userId() ?: return PlanLimits.forTier("basic")
        cachedLimits?.takeIf { it.first == user }?.let { return it.second }
        return try {
            api.planLimits(user).also { cachedLimits = user to it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PlanLimits.forTier("basic")
        }
    }

    /** The user's own context tags; empty when they can't be read. */
    suspend fun customTags(): List<CustomTag> {
        val user = userId() ?: return emptyList()
        return try {
            api.customTags(user).also { fresh ->
                cache.update { it.copy(customTags = fresh) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cache.current().customTags
        }
    }

    /** Switches the local context tag filter of the Today and Monthly screens ("all" or a tag id). */
    suspend fun setActiveTag(tag: String) {
        cache.update { it.copy(activeTag = tag) }
    }

    /** Saves the user's custom tags list (preferences.custom_tags) with optimistic cache update and rollback. */
    suspend fun saveCustomTags(tags: List<CustomTag>) {
        val user = userId() ?: return
        val previous = cache.current().customTags
        cache.update { it.copy(customTags = tags) }
        try {
            api.saveCustomTags(user, tags)
        } catch (e: Exception) {
            cache.update { it.copy(customTags = previous) }
            throw e
        }
    }

    /**
     * Updates a topic's (subdomain's) context tags (`PUT /api/subdomains/{id}`), never empty (defaults to `["me"]`).
     * Optimistically updates the cache and rolls back on failure.
     */
    suspend fun saveTopicTags(topicId: String, tags: List<String>) {
        val normalized = tags.distinct().ifEmpty { listOf("me") }
        val previous = findTopic(topicId)?.tags
        mapTopic(topicId) { it.copy(tags = normalized) }
        try {
            api.updateTopic(topicId, buildJsonObject {
                put("tags", JsonArray(normalized.map(::JsonPrimitive)))
            })
        } catch (e: Exception) {
            if (previous != null) mapTopic(topicId) { it.copy(tags = previous) }
            throw e
        }
    }

    /**
     * Updates an item's (subsubdomain's) own context tags (`PUT /api/subsubdomains/{id}`), on top of its topic's.
     * Optimistically updates the cache and rolls back on failure.
     */
    suspend fun saveItemTags(itemId: String, ownTags: List<String>) {
        val normalized = ownTags.distinct()
        val previous = findItem(itemId)?.tags
        mapItem(itemId) { it.copy(tags = normalized) }
        try {
            api.updateItem(itemId, buildJsonObject {
                put("tags", JsonArray(normalized.map(::JsonPrimitive)))
            })
        } catch (e: Exception) {
            if (previous != null) mapItem(itemId) { it.copy(tags = previous) }
            throw e
        }
    }

    /**
     * Deletes a user-created tag from settings and strips it from every topic (falling back to `["me"]`) and item
     * carrying it, matching the web's `tag-manager-dialog.tsx deleteCustomTag`. Task labels stay as plain labels.
     */
    suspend fun deleteCustomTag(tagId: String) {
        val current = cache.current()
        val nextTags = current.customTags.filterNot { it.id == tagId }
        saveCustomTags(nextTags)
        if (current.activeTag == tagId) {
            cache.update { it.copy(activeTag = CustomTag.TAG_ALL) }
        }
        val snapshot = cache.current().snapshot ?: return
        val allCards = (listOfNotNull(snapshot.integrity) + snapshot.todayDomains + snapshot.monthlyDomains)
        val affectedTopics = allCards.flatMap { it.topics }.distinctBy { it.id }.filter { tagId in it.tags }
        val affectedItems = allCards.flatMap { it.topics }.flatMap { it.items }.distinctBy { it.id }.filter { tagId in it.tags }
        for (topic in affectedTopics) {
            val remaining = topic.tags.filterNot { it == tagId }.ifEmpty { listOf("me") }
            runCatching { saveTopicTags(topic.id, remaining) }
        }
        for (item in affectedItems) {
            val remaining = item.tags.filterNot { it == tagId }
            runCatching { saveItemTags(item.id, remaining) }
        }
    }

    private suspend fun findTopic(topicId: String): Topic? {
        val snapshot = cache.current().snapshot ?: return null
        return (listOfNotNull(snapshot.integrity) + snapshot.todayDomains + snapshot.monthlyDomains)
            .asSequence().flatMap { it.topics }.firstOrNull { it.id == topicId }
    }

    private suspend fun findItem(itemId: String): TopicItem? {
        val snapshot = cache.current().snapshot ?: return null
        return (listOfNotNull(snapshot.integrity) + snapshot.todayDomains + snapshot.monthlyDomains)
            .asSequence().flatMap { it.topics }.flatMap { it.items }.firstOrNull { it.id == itemId }
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

    /**
     * Applies [transform] to every domain card in the cached snapshot: Integrity, the other Today domains, and the
     * full domain list (monthlyDomains, which the Monthly screen and the side panel read).
     */
    private suspend fun mapCards(transform: (Integrity) -> Integrity) {
        cache.update { state ->
            val snapshot = state.snapshot ?: return@update state
            state.copy(
                snapshot = snapshot.copy(
                    integrity = snapshot.integrity?.let(transform),
                    todayDomains = snapshot.todayDomains.map(transform),
                    monthlyDomains = snapshot.monthlyDomains.map(transform),
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
