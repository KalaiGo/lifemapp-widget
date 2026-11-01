package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors.hsl
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.ThemeMode

/**
 * Predator tokens (lifeMappMob docs/predator-styleguide.md §3, values as in lifeMappMob constants/predator.ts).
 * RemoteViews has no color-mix() or alpha-tinted masks, so every derived value is precomputed and opaque.
 */
data class PdPalette(
    val isDark: Boolean,
    /** Page canvas and panels share one color; panels separate by their edge. */
    val panel: Color,
    val raised: Color,
    /** Empty meter segments, icon wells. */
    val well: Color,
    val edge: Color,
    val edge2: Color,
    val text: Color,
    val dim: Color,
    /** Decorative glyphs only, never text. */
    val faint: Color,
    /** Shapes only (fills, rules, corner accents), never a text color. */
    val accent: Color,
    val accentHi: Color,
    val accentLo: Color,
    /** Accent at 13% over the panel. */
    val accentTint: Color,
    /** Accent text and icons on a surface (≥ 4.5:1 on the panel). */
    val ink: Color,
    /** Text on the accent fill. */
    val on: Color,
    /** Text on a shape filled with the ink color. */
    val onInk: Color,
    val destructive: Color,
    /** Task rows rest at raised 40% over the panel (styleguide §7). */
    val row: Color,
)

object PredatorColors {
    private val OnDark = Color(0xFF1A0A02)
    /** The web's red reads on the dark panel; on the light one it's 3.4:1, so light mode uses a deeper red. */
    private val Destructive = hsl(0f, 84.2f, 60.2f)
    private val DestructiveLight = hsl(0f, 72f, 45f)

    private class Accent(val fill: Color, val ink: Color, val on: Color, val hi: Color? = null, val lo: Color? = null)

    /** §3.3: the default scheme is Predator orange; other schemes keep their primary with contrast-checked inks. */
    private fun accent(scheme: ColorSchemeName, dark: Boolean): Accent = when (scheme) {
        ColorSchemeName.DEFAULT -> if (dark) {
            Accent(Color(0xFFFF6A13), Color(0xFFFF8A3D), Color(0xFF120A04), Color(0xFFFF9260), Color(0xFFB74A0A))
        } else {
            Accent(Color(0xFFFF6A13), Color(0xFFC2410C), OnDark, Color(0xFFFF9260), Color(0xFFB74A0A))
        }
        ColorSchemeName.TEAL, ColorSchemeName.OCEAN ->
            if (dark) Accent(hsl(173f, 70f, 52f), hsl(173f, 70f, 52f), OnDark)
            else Accent(hsl(175f, 84f, 32f), hsl(175f, 84f, 27f), OnDark)
        ColorSchemeName.SUNSET ->
            if (dark) Accent(hsl(25f, 95f, 62f), hsl(25f, 95f, 62f), OnDark)
            else Accent(hsl(25f, 95f, 53f), hsl(25f, 95f, 38f), OnDark)
        ColorSchemeName.FOREST ->
            if (dark) Accent(hsl(142f, 65f, 62f), hsl(142f, 65f, 62f), OnDark)
            else Accent(hsl(142f, 65f, 57f), hsl(142f, 65f, 31f), OnDark)
        ColorSchemeName.ROSE ->
            if (dark) Accent(hsl(351f, 95f, 75f), hsl(351f, 95f, 75f), OnDark)
            else Accent(hsl(351f, 95f, 71f), hsl(351f, 95f, 46f), OnDark)
        ColorSchemeName.MIDNIGHT ->
            if (dark) Accent(hsl(239f, 84f, 72f), hsl(239f, 84f, 72f), OnDark)
            else Accent(hsl(239f, 84f, 67f), hsl(239f, 84f, 65.5f), Color.White)
    }

    fun palette(scheme: ColorSchemeName, dark: Boolean): PdPalette {
        val a = accent(scheme, dark)
        val panel = if (dark) Color(0xFF171513) else Color(0xFFFAF8F5)
        val raised = if (dark) Color(0xFF1E1B18) else Color(0xFFF2EEE8)
        return PdPalette(
            isDark = dark,
            panel = panel,
            raised = raised,
            well = if (dark) Color(0xFF2A2521) else Color(0xFFE0D9D0),
            edge = if (dark) Color(0xFF3A322C) else Color(0xFFCDC3B8),
            edge2 = if (dark) Color(0xFF5A4A3E) else Color(0xFFA3968A),
            text = if (dark) Color(0xFFF3EFEA) else Color(0xFF1B1714),
            dim = if (dark) Color(0xFFA39A92) else Color(0xFF5E554C),
            faint = if (dark) Color(0xFF6F675F) else Color(0xFF9B9085),
            accent = a.fill,
            accentHi = a.hi ?: mix(a.fill, Color.White, 0.24f),
            accentLo = a.lo ?: mix(a.fill, Color.Black, 0.22f),
            accentTint = mix(panel, a.fill, 0.13f),
            ink = a.ink,
            on = a.on,
            onInk = if (dark) OnDark else Color.White,
            destructive = if (dark) Destructive else DestructiveLight,
            row = mix(panel, raised, 0.4f),
        )
    }

    /**
     * Material roles for the shared widget code: `primary` is the ink (accent text and icons, like the web's
     * `--primary` inside a scope), containers are the accent tint, `secondary` is the accent fill.
     */
    fun scheme(p: PdPalette): ColorScheme {
        val base = if (p.isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = p.ink,
            onPrimary = p.onInk,
            primaryContainer = p.accentTint,
            onPrimaryContainer = p.ink,
            secondary = p.accent,
            onSecondary = p.on,
            secondaryContainer = p.accentTint,
            onSecondaryContainer = p.ink,
            tertiary = p.accentHi,
            onTertiary = p.on,
            background = p.panel,
            onBackground = p.text,
            surface = p.raised,
            onSurface = p.text,
            surfaceVariant = p.well,
            onSurfaceVariant = p.dim,
            outline = p.edge,
            outlineVariant = p.edge2,
            error = p.destructive,
            onError = Color.White,
        )
    }

    /** Day/night providers; a forced [mode] uses the same palette for both. */
    fun glance(scheme: ColorSchemeName, mode: ThemeMode = ThemeMode.SYSTEM): ColorProviders {
        val light = scheme(palette(scheme, dark = mode == ThemeMode.DARK))
        val dark = scheme(palette(scheme, dark = mode != ThemeMode.LIGHT))
        return androidx.glance.material3.ColorProviders(light = light, dark = dark)
    }

    /** sRGB mix of [b] into [a] (0 → a, 1 → b), always opaque. */
    fun mix(a: Color, b: Color, amount: Float): Color = Color(
        red = a.red + (b.red - a.red) * amount,
        green = a.green + (b.green - a.green) * amount,
        blue = a.blue + (b.blue - a.blue) * amount,
    )
}
