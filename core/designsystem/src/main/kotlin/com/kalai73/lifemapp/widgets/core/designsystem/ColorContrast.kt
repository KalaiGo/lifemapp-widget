package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * WCAG contrast helpers. User colors from the web (calendars, task lists, quick links) are picked for the web's
 * own surfaces and can vanish on a widget panel: a yellow calendar on the light panel, a navy list on the dark
 * one. Before they're used as text or icons they're pushed just far enough toward black or white to read.
 */
object ColorContrast {
    /** Body text and small labels (WCAG AA). */
    const val TEXT = 4.5f

    /** Icons, bars and other meaningful graphics (WCAG 1.4.11). */
    const val GRAPHIC = 3f

    /** WCAG contrast ratio of two opaque colors, 1…21. */
    fun ratio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /**
     * [color] made opaque over [background] and, if needed, darkened (light background) or lightened (dark
     * background) in small steps until it reaches [min]. Keeps the hue, changes as little as possible.
     */
    fun ensure(color: Color, background: Color, min: Float = TEXT): Color {
        val opaque = if (color.alpha < 1f) PredatorColors.mix(background, color.copy(alpha = 1f), color.alpha) else color
        if (ratio(opaque, background) >= min) return opaque
        val target = if (background.luminance() > 0.18f) Color.Black else Color.White
        var amount = 0.05f
        while (amount < 1f) {
            val candidate = PredatorColors.mix(opaque, target, amount)
            if (ratio(candidate, background) >= min) return candidate
            amount += 0.05f
        }
        return target
    }

    /** Black-ish or white text, whichever reads better on [fill]. */
    fun onFill(fill: Color, dark: Color = Color(0xFF1A1004), light: Color = Color.White): Color =
        if (ratio(dark, fill) >= ratio(light, fill)) dark else light
}
