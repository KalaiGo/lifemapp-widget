package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.UiStyle

val LocalPdPalette = staticCompositionLocalOf { PredatorColors.palette(ColorSchemeName.DEFAULT, false) }
val LocalUiStyle = staticCompositionLocalOf { UiStyle.PREDATOR }

private val PredatorShapes = Shapes(
    extraSmall = CutCornerShape(0.dp),
    small = CutCornerShape(0.dp),
    medium = CutCornerShape(0.dp),
    large = CutCornerShape(0.dp),
    extraLarge = CutCornerShape(0.dp),
)

/** Theme of the app's own screens (sign-in, unlock, settings), in the user's web color scheme and UI style. */
@Composable
fun LifeMappAppTheme(
    scheme: ColorSchemeName = ColorSchemeName.DEFAULT,
    style: UiStyle = UiStyle.PREDATOR,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val pdPalette = PredatorColors.palette(scheme, dark)
    val colors = if (style == UiStyle.PREDATOR) {
        PredatorColors.scheme(pdPalette)
    } else {
        if (dark) LifeMappColors.darkScheme(scheme) else LifeMappColors.lightScheme(scheme)
    }
    val shapes = if (style == UiStyle.PREDATOR) PredatorShapes else MaterialTheme.shapes

    CompositionLocalProvider(
        LocalPdPalette provides pdPalette,
        LocalUiStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = shapes,
            content = content,
        )
    }
}

