package com.kalai73.lifemapp.widgets.core.presentation.panel

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** lib/domains/reminder-dates.ts ReminderStatus (none is a null reminder here) */
enum class ReminderStatus { DUE, OVERDUE, UPCOMING, CREATED }

/** Port of the web's lib/domains/reminder-dates.ts and the recurrence presets of lib/tasks/recurrence.ts. */
object ReminderDates {
    /** RECURRENCE_PRESETS values, in picker order (labels are the app's strings) */
    val RECURRENCE_PRESETS = listOf(
        "FREQ=DAILY;INTERVAL=1",
        "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
        "FREQ=WEEKLY;INTERVAL=1",
        "FREQ=WEEKLY;INTERVAL=2",
        "FREQ=MONTHLY;INTERVAL=1",
        "FREQ=MONTHLY;INTERVAL=2",
        "FREQ=MONTHLY;INTERVAL=3",
        "FREQ=MONTHLY;INTERVAL=4",
        "FREQ=YEARLY;INTERVAL=1",
    )

    /** REMINDER_LEAD_DAYS_PRESETS: days before the date the reminder's task is created */
    val LEAD_DAYS_PRESETS = listOf(0, 1, 2, 3, 7, 14)

    /** The reminder's day in [zone]: an ISO timestamp, or a plain YYYY-MM-DD date. Null when unreadable. */
    fun parse(value: String?, zone: ZoneId): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
            ?: runCatching { Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }

    /** normalizeLeadDays: a whole number of days between 0 and 365 */
    fun normalizeLeadDays(days: Int?): Int = (days ?: 0).coerceIn(0, 365)

    /** getReminderStatus: created once its task exists, else by the reminder's day against [today]. */
    fun status(date: LocalDate, taskCreated: Boolean, today: LocalDate): ReminderStatus = when {
        taskCreated -> ReminderStatus.CREATED
        date == today -> ReminderStatus.DUE
        date.isBefore(today) -> ReminderStatus.OVERDUE
        else -> ReminderStatus.UPCOMING
    }

    /** The ReminderPicker's choice of a day: 9:00 local time on it, as an ISO instant (atNine). */
    fun atNine(day: LocalDate, zone: ZoneId): String = day.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toString()
}
