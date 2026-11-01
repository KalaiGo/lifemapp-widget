package com.kalai73.lifemapp.widgets.core.weblauncher

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import kotlinx.coroutines.withTimeoutOrNull

/**
 * LIFE MAPP web routes the widgets open. Paths keep their #fragment for the Today page's sections; the
 * `?panel=true` params open the side panels, as the web's main layout reads them (main-layout.tsx handleUrlSync),
 * which is also what the hero's Journal and Budget buttons do.
 */
object WebRoutes {
    const val TODAY = "/today"
    /** Pre-sign-in pages the sign-in screen links to */
    const val SIGN_UP = "/signup"
    const val FORGOT_PASSWORD = "/forgot-password"
    const val EVENTS = "/today#section-events"
    const val TASKS = "/today#section-tasks"
    /** The tasks panel, where new tasks are added */
    const val NEW_TASK = "/today?tasks=true"
    const val MAIL = "/today#section-gmail-labels"
    const val QUICK_LINKS = "/today?links=true"
    const val JOURNAL = "/today?journal=true"
    const val BUDGET = "/today?budget=true"
    const val SETTINGS = "/today?settings=true"
    /** The Integrity domain card (integrity-domain-with-widgets.tsx) */
    const val INTEGRITY = "/today#integrity-section"
    /** Its Warnings bar, as the digest email links it */
    const val WARNINGS = "/today#section-warnings"

    /** A life domain card on the web's Today page (its element id is domain-<id>) */
    fun domain(domainId: String) = "/today#domain-$domainId"

    /**
     * Fragment for [TODAY] that starts the web's own mic, for devices without a speech recognizer (the widget
     * otherwise handles voice input itself, see VoiceInputActivity).
     */
    const val VOICE_RECORD_FRAGMENT = "voice-record"
}

object WebLauncher {
    /** A browser session made by a handoff is reused this long before the next handoff */
    const val HANDOFF_REUSE_MS = 12 * 60 * 60 * 1000L
    private const val HANDOFF_TIMEOUT_MS = 4_000L

    const val EXTRA_PATH = "com.kalai73.lifemapp.widgets.extra.PATH"
    const val EXTRA_URL = "com.kalai73.lifemapp.widgets.extra.URL"
    const val EXTRA_FRAGMENT = "com.kalai73.lifemapp.widgets.extra.FRAGMENT"

    /**
     * Intent for a widget tap that opens a LIFE MAPP route, signed in. [fragment] (already encoded) is added to
     * the URL that opens rather than to the handoff's redirect, so it stays in the browser; /auth/exchange carries
     * it over to the route.
     */
    fun routeIntent(context: Context, path: String, fragment: String? = null): Intent =
        Intent(context, WebLaunchActivity::class.java)
            .putExtra(EXTRA_PATH, path)
            .apply { if (fragment != null) putExtra(EXTRA_FRAGMENT, fragment) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            // Distinct data so PendingIntents for different routes don't collapse into one
            .setData(Uri.parse("lifemapp-route:$path"))

    /**
     * Intent for an external link (quick links, provider event pages, webmail): plain ACTION_VIEW, so
     * installed apps (WhatsApp, Spotify, Gmail, …) handle their own links.
     */
    fun externalIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(url)))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Quick links may be saved without a scheme ("example.com"); app schemes are kept as they are. */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    }

    /**
     * The URL to open for [path]: a one-time sign-in link when the browser may not have a session yet,
     * else the plain route. Falls back to the plain route when offline or slow.
     */
    suspend fun resolve(context: Context, path: String, now: Long = System.currentTimeMillis()): Uri {
        val graph = LifeMappGraph.get(context)
        val plain = Uri.parse(graph.config.webUrl + path)
        val secrets = runCatching { graph.secrets.read() }.getOrNull() ?: return plain
        if (secrets.session == null) return plain
        if (now - secrets.lastWebHandoffAt < HANDOFF_REUSE_MS) return plain
        val handoff = withTimeoutOrNull(HANDOFF_TIMEOUT_MS) {
            runCatching { graph.api.sessionHandoff(path) }.getOrNull()
        } ?: return plain
        graph.secrets.update { it.copy(lastWebHandoffAt = now) }
        return Uri.parse(handoff)
    }

    /** Forces a new handoff on the next open (e.g. the user signed out on the web). */
    suspend fun resetHandoff(context: Context) {
        LifeMappGraph.get(context).secrets.update { it.copy(lastWebHandoffAt = 0) }
    }

    fun openCustomTab(context: Context, uri: Uri, scheme: ColorSchemeName) {
        val light = LifeMappColors.lightScheme(scheme)
        val dark = LifeMappColors.darkScheme(scheme)
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder().setToolbarColor(light.background.toArgb()).build()
            )
            .setColorSchemeParams(
                CustomTabsIntent.COLOR_SCHEME_DARK,
                CustomTabColorSchemeParams.Builder().setToolbarColor(dark.background.toArgb()).build(),
            )
            .setColorScheme(
                if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
                    CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_SYSTEM
            )
            .build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            intent.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            // No Custom Tabs browser: any browser will do
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun routePendingIntent(context: Context, path: String): PendingIntent = PendingIntent.getActivity(
        context, path.hashCode(), routeIntent(context, path),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
