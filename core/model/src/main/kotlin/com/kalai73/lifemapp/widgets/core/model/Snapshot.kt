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
)

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
)

@Serializable
data class Mail(
    val inboxUrl: String? = null,
    val provider: String? = null,
)

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
    val name: String? = null,
    val encryptedName: String? = null,
    /** e.g. "Capture & Organize" */
    val infoBox: String? = null,
    val encryptedInfoBox: String? = null,
    /** 0–10 (a Double so an unexpected fraction can't fail the whole snapshot) */
    val rating: Double? = null,
    val encryptedRating: String? = null,
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
}
