package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.ThemeMode
import kotlin.math.abs

/**
 * The web app's color schemes (lifeMappWeb src/styles/globals.css, `.theme-*` and `.dark.theme-*`)
 * as Material 3 color schemes. Backgrounds follow the Today page's hero (#f9f5eb / #121110) and its
 * cards (white / zinc-800).
 */
object LifeMappColors {
    /** CSS `hsl(h s% l%)` → Color */
    fun hsl(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
        val sat = s / 100f
        val light = l / 100f
        val c = (1 - abs(2 * light - 1)) * sat
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = light - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m, alpha)
    }

    private data class Accent(val primary: Color, val accent: Color, val onAccent: Color)

    private fun light(scheme: ColorSchemeName): Accent = when (scheme) {
        ColorSchemeName.DEFAULT -> Accent(hsl(213f, 88f, 66f), hsl(213f, 88f, 95f), hsl(213f, 88f, 35f))
        ColorSchemeName.TEAL, ColorSchemeName.OCEAN -> Accent(hsl(175f, 84f, 32f), hsl(175f, 60f, 94f), hsl(175f, 84f, 24f))
        ColorSchemeName.SUNSET -> Accent(hsl(25f, 95f, 53f), hsl(25f, 90f, 95f), hsl(25f, 95f, 35f))
        ColorSchemeName.FOREST -> Accent(hsl(142f, 65f, 57f), hsl(142f, 50f, 94f), hsl(142f, 65f, 30f))
        ColorSchemeName.ROSE -> Accent(hsl(351f, 95f, 71f), hsl(351f, 60f, 95f), hsl(351f, 95f, 45f))
        ColorSchemeName.MIDNIGHT -> Accent(hsl(239f, 84f, 67f), hsl(239f, 70f, 95f), hsl(239f, 84f, 40f))
    }

    private fun dark(scheme: ColorSchemeName): Accent = when (scheme) {
        // The web's default dark primary is near-white; the brand blue reads better on a home screen
        ColorSchemeName.DEFAULT -> Accent(hsl(213f, 88f, 70f), hsl(213f, 40f, 16f), hsl(213f, 88f, 78f))
        ColorSchemeName.TEAL, ColorSchemeName.OCEAN -> Accent(hsl(173f, 70f, 52f), hsl(175f, 40f, 15f), hsl(173f, 70f, 70f))
        ColorSchemeName.SUNSET -> Accent(hsl(25f, 95f, 62f), hsl(25f, 45f, 15f), hsl(25f, 95f, 75f))
        ColorSchemeName.FOREST -> Accent(hsl(142f, 65f, 62f), hsl(142f, 40f, 14f), hsl(142f, 65f, 65f))
        ColorSchemeName.ROSE -> Accent(hsl(351f, 95f, 75f), hsl(351f, 40f, 15f), hsl(351f, 95f, 75f))
        ColorSchemeName.MIDNIGHT -> Accent(hsl(239f, 84f, 72f), hsl(239f, 40f, 16f), hsl(239f, 84f, 75f))
    }

    private val Destructive = hsl(0f, 84.2f, 60.2f)
    /** ≥ 4.5:1 on the light background, for overdue dates and error text */
    private val DestructiveLight = hsl(0f, 72f, 45f)

    fun lightScheme(scheme: ColorSchemeName): ColorScheme {
        val a = light(scheme)
        return lightColorScheme(
            primary = a.primary,
            onPrimary = Color.White,
            primaryContainer = a.accent,
            onPrimaryContainer = a.onAccent,
            secondary = a.onAccent,
            onSecondary = Color.White,
            background = Color(0xFFF9F5EB),
            onBackground = hsl(0f, 0f, 3.9f),
            surface = Color.White,
            onSurface = hsl(0f, 0f, 3.9f),
            surfaceVariant = hsl(0f, 0f, 96.1f),
            onSurfaceVariant = hsl(0f, 0f, 45.1f),
            outline = hsl(0f, 0f, 89.8f),
            error = DestructiveLight,
            onError = Color.White,
        )
    }

    fun darkScheme(scheme: ColorSchemeName): ColorScheme {
        val a = dark(scheme)
        return darkColorScheme(
            primary = a.primary,
            onPrimary = Color.White,
            primaryContainer = a.accent,
            onPrimaryContainer = a.onAccent,
            secondary = a.onAccent,
            onSecondary = Color.Black,
            background = Color(0xFF121110),
            onBackground = hsl(0f, 0f, 98f),
            surface = Color(0xFF27272A),
            onSurface = hsl(0f, 0f, 98f),
            surfaceVariant = hsl(0f, 0f, 18f),
            onSurfaceVariant = hsl(0f, 0f, 63.9f),
            outline = hsl(240f, 4f, 22f),
            error = Destructive,
            onError = Color.White,
        )
    }

    /** Glance color providers that follow the system light/dark mode, or a forced [mode]. */
    fun glance(scheme: ColorSchemeName, mode: ThemeMode = ThemeMode.SYSTEM): ColorProviders =
        androidx.glance.material3.ColorProviders(
            light = if (mode == ThemeMode.DARK) darkScheme(scheme) else lightScheme(scheme),
            dark = if (mode == ThemeMode.LIGHT) lightScheme(scheme) else darkScheme(scheme),
        )

    /** Parses "#rrggbb" / "#rgb" from the web (calendar, list and quick-link colors); null if invalid. */
    fun parseHex(value: String?): Color? {
        val hex = value?.trim()?.removePrefix("#") ?: return null
        val full = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("")
            6, 8 -> hex
            else -> return null
        }
        val argb = full.toLongOrNull(16) ?: return null
        return if (full.length == 6) Color(0xFF000000 or argb) else Color(((argb and 0xFF) shl 24) or (argb ushr 8))
    }

    /** Priority colors of the task widget (Todoist-style 4 = P1); dark mode uses the lighter 400 shades. */
    fun priority(priority: Int, dark: Boolean = false): Color? = when (priority) {
        4 -> if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
        3 -> if (dark) Color(0xFFFB923C) else Color(0xFFEA580C)
        2 -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        else -> null
    }
}
