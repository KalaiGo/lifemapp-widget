package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.model.Feed
import com.kalai73.lifemapp.widgets.core.model.FeedFilter
import com.kalai73.lifemapp.widgets.core.model.FeedFolder
import com.kalai73.lifemapp.widgets.core.model.FeedItemsResponse
import com.kalai73.lifemapp.widgets.core.model.FeedsResponse
import com.kalai73.lifemapp.widgets.core.model.LifeMappJson
import com.kalai73.lifemapp.widgets.core.model.SmartView
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** A feeds route refused the request with a message meant for the user (limits, invalid URL, …). */
class FeedRequestException(message: String, val status: Int) : Exception(message)

/** The web's feed reader (stores/feed-store.ts): /api/feeds/... with Bearer auth. */
class FeedsApi internal constructor(client: ApiClient) : ApiArea(client) {
    private val base get() = "${config.webUrl}/api/feeds"

    /** Folders and feeds with unread and today counts; [sinceIso] is the device's local midnight. */
    suspend fun feeds(sinceIso: String): FeedsResponse =
        authorized { token -> http.get(base) { bearerAuth(token); parameter("since", sinceIso) } }.body()

    /** One page of items for [filter], [offset] items in (feed-store.ts fetchItems). */
    suspend fun items(filter: FeedFilter, offset: Int, limit: Int = PAGE_SIZE): FeedItemsResponse =
        authorized { token ->
            http.get("$base/items") {
                bearerAuth(token)
                when {
                    filter.feedId != null -> parameter("feedId", filter.feedId)
                    filter.folderId != null -> parameter("folderId", filter.folderId)
                    filter.tag != null -> parameter("tag", filter.tag)
                }
                if (filter.smartView == SmartView.UNREAD) parameter("unreadOnly", "true")
                if (filter.smartView == SmartView.STARRED) parameter("starredOnly", "true")
                filter.search.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
                parameter("sort", if (filter.oldestFirst) "oldest" else "newest")
                parameter("limit", limit)
                parameter("offset", offset)
            }
        }.body()

    suspend fun setRead(itemId: String, read: Boolean) = patchItem(itemId, buildJsonObject { put("is_read", read) })

    suspend fun setStarred(itemId: String, starred: Boolean) = patchItem(itemId, buildJsonObject { put("is_starred", starred) })

