package com.kalai73.lifemapp.widgets.core.data.presentation

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.LifeMappJson
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.MailTag
import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.ScoringDomain
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
    /** The user's events card settings from the web (days to show, event cap), for the app's Today screen */
    val eventsDaysAhead: Int = EventLimits.DEFAULT_DAYS_AHEAD,
    val eventsMaxEvents: Int = EventLimits.DEFAULT_MAX_EVENTS,
    val tasks: List<TaskUi>,
    val shortcuts: List<ShortcutUi>,
    /** The Integrity domain card, null when the user has none */
    val integrity: IntegrityUi?,
    /**
     * The other Today domains (positions 2–5) the Today page shows (hidden ones left out, as on the web), in order:
     * the app's Today screen shows them all, the Dashboard widget only Tenacity (5), at the bottom of its list
     */
    val todayDomains: List<IntegrityUi> = emptyList(),
    /** The Today screen's scoring section (the web's ScoringCircles); null without domains */
    val scoring: ScoringUi? = null,
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

/** The days the web's events card shows (calendar-events-card.tsx), from the user's settings. */
data class LimitedEvents(val days: List<EventDayUi>, /** Events left out by the cap ("+N more") */ val more: Int)

object EventLimits {
    /** The web's defaults (user-settings-store.ts) */
    const val DEFAULT_DAYS_AHEAD = 3
    const val DEFAULT_MAX_EVENTS = 7
    /** The web keeps the latest 3 of today's past events */
    const val MAX_PAST_TODAY = 3

    /**
     * The web's events card: the next [daysAhead] days (the snapshot holds 7 at most), only the latest
     * [MAX_PAST_TODAY] of today's past events, then the first [maxEvents] in order. Today is always listed.
     */
    fun apply(days: List<EventDayUi>, today: LocalDate, daysAhead: Int, maxEvents: Int): LimitedEvents {
        val last = today.plusDays((daysAhead.coerceAtLeast(1) - 1).toLong())
        val inRange = days.filter { !it.date.isAfter(last) }.map { day ->
            if (day.kind != DayKind.TODAY) day
            else {
                // Events come in start order: the earliest past ones go
                val past = day.events.filter { it.past }
                val dropped = past.take((past.size - MAX_PAST_TODAY).coerceAtLeast(0)).toSet()
                day.copy(events = day.events.filterNot { it in dropped })
            }
        }
        var left = maxEvents.coerceAtLeast(1)
        val total = inRange.sumOf { it.events.size }
        val capped = inRange.map { day -> day.copy(events = day.events.take(left).also { left -= it.size }) }
            .filter { it.events.isNotEmpty() || it.kind == DayKind.TODAY }
        val withToday = if (capped.firstOrNull()?.kind == DayKind.TODAY) capped
        else listOf(EventDayUi(today, DayKind.TODAY, days.firstOrNull { it.kind == DayKind.TODAY }?.label ?: "", emptyList())) + capped
        return LimitedEvents(withToday, more = total - capped.sumOf { it.events.size })
    }
}

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
    /** A timed event of today that has ended (or started, without an end): the web's isTodayPastEvent */
    val past: Boolean = false,
    /** The snapshot's start (ISO instant or YYYY-MM-DD): with [id], tells repeated instances apart */
    val start: String = "",
    /** The event modal's "When": the full date, or the date range of a multi-day all-day event */
    val dateLabel: String = "",
    val location: String? = null,
    val description: String? = null,
    /** "confirmed", "tentative", "cancelled", or null */
    val status: String? = null,
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
    /** HTML; null when there is none, or it is encrypted and this device is locked */
    val description: String? = null,
    /** The task has an encrypted description this device can't read (locked) */
    val descriptionLocked: Boolean = false,
    /** Local due date; [dueLabel] leaves today's date out */
    val dueDate: LocalDate? = null,
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

/** The web's ScoringCircles on Today: the domains of positions 1–5, the average and the last scoring. */
data class ScoringUi(
    val domains: List<ScoreDomainUi>,
    /** Days since the last scoring, null when never scored */
    val daysSinceScored: Int?,
    /** Encryption on and this device locked: the average shows a lock and rating is disabled */
    val locked: Boolean,
    /** The hidden state is known (read from the server at least once); until then nothing can be toggled */
    val ready: Boolean,
) {
    /** 0 scored today, 1 within two weeks, 2 within three, 3 later or never (scoring-circles.tsx) */
    val urgency: Int get() = when (val d = daysSinceScored) {
        null -> 3
        0 -> 0
        in 1..14 -> 1
        in 15..21 -> 2
        else -> 3
    }
    val scoringDue: Boolean get() = daysSinceScored == null || daysSinceScored >= 22
    /** Domains that can be hidden, and whether every one of them is */
    val allHidableHidden: Boolean get() = domains.any { !it.unhidable } && domains.filterNot { it.unhidable }.all { it.hidden }
}

data class ScoreDomainUi(
    val id: String,
    val position: Int,
    /** Null when encrypted and locked */
    val name: String?,
    /** 0–10 (0 unrated), null when encrypted and locked */
    val rating: Int?,
    val hidden: Boolean,
    val unhidable: Boolean,
    /**
     * The domain's color name (one of the 20 Tailwind families): its custom color, else the score's
     * (resolveDomainColorName); null falls back to the scheme accent
     */
    val colorName: String?,
)

