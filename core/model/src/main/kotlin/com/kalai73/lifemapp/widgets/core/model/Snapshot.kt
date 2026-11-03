package com.kalai73.lifemapp.widgets.core.model

import kotlinx.serialization.Serializable

/**
 * Payload of `GET /api/widget/snapshot` (lifeMappWeb: frontend/src/lib/widget/types.ts).
 *
 * Every field has a default so an older or newer server never breaks parsing. End-to-end
 * encrypted values arrive as the stored JSON strings (`{"encrypted","iv","tag"}`) and are
 * only decrypted on device, at render time.
 */
@Serializable
data class Snapshot(
    val version: Int = 1,
    val generatedAt: String = "",
    /** Local date the snapshot treats as today (YYYY-MM-DD) */
    val today: String = "",
    val settings: SnapshotSettings = SnapshotSettings(),
    val counts: Counts = Counts(),
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val shortcuts: Shortcuts = Shortcuts(),
    val domains: Domains = Domains(),
    val weather: Weather? = null,
    val mail: Mail = Mail(),
    /** The Drive section's service links; empty from an older server */
    val drive: Drive = Drive(),
    /** The Integrity domain card; null when the user has none (or an older server) */
    val integrity: Integrity? = null,
    /**
     * The web Today page's other life domains (Serenity, Vitality, Stability, Tenacity: positions 2–5) as the same
     * card, in position order. The app's Today screen shows them; the widgets don't. Empty from an older server.
     */
    val todayDomains: List<Integrity> = emptyList(),
    /**
     * The web Monthly page's domains (positions 10–19, plus 5 for embedding in Trayectory).
     */
    val monthlyDomains: List<Integrity> = emptyList(),
    /** Sections the server couldn't load this time; the app keeps its cached copy of those */
    val failedSections: List<String> = emptyList(),
)

@Serializable
data class SnapshotSettings(
    val username: String? = null,
    val colorScheme: String = "default",
    val uiStyle: String = "predator",
    val language: String = "en",
    val dateFormat: String? = null,
    val timeFormat: String? = null,
    val timezone: String = "UTC",
    val encryptionEnabled: Boolean = false,
)

@Serializable
data class Counts(
    val events: Int = 0,
    val tasks: Int = 0,
    val mail: Int = 0,
    val mailConnected: Boolean = false,
    val calendarConnected: Boolean = false,
)

@Serializable
data class Event(
    val id: String,
    val title: String = "",
    /** ISO instant for timed events, YYYY-MM-DD for all-day events */
    val start: String,
    val end: String? = null,
    val allDay: Boolean = false,
    /** Local start date (YYYY-MM-DD), for grouping by day */
    val date: String,
    val calendarName: String? = null,
    val calendarColor: String = "#3b82f6",
    val secondary: Boolean = false,
    val source: String = "ics",
    val htmlLink: String? = null,
    val location: String? = null,
    /** Plain text as the calendar stores it, capped by the server; null for none */
    val description: String? = null,
    /** "confirmed", "tentative", "cancelled", or null */
    val status: String? = null,
)

@Serializable
data class Task(
    val id: String,
    val title: String? = null,
    val encryptedTitle: String? = null,
    val due: String? = null,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val overdue: Boolean = false,
    /** Todoist-style: 4 = P1 (highest) … 1 = P4 */
    val priority: Int = 1,
    val listName: String? = null,
    val encryptedListName: String? = null,
    val listColor: String? = null,
    val recurring: Boolean = false,
    val source: String = "local",
    /** HTML for LIFE MAPP tasks, a task manager's notes as stored (plain text, Markdown or HTML); capped by the server, null when blank, encrypted or not sent */
    val description: String? = null,
    /** The end-to-end encrypted description (EncryptedField JSON) */
    val encryptedDescription: String? = null,
    /**
     * The web Task List Widget's date segment: "today" (Today list, due today or overdue), "tomorrow" or "later"
     * (its "Tomorrow ++ 1"). Servers before upcomingTasks list today's tasks only, hence the default.
     */
    val segment: String = SEGMENT_TODAY,
) {
    companion object {
        const val SEGMENT_TODAY = "today"
        const val SEGMENT_TOMORROW = "tomorrow"
        const val SEGMENT_LATER = "later"
    }
}

@Serializable
data class Shortcut(
    val id: String,
    val label: String = "",
    val url: String,
    val icon: String = "",
    val color: String? = null,
    val position: Int = 0,
)