    private suspend fun patchItem(itemId: String, body: JsonObject) {
        authorized { token ->
            http.patch("$base/items/$itemId") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(body) }
        }
    }

    /** Marks the filter's items read; with [beforeIso], only those published before it. */
    suspend fun markAllRead(filter: FeedFilter, beforeIso: String?) {
        val body = buildJsonObject {
            when {
                filter.feedId != null -> put("feed_id", filter.feedId)
                filter.folderId != null -> put("folder_id", filter.folderId)
                filter.tag != null -> put("tag", filter.tag)
            }
            beforeIso?.let { put("before", it) }
        }
        authorized { token ->
            http.post("$base/items/mark-read") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(body) }
        }
    }

    /** Syncs one feed now; returns how many new items were inserted. The route's own error text is thrown as [FeedRequestException]. */
    suspend fun refresh(feedId: String): Int {
        val response = userCall { token -> http.post("$base/$feedId/refresh") { bearerAuth(token) } }
        return runCatching {
            (LifeMappJson.parseToJsonElement(response.bodyAsText()).jsonObject["insertedCount"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        }.getOrNull() ?: 0
    }

    /** Subscribes to a URL or handle (POST /api/feeds); the server detects RSS, YouTube or social sources. */
    suspend fun subscribe(url: String, title: String?, folderId: String?): Feed {
        val body = buildJsonObject {
            put("url", url.trim())
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
            put("folder_id", folderId?.let(::JsonPrimitive) ?: JsonNull)
        }
        val response = userCall { token ->
            http.post(base) { bearerAuth(token); contentType(ContentType.Application.Json); setBody(body) }
        }
        return LifeMappJson.decodeFromJsonElement(response.body<JsonObject>().getValue("feed"))
    }

    /** Changes a feed's title, folder, interval, active flag or tags (PATCH /api/feeds/{id}). */
    suspend fun updateFeed(
        feedId: String,
        title: String? = null,
        folderId: String? = null,
        clearFolder: Boolean = false,
        intervalMinutes: Int? = null,
        active: Boolean? = null,
        tags: List<String>? = null,
    ): Feed {
        val body = buildJsonObject {
            title?.let { put("title", it.trim()) }
            if (clearFolder) put("folder_id", JsonNull) else folderId?.let { put("folder_id", it) }
            intervalMinutes?.let { put("fetch_interval_minutes", it) }
            active?.let { put("is_active", it) }
            tags?.let { put("tags", JsonArray(it.map(::JsonPrimitive))) }
        }
        val response = userCall { token ->
            http.patch("$base/$feedId") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(body) }
        }
        return LifeMappJson.decodeFromJsonElement(response.body<JsonObject>().getValue("feed"))
    }

    suspend fun deleteFeed(feedId: String) {
        authorized { token -> http.delete("$base/$feedId") { bearerAuth(token) } }
    }

    suspend fun createFolder(name: String, parentId: String?): FeedFolder {
        val body = buildJsonObject {
            put("name", name.trim())
            put("parent_id", parentId?.let(::JsonPrimitive) ?: JsonNull)
        }
        val response = userCall { token ->
            http.post("$base/folders") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(body) }
        }
        return LifeMappJson.decodeFromJsonElement(response.body<JsonObject>().getValue("folder"))
    }

    /** Deletes a folder; its subfolders and feeds move up to its parent. */
    suspend fun deleteFolder(folderId: String) {
        userCall { token -> http.delete("$base/folders") { bearerAuth(token); parameter("id", folderId) } }
    }

    /** The user's feeds as OPML XML (GET /api/feeds/opml). */
    suspend fun exportOpml(): String = authorized { token -> http.get("$base/opml") { bearerAuth(token) } }.bodyAsText()

    /** Imports an OPML document; returns how many feeds were added and how many outlines it had. */
    suspend fun importOpml(xml: String): Pair<Int, Int> {
        val response = userCall { token ->
            http.post("$base/opml") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("xml", xml) })
            }
        }
        val body = response.body<JsonObject>()
        fun count(name: String) = (body[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        return count("imported") to count("totalParsed")
    }

    /** A feed item's picture from its own host: a plain GET, never with the session's token. Null on any failure. */
    suspend fun downloadImage(url: String): ByteArray? = runCatching {
        val response = http.get(url)
        if (response.status.isSuccess()) response.readRawBytes() else null
    }.getOrNull()

    /**
     * A call whose 4xx answers carry an `error` text for the user: thrown as [FeedRequestException] instead of a bare
     * [ServerException], so the dialog can show why (feed limit reached, not a feed, …).
     */
    private suspend fun userCall(request: suspend (String) -> HttpResponse): HttpResponse {
        val response = authorized(accept = { it.isSuccess() || it.value in 400..499 }, request = request)
        if (response.status.isSuccess()) return response
        val message = runCatching {
            (LifeMappJson.parseToJsonElement(response.bodyAsText()).jsonObject["error"] as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        throw FeedRequestException(message ?: "Request failed (${response.status.value})", response.status.value)
    }

    companion object {
        /** feed-store.ts fetches 40 items per page */
        const val PAGE_SIZE = 40
    }
}

/** Link titles (POST /api/links/titles) for media links without their own text. */
class LinksApi internal constructor(client: ApiClient) : ApiArea(client) {
    /** Titles by URL; URLs without one are left out. At most [MAX_TITLE_BATCH] URLs per call. */
    suspend fun titles(urls: List<String>): Map<String, String> {
        if (urls.isEmpty()) return emptyMap()
        val response = authorized { token ->
            http.post("${config.webUrl}/api/links/titles") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("urls", JsonArray(urls.take(MAX_TITLE_BATCH).map(::JsonPrimitive))) })
            }
        }
        val titles = response.body<JsonObject>()["titles"] as? JsonObject ?: return emptyMap()
        return titles.mapNotNull { (url, title) -> (title as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { url to it } }.toMap()
    }

    companion object {
        /** lib/links/link-titles.ts MAX_TITLE_BATCH */
        const val MAX_TITLE_BATCH = 40
    }
}