object WidgetPresenter {
    const val STALE_AFTER_MS = 2 * 60 * 60 * 1000L

    /** domain-colors.ts SCORE_TO_COLOR: the color family of a 1–10 score */
    private val SCORE_TO_COLOR = listOf("red", "red", "orange", "orange", "amber", "yellow", "lime", "green", "emerald", "emerald")

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
                description = (t.description ?: key?.let { EncryptedField.decrypt(t.encryptedDescription, it) })?.takeIf { it.isNotBlank() },
                descriptionLocked = key == null && !t.encryptedDescription.isNullOrBlank(),
                dueDate = due,
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
                            past = !e.allDay && e.date == today.toString() &&
                                runCatching { Instant.parse(e.end ?: e.start).toEpochMilli() < now }.getOrDefault(false),
                            start = e.start,
                            dateLabel = eventDateLabel(e, zone, language),
                            location = e.location?.takeIf { it.isNotBlank() },
                            description = e.description?.takeIf { it.isNotBlank() },
                            status = e.status?.takeIf { it.isNotBlank() },
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

        val integrityUi = snapshot.integrity?.let { integrity(it, key) }
        val todayDomainsUi = snapshot.todayDomains.map { integrity(it, key) }
        val scoring = scoring(state.scoring, listOfNotNull(integrityUi) + todayDomainsUi, encryption, today, deviceZone)

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
            eventsDaysAhead = state.scoring?.eventsDaysAhead ?: EventLimits.DEFAULT_DAYS_AHEAD,
            eventsMaxEvents = state.scoring?.eventsMaxEvents ?: EventLimits.DEFAULT_MAX_EVENTS,
            tasks = tasks,
            shortcuts = shortcuts,
            integrity = integrityUi,
            todayDomains = todayDomainsUi.filterNot { d -> scoring?.domains?.any { it.id == d.domainId && it.hidden } == true },
            scoring = scoring,
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
    /**
     * The event modal's date, as the web's formatEventFullDate: the start day in the user's zone, and for an
     * all-day event lasting several days its last day too (the end date is exclusive).
     */
    internal fun eventDateLabel(e: com.kalai73.lifemapp.widgets.core.model.Event, zone: ZoneId, language: String): String = runCatching {
        val start = if (e.allDay) LocalDate.parse(e.start.take(10)) else Instant.parse(e.start).atZone(zone).toLocalDate()
        val last = if (e.allDay) e.end?.let { LocalDate.parse(it.take(10)).minusDays(1) } else null
        val first = DateLabels.dayAndDate(start, DateLabels.DATE_FORMAT, language)
        if (last != null && last.isAfter(start)) "$first – ${DateLabels.dayAndDate(last, DateLabels.DATE_FORMAT, language)}" else first
    }.getOrDefault(e.date)

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

    /**
     * The scoring section from the Today domain cards and the scoring state: a domain is hidden when listed in
     * hiddenDomainIds, or (never set) whenever it can be hidden. Before the state is first read nothing is hidden.
     */
    internal fun scoring(state: Scoring?, cards: List<IntegrityUi>, encryption: EncryptionState, today: LocalDate, zone: ZoneId): ScoringUi? {
        if (cards.isEmpty()) return null
        val domains = cards.sortedBy { it.position }.map { card ->
            val stored = state?.domains?.firstOrNull { it.id == card.domainId }
            val unhidable = ScoringDomain.isUnhidable(card.position, stored?.name ?: card.name)
            val hidden = state != null && !unhidable && (state.hiddenDomainIds?.contains(card.domainId) ?: true)
            val custom = stored?.color?.takeIf { it in DOMAIN_COLOR_NAMES }
            val rating = card.rating
            ScoreDomainUi(
                id = card.domainId,
                position = card.position,
                name = card.name,
                rating = rating,
                hidden = hidden,
                unhidable = unhidable,
                colorName = custom ?: rating?.takeIf { it > 0 }?.let { SCORE_TO_COLOR[(it - 1).coerceIn(0, 9)] },
            )
        }
        return ScoringUi(
            domains = domains,
            daysSinceScored = state?.lastCompleted?.let { daysSince(it, today, zone) },
            locked = encryption == EncryptionState.LOCKED,
            ready = state != null,
        )
    }

    /** Days from the stored scoring time (an ISO instant, or "YYYY-MM" from older rows) to [today]. */
    internal fun daysSince(value: String, today: LocalDate, zone: ZoneId): Int? {
        val date = if (value.length > 7) {
            runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
                ?: runCatching { Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
                ?: runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
        } else {
            runCatching { LocalDate.parse("$value-01") }.getOrNull()
        } ?: return null
        return java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt().coerceAtLeast(0)
    }

    /** The 20 Tailwind color families a domain can take (domain-colors.ts DOMAIN_COLOR_NAMES) */
    val DOMAIN_COLOR_NAMES = setOf(
        "red", "orange", "amber", "yellow", "lime", "green", "emerald", "teal", "cyan", "sky",
        "blue", "indigo", "violet", "purple", "fuchsia", "pink", "rose", "stone", "slate", "zinc",
    )

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
