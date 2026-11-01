package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.text.FontFamily
import com.kalai73.lifemapp.widgets.core.model.UiStyle

/**
 * The web's two UI styles, as far as RemoteViews can draw them.
 * - Classic: rounded 24dp cards and the standard Material type.
 * - Predator (lifeMappMob docs/predator-styleguide.md): cut, never round. Panels are chamfered with accent
 *   corners, chips and buttons are parallelograms, scores and icon wells are hexagons, color bars lean.
 *   RemoteViews can't load Chakra Petch or track letters, so labels use the condensed system face in bold
 *   italic uppercase, and data captions use monospace. Body text (user content) keeps the default face.
 */
data class WidgetStyle(
    val style: UiStyle,
    val cornerRadius: Dp,
    val innerRadius: Dp,
    /** Short fixed labels (pd-label): titles, buttons, counters. */
    val labelFamily: FontFamily?,
    /** Data captions: dates, times, "Updated 10:42". */
    val captionFamily: FontFamily?,
    val italicLabels: Boolean,
    val uppercaseLabels: Boolean,
) {
    val isPredator: Boolean get() = style == UiStyle.PREDATOR

    /** For short, fixed labels only, never user content (styleguide §4). */
    fun label(text: String): String = if (uppercaseLabels) text.uppercase() else text

    companion object {
        /** The closest system face to Chakra Petch's narrow HUD look. */
        val Condensed = FontFamily("sans-serif-condensed")

        fun of(style: UiStyle): WidgetStyle = when (style) {
            UiStyle.PREDATOR -> WidgetStyle(
                style = style,
                cornerRadius = 0.dp,
                innerRadius = 0.dp,
                labelFamily = Condensed,
                captionFamily = FontFamily.Monospace,
                italicLabels = true,
                uppercaseLabels = true,
            )
            UiStyle.CLASSIC -> WidgetStyle(
                style = style,
                cornerRadius = 24.dp,
                innerRadius = 12.dp,
                labelFamily = null,
                captionFamily = null,
                italicLabels = false,
                uppercaseLabels = false,
            )
        }
    }
}