@Serializable
data class Shortcuts(
    val plain: List<Shortcut> = emptyList(),
    /** Encrypted JSON array of quick links when they are end-to-end encrypted */
    val encrypted: String? = null,
)

@Serializable
data class Domains(
    val averageRating: Double? = null,
    val ratings: List<Int> = emptyList(),
    val encryptedRatings: List<String> = emptyList(),
)

@Serializable
data class Weather(
    val location: String = "",
    val unit: String = "celsius",
    val high: Int = 0,
    val low: Int = 0,
    val condition: String = "",
    /** Visual Crossing icon id, e.g. "partly-cloudy-day" */
    val icon: String = "",
    /** The next days after today (up to five), in date order; empty from an older server */
    val forecast: List<WeatherDay> = emptyList(),
)

@Serializable
data class WeatherDay(
    /** yyyy-MM-dd */
    val date: String = "",
    val high: Int = 0,
    val low: Int = 0,
    val condition: String = "",
    val icon: String = "",
)

@Serializable
data class Mail(
    val inboxUrl: String? = null,
    val provider: String? = null,
    /** The web E-Mails widget's tags of the shown account, in its order; empty from older servers */
    val tags: List<MailTag> = emptyList(),
)

/** A Gmail label or Outlook folder as the web E-Mails widget lists it. */
@Serializable
data class MailTag(
    /** null for the inbox, which the widget calls "Today" */
    val name: String? = null,
    /** inbox, 7days, 30days, 365days or other */
    val tier: String = TIER_OTHER,
    /** Lucide icon name */
    val icon: String = "tag",
    val color: String? = null,
    val unread: Int = 0,
    val total: Int = 0,
    val url: String? = null,
) {
    companion object {
        const val TIER_INBOX = "inbox"
        const val TIER_7_DAYS = "7days"
        const val TIER_30_DAYS = "30days"
        const val TIER_365_DAYS = "365days"
        const val TIER_OTHER = "other"
    }
}

@Serializable
data class Drive(
    val links: List<DriveLink> = emptyList(),
)

@Serializable
data class DriveLink(
    val label: String = "",
    val url: String,
)

/**
 * The Integrity domain card of the web's Today page (integrity-domain-with-widgets.tsx). Encrypted values are
 * self-contained `{"encrypted","iv","tag"}` JSON; the plaintext field is then null.
 */
@Serializable
data class Integrity(
    val domainId: String,
    /** 1 for Integrity, 2–5 for the other Today domains */
    val position: Int = 1,
    val name: String? = null,
    val encryptedName: String? = null,
    /** e.g. "Capture & Organize" */
    val infoBox: String? = null,
    val encryptedInfoBox: String? = null,
    /** 0–10 (a Double so an unexpected fraction can't fail the whole snapshot) */
    val rating: Double? = null,
    val encryptedRating: String? = null,
    val color: String? = null,
    val topics: List<Topic> = emptyList(),
    /** Items flagged with a warning in any domain */
    val warnings: Int = 0,
)

/** A topic (subdomain) in position order, with its first items. */
@Serializable
data class Topic(
    val id: String,
    val name: String? = null,
    val encryptedName: String? = null,
    /** The Reminders topic, which the Reception adds to */
    val reminders: Boolean = false,
    /** Collapsed on the web card (subdomain is_collapsed): the widget lists no items */
    val collapsed: Boolean = false,
    /** Every item, also those beyond the list cap */
    val count: Int = 0,
    val items: List<TopicItem> = emptyList(),
)

@Serializable
data class TopicItem(
    val id: String,
    val name: String? = null,
    val encryptedName: String? = null,
    val warning: Boolean = false,
    val isImage: Boolean = false,
    /** The item's text as HTML; null for images, encrypted and empty items */
    val content: String? = null,
    val encryptedContent: String? = null,
    /** Collapsed on the web card (subsubdomain is_collapsed): only the header shows */
    val collapsed: Boolean = false,
)

/** Section names used in [Snapshot.failedSections]. */
object Sections {
    const val EVENTS = "events"
    const val TASKS = "tasks"
    const val MAIL = "mail"
    const val DOMAINS = "domains"
    const val WEATHER = "weather"
    const val SHORTCUTS = "shortcuts"
    const val INTEGRITY = "integrity"
    const val TODAY_DOMAINS = "todayDomains"
    const val MONTHLY_DOMAINS = "monthlyDomains"
}

enum class DashboardTab { TODAY, MONTHLY }
