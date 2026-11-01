package com.kalai73.lifemapp.widgets.core.data.presentation

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.LifeMappJson
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Shortcut
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.ThemeMode
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Everything a widget draws, decrypted and formatted. Built fresh for every render; never stored. */
data class WidgetUi(
    val authState: AuthState,
    val encryption: EncryptionState,
    val hasData: Boolean,
    val colorScheme: ColorSchemeName,
    val uiStyle: UiStyle,
    /** Light / dark choice of this device */
    val themeMode: ThemeMode,
    val language: String,
    val username: String?,
    /** "Sa. 31/10/2026" */
    val todayLabel: String,
    val today: LocalDate,
    val eventsCount: Int,
    val tasksCount: Int,
    val mailCount: Int,
    val mailConnected: Boolean,
    val mailUrl: String?,
    /** Drive section badges (label, url); empty hides the section */
    val driveLinks: List<DriveLinkUi>,
    val calendarConnected: Boolean,
    val averageRating: Double?,
    val weather: WeatherUi?,
    val eventDays: List<EventDayUi>,
    val tasks: List<TaskUi>,
    val shortcuts: List<ShortcutUi>,
    /** The Integrity domain card, null when the user has none */
    val integrity: IntegrityUi?,
    /** Quick links are encrypted and this device is locked */
    val shortcutsLocked: Boolean,
    /** Local time of the last successful sync, null if never */
    val updatedAt: String?,
    /** No successful sync for over two hours */
    val stale: Boolean,
    val syncing: Boolean,
    val error: SyncError?,
    val completionFailed: Boolean,
) {
    companion object {
        val SignedOut = WidgetUi(
            authState = AuthState.SIGNED_OUT, encryption = EncryptionState.DISABLED, hasData = false,
            colorScheme = ColorSchemeName.DEFAULT, uiStyle = UiStyle.PREDATOR, themeMode = ThemeMode.SYSTEM, language = "en", username = null,
            todayLabel = "", today = LocalDate.now(), eventsCount = 0, tasksCount = 0, mailCount = 0,
            mailConnected = false, mailUrl = null, driveLinks = emptyList(), calendarConnected = false, averageRating = null, weather = null,
            eventDays = emptyList(), tasks = emptyList(), shortcuts = emptyList(), integrity = null, shortcutsLocked = false,
            updatedAt = null, stale = false, syncing = false, error = null, completionFailed = false,
        )
    }
}

data class DriveLinkUi(val label: String, val url: String)

data class WeatherUi(val temperature: String, val low: String, val condition: String, val icon: String, val location: String)

enum class DayKind { TODAY, TOMORROW, OTHER }

data class EventDayUi(val date: LocalDate, val kind: DayKind, val label: String, val events: List<EventUi>)

data class EventUi(
    val id: String,
    val title: String,
    /** "09:30 – 10:00", or null for all-day events */
    val timeLabel: String?,
    val allDay: Boolean,
    val color: String,
    val calendarName: String?,
    val secondary: Boolean,
    /** Provider page of the event, else null (opens the Today page's events section) */
    val link: String?,
)

data class TaskUi(
    val id: String,
    /** null when encrypted and this device is locked */
    val title: String?,
    val dueLabel: String?,
    val overdue: Boolean,
    val priority: Int,
    val listName: String?,
    val listColor: String?,
    val recurring: Boolean,
    /** Can be completed from the widget (LIFE MAPP tasks) */
    val completable: Boolean,
)

data class ShortcutUi(val id: String, val label: String, val url: String, val icon: String, val color: String?)

/** The Integrity domain card. Names are null when encrypted and this device is locked. */
data class IntegrityUi(
    val name: String?,
    val infoBox: String?,
    /** 0–10, null when encrypted and locked */
    val rating: Int?,
    val topics: List<TopicUi>,
    val warnings: Int,
)

data class TopicUi(
    val id: String,
    /** 1-based, as the web numbers topics */
    val number: Int,
    val name: String?,
    val reminders: Boolean,
    val count: Int,
    val items: List<TopicItemUi>,
) {
    /** Items not listed (beyond the snapshot's cap) */
    val more: Int get() = (count - items.size).coerceAtLeast(0)
}

data class TopicItemUi(val id: String, val name: String?, val warning: Boolean, val isImage: Boolean)

