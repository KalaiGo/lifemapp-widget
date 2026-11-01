package com.kalai73.lifemapp.widgets.widget.shortcuts

// Shortcut tiles as on the web's quick links card (components/quick-links/quick-links-card.tsx).

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.kalai73.lifemapp.widgets.core.data.presentation.ShortcutUi
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.PdHex
import com.kalai73.lifemapp.widgets.widget.common.numberStyle
import com.kalai73.lifemapp.widgets.widget.common.color
import com.kalai73.lifemapp.widgets.widget.common.openExternal
import com.kalai73.lifemapp.widgets.widget.common.textStyle

internal val TILE_WIDTH = 64.dp


@Composable
internal fun ShortcutTile(shortcut: ShortcutUi, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    val style = LocalWidgetStyle.current
    val tint = LifeMappColors.parseHex(shortcut.color)
    val drawable = LucideIcons.resolve(shortcut.icon)
    val pd = LocalPd.current
    if (pd != null) {
        // Hex icon well per link: edge in the link color, raised fill (styleguide §7)
        Column(
            modifier = modifier.padding(3.dp).clickable(openExternal(shortcut.url)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PdHex(38.dp, edge = tint?.let(::color) ?: pd.accent, fill = tint?.let { pd.tint(it, 0.12f) } ?: pd.raised) {
                when {
                    drawable != null -> Icon(drawable, shortcut.label, size = 18.dp, tint = tint?.let(::color) ?: pd.ink)
                    LucideIcons.isEmoji(shortcut.icon) -> Text(shortcut.icon, style = textStyle(size = 17.sp))
                    else -> Text(
                        (shortcut.label.firstOrNull() ?: '?').uppercase(),
                        style = numberStyle(16.sp, color = tint?.let(::color) ?: pd.ink),
                    )
                }
            }
            Box(GlanceModifier.height(2.dp)) {}
            // Labels are user content: no uppercase
            Text(shortcut.label, style = textStyle(size = 10.sp, color = pd.text).copy(textAlign = TextAlign.Center), maxLines = 1)
        }
        return
    }
    Column(
        modifier = modifier.padding(3.dp).clickable(openExternal(shortcut.url)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.size(40.dp)
                .background(tint?.copy(alpha = 0.16f)?.let(::color) ?: GlanceTheme.colors.primaryContainer)
                .cornerRadius(style.innerRadius),
            contentAlignment = Alignment.Center,
        ) {
            when {
                drawable != null -> Icon(drawable, shortcut.label, size = 22.dp, tint = tint?.let(::color) ?: GlanceTheme.colors.primary)
                LucideIcons.isEmoji(shortcut.icon) -> Text(shortcut.icon, style = textStyle(size = 20.sp))
                // Unknown icon: first letter of the label on the colored tile
                else -> Text(
                    (shortcut.label.firstOrNull() ?: '?').uppercase(),
                    style = textStyle(size = 18.sp, weight = FontWeight.Bold, color = tint?.let(::color) ?: GlanceTheme.colors.primary),
                )
            }
        }
        Box(GlanceModifier.height(2.dp)) {}
        Text(
            style.label(shortcut.label),
            style = textStyle(size = 10.sp).copy(textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}
