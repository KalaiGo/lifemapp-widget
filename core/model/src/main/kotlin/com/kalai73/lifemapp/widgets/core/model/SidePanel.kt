package com.kalai73.lifemapp.widgets.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A plan's limits on the life map tree, as the web's side panel applies them (lib/links/domain-limits.ts). */
@Serializable
data class PlanLimits(
    val tier: String,
    /** limits.max_subdomain_chars: the cap on an item's text */
    val maxItemChars: Int,
    val maxTopicsPerDomain: Int,
    val maxItemsPerTopic: Int,
    /** Items in Integrity's Reminders topic (reminder-limits.ts getMaxReminders) */
    val maxReminders: Int,
) {
    companion object {
        /** subscription-features.ts SUBSCRIPTION_LIMITS and reminder-limits.ts, for a database without the limits row */
        fun forTier(tier: String): PlanLimits = when (tier) {
            "pro" -> PlanLimits(tier, 7000, 10, 7, 30)
            "master", "admin" -> PlanLimits(tier, 20000, 20, 10, 50)
            "business" -> PlanLimits(tier, 10000, 10, 10, 50)
            else -> PlanLimits("basic", 5000, 3, 2, 10)
        }
    }
}

// ── Feeds (mirror of lifeMappWeb/frontend/src/types/feeds.types.ts) ────────────

@Serializable
data class FeedFolder(
    val id: String,
    val name: String = "",
    @SerialName("domain_id") val domainId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val position: Int = 0,
)

@Serializable
data class Feed(
    val id: String,
    @SerialName("folder_id") val folderId: String? = null,
    @SerialName("source_type") val sourceType: String = "rss",
    val platform: String = "rss",
    val title: String = "",
    val url: String = "",
    @SerialName("site_url") val siteUrl: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("fetch_interval_minutes") val fetchIntervalMinutes: Int = 30,
    val position: Int = 0,
    @SerialName("last_fetched_at") val lastFetchedAt: String? = null,
    @SerialName("last_fetch_status") val lastFetchStatus: String = "ok",
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val tags: List<String> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("today_count") val todayCount: Int = 0,
)

/** The feed an item comes from, as /api/feeds/items joins it */
@Serializable
data class FeedRef(
    val id: String,
    val title: String = "",
    val platform: String = "rss",
    @SerialName("source_type") val sourceType: String = "rss",
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("site_url") val siteUrl: String? = null,
)

@Serializable
data class FeedItem(
    val id: String,
    @SerialName("feed_id") val feedId: String,
    val title: String = "",
    val url: String = "",
    val author: String? = null,
    val summary: String? = null,
    @SerialName("content_html") val contentHtml: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("media_urls") val mediaUrls: List<String> = emptyList(),
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("is_starred") val isStarred: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    val feed: FeedRef? = null,
)

@Serializable
data class FeedsResponse(
    val folders: List<FeedFolder> = emptyList(),
    val feeds: List<Feed> = emptyList(),
)

@Serializable
data class FeedItemsResponse(
    val items: List<FeedItem> = emptyList(),
    val hasMore: Boolean = false,
)

/** The feed sidebar's smart views (feed-store.ts SmartView); Unread is the default */
enum class SmartView { ALL, UNREAD, STARRED }

/** What the Feeds stream lists: a smart view, narrowed to a folder (and its subfolders), a feed or a topic tag */
@Serializable
data class FeedFilter(
    val smartView: SmartView = SmartView.UNREAD,
    val folderId: String? = null,
    val feedId: String? = null,
    val tag: String? = null,
    val search: String = "",
    val oldestFirst: Boolean = false,
)