object WidgetPresenter {
    const val STALE_AFTER_MS = 2 * 60 * 60 * 1000L

    fun present(
        state: CachedState,
        authState: AuthState,
        dataKey: String?,
        now: Long = System.currentTimeMillis(),
        deviceZone: ZoneId = ZoneId.systemDefault(),
    ): WidgetUi {
        val snapshot = state.snapshot
        if (authState == AuthState.SIGNED_OUT) return WidgetUi.SignedOut.copy(themeMode = state.themeMode)
        val settings = snapshot?.settings
        val zone = runCatching { ZoneId.of(settings?.timezone ?: deviceZone.id) }.getOrDefault(deviceZone)
        val language = DateLabels.baseLanguage(settings?.language)
        // "Today" is the device's current date, so the widget rolls over at midnight even before the next sync
        val today = LocalDate.now(java.time.Clock.fixed(Instant.ofEpochMilli(now), deviceZone))
        val encryption = when {
            settings?.encryptionEnabled != true -> EncryptionState.DISABLED
            dataKey != null -> EncryptionState.UNLOCKED
            else -> EncryptionState.LOCKED
        }
        val key = dataKey.takeIf { encryption == EncryptionState.UNLOCKED }

        if (snapshot == null) {
            return WidgetUi.SignedOut.copy(
                authState = authState,
                themeMode = state.themeMode,
                today = today,
                todayLabel = DateLabels.dayAndDate(today, null, language),
                syncing = state.isSyncing(now),
                error = state.lastError,
            )
        }

        val pending = state.pendingCompletions.keys
        val visibleTasks = snapshot.tasks.filterNot { it.id in pending }
        val hiddenCount = snapshot.tasks.size - visibleTasks.size

        val tasks = visibleTasks.map { t ->
            val due = t.dueDate?.let(LocalDate::parse)
            TaskUi(
                id = t.id,
                title = t.title ?: key?.let { EncryptedField.decrypt(t.encryptedTitle, it) },
                dueLabel = due?.let { d ->
                    val date = if (d == today) null else DateLabels.dayAndDate(d, settings?.dateFormat, language)
                    val time = t.due?.takeIf { t.dueTime != null }?.let { DateLabels.time(Instant.parse(it), zone, settings?.timeFormat) }
                    listOfNotNull(date, time).joinToString(" ").ifEmpty { null }
                },
                overdue = due != null && due < today,
                priority = t.priority,
                listName = t.listName ?: key?.let { EncryptedField.decrypt(t.encryptedListName, it) },
                listColor = t.listColor,
                recurring = t.recurring,
                completable = t.source == "local",
            )
        }

        val eventDays = snapshot.events
            // After midnight, yesterday's events drop out; multi-day all-day events still running stay
            .filter { e -> LocalDate.parse(e.date) >= today || (e.allDay && e.end?.let(LocalDate::parse)?.isAfter(today) == true) }
            .groupBy { e -> maxOf(LocalDate.parse(e.date), today) }
            .toSortedMap()
            .map { (date, events) ->
                EventDayUi(
                    date = date,
                    kind = when (date) {
                        today -> DayKind.TODAY
                        today.plusDays(1) -> DayKind.TOMORROW
                        else -> DayKind.OTHER
                    },
                    label = DateLabels.dayAndDate(date, settings?.dateFormat, language),
                    events = events.map { e ->
                        EventUi(
                            id = e.id,
                            title = e.title,
                            timeLabel = if (e.allDay) null else {
                                val start = DateLabels.time(Instant.parse(e.start), zone, settings?.timeFormat)
                                val end = e.end?.let { DateLabels.time(Instant.parse(it), zone, settings?.timeFormat) }
                                if (end != null && end != start) "$start – $end" else start
                            },
                            allDay = e.allDay,
                            color = e.calendarColor,
                            calendarName = e.calendarName,
                            secondary = e.secondary,
                            link = e.htmlLink,
                        )
                    },
                )
            }

        val (shortcuts, shortcutsLocked) = shortcuts(snapshot.shortcuts.plain, snapshot.shortcuts.encrypted, key, encryption)

        val ratings = snapshot.domains.ratings + (key?.let { k ->
            snapshot.domains.encryptedRatings.mapNotNull { EncryptedField.decrypt(it, k)?.toIntOrNull() }
        } ?: emptyList())
        val averageRating = if (ratings.isEmpty()) null else (ratings.average() * 10).roundToInt() / 10.0

        val unit = if (snapshot.weather?.unit == "fahrenheit") "°F" else "°C"

        return WidgetUi(
            authState = authState,
            encryption = encryption,
            hasData = true,
            colorScheme = ColorSchemeName.from(settings?.colorScheme),
            uiStyle = UiStyle.from(settings?.uiStyle),
            themeMode = state.themeMode,
            language = language,
            username = settings?.username,
            todayLabel = DateLabels.dayAndDate(today, settings?.dateFormat, language),
            today = today,
            eventsCount = run {
                val listedToday = eventDays.firstOrNull { it.kind == DayKind.TODAY }?.events?.size ?: 0
                // The server's count also covers events beyond the list cap, but only for the day it was made
                if (snapshot.today == today.toString()) maxOf(listedToday, snapshot.counts.events) else listedToday
            },
            tasksCount = (snapshot.counts.tasks - hiddenCount).coerceAtLeast(0),
            mailCount = snapshot.counts.mail,
            mailConnected = snapshot.counts.mailConnected,
            mailUrl = snapshot.mail.inboxUrl,
            driveLinks = snapshot.drive.links.filter { it.url.isNotBlank() }.map { DriveLinkUi(it.label.ifBlank { it.url }, it.url) },
            calendarConnected = snapshot.counts.calendarConnected,
            averageRating = averageRating,
            weather = snapshot.weather?.let { w ->
                WeatherUi("${w.high}$unit", "${w.low}$unit", w.condition, w.icon, w.location)
            },
            eventDays = eventDays,
            tasks = tasks,
            shortcuts = shortcuts,
            integrity = snapshot.integrity?.let { integrity(it, key) },
            shortcutsLocked = shortcutsLocked,
            updatedAt = state.lastSuccessAt.takeIf { it > 0 }?.let { DateLabels.time(Instant.ofEpochMilli(it), deviceZone, settings?.timeFormat) },
            stale = state.lastSuccessAt > 0 && now - state.lastSuccessAt > STALE_AFTER_MS,
            syncing = state.isSyncing(now),
            error = state.lastError,
            completionFailed = state.completionFailed,
        )
    }

