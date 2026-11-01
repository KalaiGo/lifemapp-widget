package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName

/** Theme of the app's own screens (sign-in, unlock, settings), in the user's web color scheme. */
@Composable
fun LifeMappAppTheme(scheme: ColorSchemeName = ColorSchemeName.DEFAULT, content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) LifeMappColors.darkScheme(scheme) else LifeMappColors.lightScheme(scheme)
    MaterialTheme(colorScheme = colors, content = content)
}
