package com.kalai73.lifemapp.widgets.core.data.presentation

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.LifeMappJson
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.MailTag
import com.kalai73.lifemapp.widgets.core.data.repository.TaskRepository
import com.kalai73.lifemapp.widgets.core.model.Shortcut
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.Task
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
    /** The web E-Mails widget's tags; empty when the server sends none (then the inbox row alone) */
    val mailTags: List<MailTagUi> = emptyList(),
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
    /** The other Today domains (positions 2–5), in order: the app's Today screen only, never the widgets */
    val todayDomains: List<IntegrityUi> = emptyList(),
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

/**
 * A mail tag row as the web E-Mails widget draws it. [name] is null for the inbox ("Today").
 * [fade] dims later horizons as the web does: 1 for the inbox and others, down to 0.4 for "In 365 days".
 */
data class MailTagUi(
    val name: String?,
    val icon: String,
    val color: String?,
    val unread: Int,
    val total: Int,
    val url: String?,
    val fade: Float,
)

data class WeatherUi(
    val temperature: String,
    val low: String,
    val condition: String,
    val icon: String,
    val location: String,
    /** The next days after today, for the forecast row; empty when the server sent none */
    val forecast: List<ForecastDayUi> = emptyList(),
)

/** One forecast day: short weekday ("Mon"), high and low with unit. */
data class ForecastDayUi(val day: String, val high: String, val low: String, val condition: String, val icon: String)

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
    /** Can be completed from the widget (LIFE MAPP and Todoist tasks) */
    val completable: Boolean,
    /** The web Task List Widget's segment: TODAY, TOMORROW, or OTHER ("Tomorrow ++ 1"). Only TODAY is at full strength. */
    val day: DayKind = DayKind.TODAY,
    /**
     * The web's task color tier (lib/tasks/task-style.ts getTaskColorTier), colouring the checkbox and bar:
     * 4 red (P1, Today list, due today or overdue), 3 orange (P2, 7-day list, due within 7 days), 2 blue, 1 neutral.
     */
    val colorTier: Int = 1,
    /** The web's provider badge: "LIFE MAPP", "TODOIST-LIF" (Todoist plus its project's first 3 letters), … */
    val tagLabel: String? = null,
    /** The provider badge's hex color (emerald for LIFE MAPP, red for Todoist, …) */
    val tagColor: String? = null,
) {
    val upcoming: Boolean get() = day != DayKind.TODAY
}

data class ShortcutUi(val id: String, val label: String, val url: String, val icon: String, val color: String?)

/**
 * A domain card: Integrity, or one of the other Today domains (no Reminders topic, warnings 0). Names are null
 * when encrypted and this device is locked.
 */
data class IntegrityUi(
    val domainId: String = "",
    /** 1 for Integrity, 2–5 for Serenity, Vitality, Stability, Tenacity */
    val position: Int = 1,
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
    /** Collapsed on the web card: show the header only */
    val collapsed: Boolean = false,
    val count: Int,
    val items: List<TopicItemUi>,
) {
    /** Items not listed (beyond the snapshot's cap) */
    val more: Int get() = (count - items.size).coerceAtLeast(0)
}