    private fun shortcuts(
        plain: List<Shortcut>,
        encrypted: String?,
        key: String?,
        encryption: EncryptionState,
    ): Pair<List<ShortcutUi>, Boolean> {
        val entries = when {
            encrypted == null -> plain
            key != null -> EncryptedField.decrypt(encrypted, key)
                ?.let { runCatching { LifeMappJson.decodeFromString(ListSerializer(Shortcut.serializer()), it) }.getOrNull() }
                ?: plain
            else -> return plain.map(::toUi) to (encryption == EncryptionState.LOCKED)
        }
        return entries.filter { it.url.isNotBlank() }.sortedBy { it.position }.map(::toUi) to false
    }

    private fun toUi(s: Shortcut) = ShortcutUi(s.id, s.label, s.url, s.icon, s.color)

    private fun integrity(i: Integrity, key: String?): IntegrityUi {
        fun text(plain: String?, encrypted: String?): String? =
            if (encrypted != null) key?.let { EncryptedField.decrypt(encrypted, it) } else plain
        val rating = if (i.encryptedRating != null) key?.let { EncryptedField.decrypt(i.encryptedRating, it) }?.toDoubleOrNull()
        else i.rating
        return IntegrityUi(
            name = text(i.name, i.encryptedName),
            infoBox = text(i.infoBox, i.encryptedInfoBox)?.takeIf { it.isNotBlank() },
            rating = rating?.roundToInt()?.coerceIn(0, 10),
            topics = i.topics.mapIndexed { index, t ->
                TopicUi(
                    id = t.id,
                    number = index + 1,
                    name = text(t.name, t.encryptedName),
                    reminders = t.reminders,
                    count = maxOf(t.count, t.items.size),
                    items = t.items.map { item -> TopicItemUi(item.id, text(item.name, item.encryptedName), item.warning, item.isImage) },
                )
            },
            warnings = i.warnings,
        )
    }
}
