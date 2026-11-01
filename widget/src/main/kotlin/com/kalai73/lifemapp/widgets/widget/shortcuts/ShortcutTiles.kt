package com.kalai73.lifemapp.widgets.widget.shortcuts

// The Shortcuts card as on the web's Today page (components/domains/workspace-widget.tsx), simplified: a Mail
// section (connect prompt or the inbox with its unread count) and the quick links as tiles. No Drive section.

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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.action.Action
import androidx.glance.LocalContext
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.ConnectCard
import com.kalai73.lifemapp.widgets.widget.common.PdBar
import com.kalai73.lifemapp.widgets.widget.common.PdDivider
import com.kalai73.lifemapp.widgets.widget.common.PdWrapFrame
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.widget.common.openRoute
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
/** Minimum width of a numbered link row (Predator), for the column count. */
internal val LINK_ROW_WIDTH = 130.dp

/**
 * A section title inside the Shortcuts card ("MAIL", "LINKS 3"): a divider line above, small icon and label in
 * the accent, an optional count, and an optional action on the right ("Manage").
 */
@Composable
internal fun ShortcutsSubHeader(
    icon: Int,
    title: String,
    onClick: Action,
    count: Int? = null,
    actionLabel: String? = null,
    actionIcon: Int? = null,
    onAdd: Action? = null,
    chevron: Boolean = false,
) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val accent = pd?.accent ?: GlanceTheme.colors.primary
    val dim = pd?.dim ?: GlanceTheme.colors.onSurfaceVariant
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(pd?.edge ?: GlanceTheme.colors.outline)) {}
        Row(modifier = GlanceModifier.fillMaxWidth().clickable(onClick).padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (chevron) {
                // Decorative: the web card's collapse toggle (a widget section is always expanded)
                Icon(LucideIcons.CHEVRON_DOWN, null, size = 12.dp, tint = dim)
                Spacer(GlanceModifier.width(4.dp))
            }
            Icon(icon, null, size = 12.dp, tint = accent)
            Spacer(GlanceModifier.width(5.dp))
            Text(
                style.label(title),
                style = if (pd != null) labelStyle(size = 12.sp, color = accent) else textStyle(size = 12.sp, weight = FontWeight.Bold, color = accent),
                maxLines = 1,
            )
            if (count != null) {
                Spacer(GlanceModifier.width(6.dp))
                Text("$count", style = if (pd != null) captionStyle(size = 10.sp, color = dim) else textStyle(size = 11.sp, color = dim), maxLines = 1)
            }
            if (onAdd != null) {
                Spacer(GlanceModifier.width(2.dp))
                Box(GlanceModifier.size(20.dp).clickable(onAdd), contentAlignment = Alignment.Center) {
                    Icon(LucideIcons.ADD, LocalContext.current.getString(R.string.add_link), size = 12.dp, tint = dim)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (actionLabel != null) {
                if (actionIcon != null) {
                    Icon(actionIcon, null, size = 11.dp, tint = dim)
                    Spacer(GlanceModifier.width(4.dp))
                }
                Text(
                    style.label(actionLabel),
                    style = if (pd != null) labelStyle(size = 10.sp, color = dim) else textStyle(size = 11.sp, color = dim),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Mail: the connect prompt, or the inbox row with its unread count. */
@Composable
internal fun MailBody(ui: WidgetUi) {
    val context = LocalContext.current
    if (!ui.mailConnected) {
        ConnectCard(LucideIcons.MAIL, context.getString(R.string.mail_not_connected), context.getString(R.string.connect_mail), openRoute(context, WebRoutes.MAIL))
        return
    }
    val action = ui.mailUrl?.let(::openExternal) ?: openRoute(context, WebRoutes.MAIL)
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val label = context.getString(R.string.inbox)
    val unread = ui.mailCount > 0
    if (pd != null) {
        PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge, fill = pd.row, modifier = GlanceModifier.fillMaxWidth().clickable(action)) { frame ->
            Row(modifier = frame.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                PdBar(pd.accent, height = 18.dp)
                Spacer(GlanceModifier.width(6.dp))
                Icon(LucideIcons.INBOX, null, size = 14.dp, tint = if (unread) pd.accent else pd.dim)
                Spacer(GlanceModifier.width(6.dp))
                Text(label, style = textStyle(size = 13.sp, weight = FontWeight.Bold, color = pd.text), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                // Unread count: accent parallelogram when there is unread mail, framed panel otherwise
                PdWrapFrame(PdShape.PARA, edge = if (unread) pd.accentHi else pd.edge, fill = if (unread) pd.accent else pd.panel) { badge ->
                    Text("${ui.mailCount}", style = numberStyle(11.sp, color = if (unread) pd.on else pd.dim), modifier = badge.padding(horizontal = 9.dp, vertical = 1.dp))
                }
            }
        }
        return
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius)
            .clickable(action).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LucideIcons.INBOX, null, size = 16.dp, tint = if (unread) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant)
        Spacer(GlanceModifier.width(8.dp))
        Text(label, style = textStyle(size = 13.sp, weight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Text(
            "${ui.mailCount}",
            style = textStyle(size = 11.sp, weight = FontWeight.Bold, color = if (unread) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.background(if (unread) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant)
                .cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}


/**
 * A quick link as a numbered row (Predator, styleguide §7): number hex, slanted divider, the link icon in its
 * color and the label, in a chamfered row frame. Laid out two or more per line.
 */
@Composable
internal fun ShortcutRow(shortcut: ShortcutUi, number: Int, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    val pd = LocalPd.current ?: return ShortcutTile(shortcut, modifier)
    val tint = LifeMappColors.parseHex(shortcut.color)?.let(::color)
    val drawable = LucideIcons.resolve(shortcut.icon)
    Box(modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
        PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.row, fill = pd.row, modifier = GlanceModifier.fillMaxWidth().clickable(openExternal(shortcut.url))) { frame ->
            Row(modifier = frame.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                PdHex(16.dp, edge = pd.accent, fill = pd.panel) {
                    Text("$number", style = numberStyle(8.sp, color = pd.ink))
                }
                Spacer(GlanceModifier.width(3.dp))
                PdDivider(height = 16.dp)
                Spacer(GlanceModifier.width(3.dp))
                when {
                    drawable != null -> Icon(drawable, null, size = 12.dp, tint = tint ?: pd.accent)
                    LucideIcons.isEmoji(shortcut.icon) -> Text(shortcut.icon!!, style = textStyle(size = 11.sp))
                    else -> Icon(LucideIcons.LINK, null, size = 12.dp, tint = tint ?: pd.accent)
                }
                Spacer(GlanceModifier.width(5.dp))
                // Labels are user content: no uppercase
                Text(shortcut.label, style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = pd.text), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

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