data class TopicItemUi(
    val id: String,
    val name: String?,
    val warning: Boolean,
    val isImage: Boolean,
    /** The item's text as HTML, null when it has none or can't be decrypted */
    val content: String? = null,
    /** Collapsed on the web card: the header only */
    val collapsed: Boolean = false,
    /** The item has text, also when it's encrypted and this device is locked ([content] is null then) */
    val hasContent: Boolean = content != null,
)

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
                todayLabel = DateLabels.dayAndDate(today, DateLabels.DATE_FORMAT, language),
                syncing = state.isSyncing(now),
                error = state.lastError,
            )
        }

        val pending = state.pendingCompletions.keys
        val visibleTasks = snapshot.tasks.filterNot { it.id in pending }
        // The server counts the Today segment only
        val hiddenCount = snapshot.tasks.count { it.id in pending && it.segment == Task.SEGMENT_TODAY }

        // Within a day, the server's earlier segments first: after midnight, yesterday's Tomorrow tasks join Today
        // below the tasks that were already today's (a stable sort on the day alone would keep the input order)
        val tasks = visibleTasks.sortedBy { segmentRank(it.segment) }.map { t ->
            val due = t.dueDate?.let(LocalDate::parse)
            val listName = t.listName ?: key?.let { EncryptedField.decrypt(t.encryptedListName, it) }
            val (tagLabel, tagColor) = providerTag(t.source, listName)
            TaskUi(
                id = t.id,
                title = t.title ?: key?.let { EncryptedField.decrypt(t.encryptedTitle, it) },
                dueLabel = due?.let { d ->
                    val date = if (d == today) null else DateLabels.dayAndDate(d, DateLabels.DATE_FORMAT, language)
                    // Connected task managers send only the local time, no instant
                    val time = t.due?.takeIf { t.dueTime != null }?.let { DateLabels.time(Instant.parse(it), zone, settings?.timeFormat) }
                        ?: t.dueTime?.let { DateLabels.time(it, settings?.timeFormat) }
                    listOfNotNull(date, time).joinToString(" ").ifEmpty { null }
                },
                overdue = due != null && due < today,
                priority = t.priority,
                listName = listName,
                listColor = t.listColor,
                recurring = t.recurring,
                completable = TaskRepository.canComplete(t.source),
                day = taskDay(t.segment, due, today),
                colorTier = colorTier(t.priority, listName, due?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }),
                tagLabel = tagLabel,
                tagColor = tagColor,
            )
        }
            // Segments in order (Today, Tomorrow, Tomorrow ++ 1); the server's order within each is kept
            .sortedBy { it.day.ordinal }

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
                    label = DateLabels.dayAndDate(date, DateLabels.DATE_FORMAT, language),
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
            colorScheme = state.colorScheme,
            uiStyle = UiStyle.from(settings?.uiStyle),
            themeMode = state.themeMode,
            language = language,
            username = settings?.username,
            todayLabel = DateLabels.dayAndDate(today, DateLabels.DATE_FORMAT, language),
            today = today,
            eventsCount = run {
                val listedToday = eventDays.firstOrNull { it.kind == DayKind.TODAY }?.events?.size ?: 0
                // The server's count also covers events beyond the list cap, but only for the day it was made
                if (snapshot.today == today.toString()) maxOf(listedToday, snapshot.counts.events) else listedToday
            },
            tasksCount = run {
                val listedToday = tasks.count { it.day == DayKind.TODAY }
                // As for events: the server's count covers tasks beyond the list cap, but only for the day it was made
                val counted = (snapshot.counts.tasks - hiddenCount).coerceAtLeast(0)
                if (snapshot.today == today.toString()) maxOf(listedToday, counted) else listedToday
            },
            mailCount = snapshot.counts.mail,
            mailConnected = snapshot.counts.mailConnected,
            mailUrl = snapshot.mail.inboxUrl,
            mailTags = snapshot.mail.tags.map { t ->
                MailTagUi(
                    name = t.name,
                    icon = t.icon,
                    color = t.color,
                    unread = t.unread,
                    total = t.total,
                    url = t.url?.takeIf { it.isNotBlank() },
                    fade = mailTagFade(t.tier),
                )
            },
            driveLinks = snapshot.drive.links.filter { it.url.isNotBlank() }.map { DriveLinkUi(it.label.ifBlank { it.url }, it.url) },
            calendarConnected = snapshot.counts.calendarConnected,
            averageRating = averageRating,
            weather = snapshot.weather?.let { w ->
                WeatherUi(
                    "${w.high}$unit", "${w.low}$unit", w.condition, w.icon, w.location,
                    forecast = w.forecast.mapNotNull { d ->
                        val date = runCatching { LocalDate.parse(d.date) }.getOrNull() ?: return@mapNotNull null
                        if (!date.isAfter(today)) return@mapNotNull null
                        ForecastDayUi(DateLabels.shortDayName(date, language), "${d.high}°", "${d.low}°", d.condition, d.icon)
                    }.take(5),
                )
            },
            eventDays = eventDays,
            tasks = tasks,
            shortcuts = shortcuts,
            integrity = snapshot.integrity?.let { integrity(it, key) },
            todayDomains = snapshot.todayDomains.map { integrity(it, key) },
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

    /**
     * The task's segment on the device's date, so a cached snapshot rolls over at midnight: "today" stays Today
     * (Today list or overdue); a due date otherwise decides; undated upcoming tasks ("In 7 days" list) stay later.
     */
    private fun segmentRank(segment: String): Int = when (segment) {
        Task.SEGMENT_TODAY -> 0
        Task.SEGMENT_TOMORROW -> 1
        else -> 2
    }

    internal fun taskDay(segment: String, due: LocalDate?, today: LocalDate): DayKind = when {
        segment == Task.SEGMENT_TODAY -> DayKind.TODAY
        due == null -> if (segment == Task.SEGMENT_TOMORROW) DayKind.TOMORROW else DayKind.OTHER
        !due.isAfter(today) -> DayKind.TODAY
        due == today.plusDays(1) -> DayKind.TOMORROW
        else -> DayKind.OTHER
    }

    /**
     * The web's task color tier (lib/tasks/task-style.ts getTaskColorTier): an explicit priority, else the
     * horizon list (Today, 7, 30, 365 days), else how soon it is due ([daysUntilDue] 0 today, negative overdue).
     */
    internal fun colorTier(priority: Int, listName: String?, daysUntilDue: Long?): Int {
        if (priority > 1) return priority.coerceIn(1, 4)
        val name = listName?.lowercase()?.trim().orEmpty()
        if (name.isNotEmpty()) {
            if ("today" in name || "hoy" in name || "heute" in name) return 4
            if ('7' in name) return 3
            if ("30" in name) return 2
            if ("365" in name) return 1
        }
        if (daysUntilDue != null) {
            if (daysUntilDue <= 0) return 4
            if (daysUntilDue <= 7) return 3
            if (daysUntilDue <= 30) return 2
        }
        return 1
    }

    /**
     * The web's provider badge (components/tasks/task-list-item.tsx PROVIDER_CONFIG): label and hex color.
     * Todoist adds its project's first three letters, as the web does.
     */
    internal fun providerTag(source: String, listName: String?): Pair<String, String> = when (source) {
        "todoist" -> ("Todoist" + (listName?.trim()?.takeIf { it.isNotEmpty() }?.let { "-" + it.take(3) } ?: "")) to "#EF4444"
        "google-tasks" -> "Google" to "#3B82F6"
        "microsoft-todo" -> "To Do" to "#0EA5E9"
        "ticktick" -> "TickTick" to "#F59E0B"
        else -> "LIFE MAPP" to "#10B981"
    }

    /** The web widget's row opacity per horizon tier (gmail-labels-widget.tsx) */
    internal fun mailTagFade(tier: String): Float = when (tier) {
        MailTag.TIER_7_DAYS -> 0.85f
        MailTag.TIER_30_DAYS -> 0.6f
        MailTag.TIER_365_DAYS -> 0.4f
        MailTag.TIER_OTHER -> 0.65f
        else -> 1f
    }

    private fun toUi(s: Shortcut) = ShortcutUi(s.id, s.label, s.url, s.icon, s.color)

    private fun integrity(i: Integrity, key: String?): IntegrityUi {
        fun text(plain: String?, encrypted: String?): String? =
            if (encrypted != null) key?.let { EncryptedField.decrypt(encrypted, it) } else plain
        val rating = if (i.encryptedRating != null) key?.let { EncryptedField.decrypt(i.encryptedRating, it) }?.toDoubleOrNull()
        else i.rating
        return IntegrityUi(
            domainId = i.domainId,
            position = i.position,
            name = text(i.name, i.encryptedName),
            infoBox = text(i.infoBox, i.encryptedInfoBox)?.takeIf { it.isNotBlank() },
            rating = rating?.roundToInt()?.coerceIn(0, 10),
            topics = i.topics.mapIndexed { index, t ->
                TopicUi(
                    id = t.id,
                    number = index + 1,
                    name = text(t.name, t.encryptedName),
                    reminders = t.reminders,
                    collapsed = t.collapsed && !t.reminders,
                    count = maxOf(t.count, t.items.size),
                    items = t.items.map { item ->
                        val content = text(item.content, item.encryptedContent)?.takeIf { it.isNotBlank() }
                        TopicItemUi(
                            item.id, text(item.name, item.encryptedName), item.warning, item.isImage,
                            content = content,
                            collapsed = item.collapsed,
                            hasContent = content != null || (!item.isImage && !item.encryptedContent.isNullOrBlank()),
                        )
                    },
                )
            },
            warnings = i.warnings,
        )
    }
}
