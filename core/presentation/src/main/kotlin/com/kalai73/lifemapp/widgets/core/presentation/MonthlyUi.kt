package com.kalai73.lifemapp.widgets.core.presentation

import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.ThemeMode
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import java.time.LocalDate

/**
 * Everything the Monthly screen draws, decrypted and formatted. Built fresh for every render; never stored.
 */
data class MonthlyUi(
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
    val averageRating: Double?,
    val weather: WeatherUi?,
    /** Domain 01: Integrity (with warnings and reminders) */
    val integrity: IntegrityUi?,
    /** Domain 10: Trayectory (with embedded Tenacity pos 05) */
    val trayectory: TrayectoryUi?,
    /** Domains 11–19: visible monthly domains (filtered by hidden status) */
    val monthlyDomains: List<IntegrityUi> = emptyList(),
    /** Monthly domains hidden on this device (for unhiding / nav overlay) */
    val hiddenMonthlyDomains: List<IntegrityUi> = emptyList(),
    /** 10-domain scoring section (positions 10–19); null without domains */
    val scoring: MonthlyScoringUi? = null,
    /** Local time of the last successful sync, null if never */
    val updatedAt: String?,
    /** No successful sync for over two hours */
    val stale: Boolean,
    val syncing: Boolean,
    val error: SyncError?,
) {
    companion object {
        val SignedOut = MonthlyUi(
            authState = AuthState.SIGNED_OUT,
            encryption = EncryptionState.DISABLED,
            hasData = false,
            colorScheme = ColorSchemeName.DEFAULT,
            uiStyle = UiStyle.PREDATOR,
            themeMode = ThemeMode.SYSTEM,
            language = "en",
            username = null,
            todayLabel = "",
            today = LocalDate.now(),
            eventsCount = 0,
            tasksCount = 0,
            mailCount = 0,
            mailConnected = false,
            mailUrl = null,
            averageRating = null,
            weather = null,
            integrity = null,
            trayectory = null,
            monthlyDomains = emptyList(),
            hiddenMonthlyDomains = emptyList(),
            scoring = null,
            updatedAt = null,
            stale = false,
            syncing = false,
            error = null,
        )
    }
}

/** Trayectory domain card (position 10) with Tenacity (position 5) embedded in preActionContent. */
data class TrayectoryUi(
    val domain: IntegrityUi,
    val embeddedTenacity: IntegrityUi?,
)

/** The Monthly screen's scoring area (positions 10–19). */
data class MonthlyScoringUi(
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
    /** Hidable domains (11–19, exempting Trayectory 10), and whether every one of them is hidden */
    val allHidableHidden: Boolean get() = domains.any { !it.unhidable } && domains.filterNot { it.unhidable }.all { it.hidden }
}

