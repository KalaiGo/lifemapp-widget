package com.kalai73.lifemapp.widgets.core.data.presentation

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Port of lifeMappWeb `src/lib/calendar/date-format-core.ts`, so widget date labels read exactly
 * like the web widgets' ("We. 23/09/2026").
 */
object DateLabels {
    const val DEFAULT_DATE_FORMAT = "dd/MM/yyyy"

    /** The format the widgets and the app's Today screen always use, whatever the web setting: "Mi. 23.09.2026" */
    const val DATE_FORMAT = "dd.MM.yyyy"

    private val MONTHS_SHORT = mapOf(
        "en" to listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        "de" to listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"),
        "es" to listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"),
    )

    /** Index 0 = Sunday, as in JavaScript */
    private val DAYS_SHORT = mapOf(
        "en" to listOf("Su.", "Mo.", "Tu.", "We.", "Th.", "Fr.", "Sa."),
        "de" to listOf("So.", "Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa."),
        "es" to listOf("Do.", "Lu.", "Ma.", "Mi.", "Ju.", "Vi.", "Sá."),
    )

    fun baseLanguage(language: String?): String {
        val lang = (language ?: "en").take(2)
        return if (lang in MONTHS_SHORT) lang else "en"
    }

    /** Supported tokens: dd, MM, MMM, yyyy, d (first occurrence each, like the web's String.replace) */
    fun formatDate(date: LocalDate, format: String?, language: String?): String {
        val pattern = format?.takeIf { it.isNotBlank() } ?: DEFAULT_DATE_FORMAT
        val monthName = MONTHS_SHORT.getValue(baseLanguage(language))[date.monthValue - 1]
        return pattern
            .replaceFirst("dd", date.dayOfMonth.toString().padStart(2, '0'))
            .replaceFirst(Regex("\\bd\\b"), date.dayOfMonth.toString())
            .replaceFirst("MMM", monthName)
            .replaceFirst("MM", date.monthValue.toString().padStart(2, '0'))
            .replaceFirst("yyyy", date.year.toString())
    }

    fun shortDayName(date: LocalDate, language: String?): String =
        DAYS_SHORT.getValue(baseLanguage(language))[date.dayOfWeek.value % 7]

    fun dayAndDate(date: LocalDate, format: String?, language: String?): String =
        "${shortDayName(date, language)} ${formatDate(date, format, language)}"

    fun is12h(timeFormat: String?) = timeFormat == "12" || timeFormat == "12h"

    fun time(instant: Instant, zone: ZoneId, timeFormat: String?): String =
        DateTimeFormatter.ofPattern(if (is12h(timeFormat)) "h:mm a" else "HH:mm").withZone(zone).format(instant)

    /** A local "14:30" as the user's time format shows it; the text as is when it doesn't parse. */
    fun time(local: String, timeFormat: String?): String =
        runCatching { DateTimeFormatter.ofPattern(if (is12h(timeFormat)) "h:mm a" else "HH:mm").format(LocalTime.parse(local)) }
            .getOrDefault(local)
}
