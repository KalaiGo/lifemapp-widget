package com.kalai73.lifemapp.widgets.widget.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.kalai73.lifemapp.widgets.core.designsystem.ColorContrast
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.PredatorColors
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.ThemeMode
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import androidx.glance.color.ColorProvider as DayNight

/**
 * Every day/night color of the widget goes through here, so the device's theme choice (app settings) wins over
 * the system mode: [ThemeMode.SYSTEM] gives RemoteViews both variants, a forced mode gives only one.
 *
 * Also makes user colors from the web (calendars, task lists, quick links) readable on the widget's own
 * surface in each mode: [text] for labels, [graphic] for icons and bars.
 *
 * [lightSurface] / [darkSurface] are what those colors sit on: the row fill in Predator, the card in Classic.
 */
class WidgetColors(
    val mode: ThemeMode,
    private val lightSurface: Color,
    private val darkSurface: Color,
) {
    fun of(day: Color, night: Color): ColorProvider = when (mode) {
        ThemeMode.SYSTEM -> DayNight(day = day, night = night)
        ThemeMode.LIGHT -> ColorProvider(day)
        ThemeMode.DARK -> ColorProvider(night)
    }

    /** [color] as text (≥ 4.5:1); [over] > 0 when the text sits on the same color at that amount (a tag chip). */
    fun text(color: Color, over: Float = 0f): ColorProvider = adjusted(color, ColorContrast.TEXT, over)

    /** [color] as an icon, bar or checkbox (≥ 3:1). */
    fun graphic(color: Color): ColorProvider = adjusted(color, ColorContrast.GRAPHIC, 0f)

    /** [color] at [amount] over the surface, opaque: chip and tile backgrounds. */
    fun tint(color: Color, amount: Float): ColorProvider =
        of(PredatorColors.mix(lightSurface, color, amount), PredatorColors.mix(darkSurface, color, amount))

    /** The task priority color for each mode, null for no priority. */
    fun priority(priority: Int): ColorProvider? {
        val day = LifeMappColors.priority(priority, dark = false) ?: return null
        val night = LifeMappColors.priority(priority, dark = true) ?: return null
        return of(ColorContrast.ensure(day, lightSurface, ColorContrast.GRAPHIC), ColorContrast.ensure(night, darkSurface, ColorContrast.GRAPHIC))
    }

    private fun adjusted(color: Color, min: Float, over: Float): ColorProvider {
        val day = PredatorColors.mix(lightSurface, color, over)
        val night = PredatorColors.mix(darkSurface, color, over)
        return of(ColorContrast.ensure(color, day, min), ColorContrast.ensure(color, night, min))
    }

    companion object {
        fun of(style: UiStyle, scheme: ColorSchemeName, mode: ThemeMode): WidgetColors = if (style == UiStyle.PREDATOR) {
            WidgetColors(mode, PredatorColors.palette(scheme, dark = false).row, PredatorColors.palette(scheme, dark = true).row)
        } else {
            WidgetColors(mode, LifeMappColors.lightScheme(scheme).background, LifeMappColors.darkScheme(scheme).background)
        }
    }
}

/** Set by LifeMappWidgetTheme. */
val LocalWidgetColors = staticCompositionLocalOf { WidgetColors.of(UiStyle.CLASSIC, ColorSchemeName.DEFAULT, ThemeMode.SYSTEM) }
