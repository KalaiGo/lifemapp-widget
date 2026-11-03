package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.ItemsApi
import com.kalai73.lifemapp.widgets.core.data.api.ServerException
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.content.ImageContent
import com.kalai73.lifemapp.widgets.core.data.content.ItemHtml
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import kotlinx.coroutines.CancellationException
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
