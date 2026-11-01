package com.kalai73.lifemapp.widgets.core.model

import kotlinx.serialization.Serializable

/** What the local cache holds: the last snapshot plus sync bookkeeping. Widgets render only from this. */
@Serializable
data class CachedState(
    val snapshot: Snapshot? = null,
    /** Epoch millis of the last successful sync */
    val lastSuccessAt: Long = 0,
    /** Epoch millis of the last sync attempt (successful or not) */
    val lastAttemptAt: Long = 0,
    val lastError: SyncError? = null,
    /** A sync is running (drives the reload spinner); see [syncStartedAt] */
    val syncing: Boolean = false,
    /** Epoch millis the running sync started; a flag older than [SYNC_TIMEOUT_MS] is treated as finished */
    val syncStartedAt: Long = 0,
    /** Tasks checked in a widget whose completion hasn't been confirmed by the server yet: id → epoch millis */
    val pendingCompletions: Map<String, Long> = emptyMap(),
    /** A task completion failed after retries; the widget shows a notice once */
    val completionFailed: Boolean = false,
    /** Light / dark choice of this device for the app and the widgets (not synced with the web) */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    /** Whether a sync is really running: a killed or cancelled worker can't leave the spinner on for good. */
    fun isSyncing(now: Long): Boolean = syncing && now - syncStartedAt < SYNC_TIMEOUT_MS

    companion object {
        const val SYNC_TIMEOUT_MS = 90_000L
    }
}

@Serializable
enum class SyncError { NETWORK, UNAUTHORIZED, SERVER, LOCKED_DEVICE }

/** Session state the app and widgets react to. */
enum class AuthState { SIGNED_OUT, SIGNED_IN }

/** End-to-end encryption state on this device. */
enum class EncryptionState {
    /** The account doesn't use end-to-end encryption */
    DISABLED,
    /** Encrypted account, data key not on this device yet: titles render as "Locked" */
    LOCKED,
    UNLOCKED,
}

/** Color schemes of the web app (lm_user_settings.color_scheme). */
enum class ColorSchemeName(val id: String) {
    DEFAULT("default"), TEAL("teal"), OCEAN("ocean"), SUNSET("sunset"), FOREST("forest"), ROSE("rose"), MIDNIGHT("midnight");

    companion object {
        fun from(id: String?): ColorSchemeName = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** UI styles of the web app (lm_user_settings.ui_style, resolved by effectiveUiStyle on the server). */
enum class UiStyle(val id: String) {
    CLASSIC("classic"), PREDATOR("predator");

    companion object {
        /** Null or unknown values are Predator, the web default (styleguide §2). */
        fun from(id: String?): UiStyle = if (id == CLASSIC.id) CLASSIC else PREDATOR
    }
}

/** Light or dark appearance of the app and the widgets, chosen on this device. */
@Serializable
enum class ThemeMode {
    /** Follow the system dark theme setting */
    SYSTEM,
    LIGHT,
    DARK;

    /** Whether to draw dark, given the system setting. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}
