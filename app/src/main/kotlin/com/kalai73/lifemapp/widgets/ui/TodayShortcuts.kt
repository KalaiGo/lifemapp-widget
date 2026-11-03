package com.kalai73.lifemapp.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.presentation.MailTagUi
import com.kalai73.lifemapp.widgets.core.presentation.ShortcutUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.designsystem.ColorContrast
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes

// ── Shortcuts ───────────────────────────────────────────────────────────────

@Composable
internal fun ShortcutsSection(ui: WidgetUi, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit, onUnlock: () -> Unit) {
    val t = LocalTok.current
    WidgetCard(stringResource(R.string.today_shortcuts), LucideIcons.ZAP, rule = false, onTitle = { onOpenWeb(WebRoutes.QUICK_LINKS) }) {
        SubHeader(LucideIcons.MAIL, stringResource(R.string.today_mail), count = null) { openLink(ui.mailUrl ?: WebRoutes.MAIL) }
        MailBody(ui, openLink, onOpenWeb)
        if (ui.driveLinks.isNotEmpty()) {
            SubHeader(LucideIcons.FOLDER, stringResource(R.string.today_drive), count = null, top = SUB_HEADER_GAP) { openLink(ui.driveLinks.first().url) }
            DriveBody(ui, openLink)
        }
        SubHeader(
            LucideIcons.LINK, stringResource(R.string.today_links),
            count = ui.shortcuts.size.takeUnless { ui.shortcutsLocked },
            onAdd = { onOpenWeb(WebRoutes.QUICK_LINKS) }.takeUnless { ui.shortcutsLocked },
            top = SUB_HEADER_GAP,
        ) { onOpenWeb(WebRoutes.QUICK_LINKS) }
        when {
            ui.shortcutsLocked -> EmptyLine(stringResource(R.string.today_locked_banner), onUnlock)
            ui.shortcuts.isEmpty() -> EmptyLine(stringResource(R.string.today_no_links)) { onOpenWeb(WebRoutes.QUICK_LINKS) }
            // Predator: numbered rows, two per line; Classic: icon tiles, four per line
            t.predator -> ui.shortcuts.chunked(2).forEachIndexed { r, pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEachIndexed { i, s -> ShortcutRow(s, r * 2 + i + 1, Modifier.weight(1f), openLink) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            else -> ui.shortcuts.chunked(4).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { s -> ShortcutTile(s, Modifier.weight(1f), openLink) }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Space above the Drive and Links sub-headers, which follow content (Mail sits right under the section title). */
private val SUB_HEADER_GAP = 24.dp

/** A divider line above, small icon and label in the ink color, an optional count and "+". */
@Composable
private fun SubHeader(icon: Int, title: String, count: Int?, onAdd: (() -> Unit)? = null, top: Dp = 12.dp, onClick: () -> Unit) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(top = top, bottom = 2.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, 13.dp, t.ink)
            Spacer(Modifier.width(6.dp))
            Text(t.s(title), style = if (t.predator) t.label(13.sp) else t.body(13.sp, t.ink, FontWeight.Bold), maxLines = 1)
            if (count != null) Text("$count", style = t.caption(11.sp), modifier = Modifier.padding(start = 6.dp))
            if (onAdd != null) {
                Box(Modifier.size(28.dp).clickable(onClick = onAdd), contentAlignment = Alignment.Center) { Ic(LucideIcons.ADD, 14.dp, t.dim) }
            }
        }
    }
}

/** Drive: the user's service links as badges in one row (up to three, as the widget shows), each opening its link. */
@Composable
private fun DriveBody(ui: WidgetUi, openLink: (String) -> Unit) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        ui.driveLinks.take(3).forEach { link ->
            Box(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                Row(
                    Modifier.fillMaxWidth().framed(t, if (t.predator) t.pd.panel else t.card, edge = if (t.predator) t.pd.edge else t.card)
                        .clickable { openLink(link.url) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Ic(LucideIcons.FOLDER, 12.dp, t.accent)
                    Spacer(Modifier.width(5.dp))
                    // Labels are user content: no uppercase
                    Text(link.label, style = t.body(11.sp, t.dim, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Mail: the connect prompt, the web E-Mails widget's tags, or (older servers) the inbox row with its count. */
@Composable
private fun MailBody(ui: WidgetUi, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit) {
    val t = LocalTok.current
    if (!ui.mailConnected) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).framed(t, if (t.predator) t.pd.well else t.well, edge = if (t.predator) t.pd.edge else t.well, cut = 8.dp)
                .clickable { onOpenWeb(WebRoutes.MAIL) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(LucideIcons.MAIL, 18.dp, t.dim)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_mail_not_connected), style = t.body(13.sp, t.text, FontWeight.Medium))
                Text(stringResource(R.string.today_connect_mail), style = t.caption(10.sp))
            }
        }
        return
    }
    if (ui.mailTags.isNotEmpty()) {
        // Two columns, filled row by row (1 2 / 3 4 ...); an odd last tag keeps half the width
        ui.mailTags.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEachIndexed { c, tag -> MailTagRow(tag, r * 2 + c + 1, openLink, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        return
    }
    // The inbox row alone still takes one of the two tag columns
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InboxRow(ui, openLink, Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

/** The inbox with its unread count (older servers without the tag list). */
@Composable
private fun InboxRow(ui: WidgetUi, openLink: (String) -> Unit, modifier: Modifier) {
    val t = LocalTok.current
    val unread = ui.mailCount > 0
    Row(
        modifier.padding(vertical = 4.dp).framed(t, if (t.predator) t.row else t.card)
            .clickable { openLink(ui.mailUrl ?: WebRoutes.MAIL) }.padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (t.predator) {
            Bar(t.accent, 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Ic(LucideIcons.INBOX, 16.dp, if (unread) t.accent else t.dim)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.today_inbox), style = t.body(14.sp, t.text, FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        // Unread count, right after the label: accent parallelogram when there is unread mail, framed panel otherwise
        val shape: Shape = if (t.predator) pdParallelogramShape(5.dp) else RoundedCornerShape(10.dp)
        Text(
            "${ui.mailCount}",
            style = t.label(12.sp, if (unread) t.onAccent else t.dim),
            modifier = Modifier.clip(shape).background(if (unread) t.accent else if (t.predator) t.pd.panel else t.well, shape)
                .let { if (!unread && t.predator) it.border(1.dp, t.pd.edge, shape) else it }
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

/**
 * A mail tag as the web E-Mails widget lists it: running number, color bar, icon, name ("Today" for the inbox) and
 * unread / total. Later horizons are dimmed; the inbox count is filled with the accent when there is unread mail.
 */
@Composable
private fun MailTagRow(tag: MailTagUi, number: Int, openLink: (String) -> Unit, modifier: Modifier) {
    val t = LocalTok.current
    val inbox = tag.name == null
    val unread = tag.unread > 0
    val tint = LifeMappColors.parseHex(tag.color) ?: t.accent
    Row(
        // No border: the row fill alone sets the tag apart
        modifier.padding(vertical = 2.dp).alpha(tag.fade)
            .framed(t, if (t.predator) t.row else t.card)
            .clickable { openLink(tag.url ?: WebRoutes.MAIL) }.padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBadge(number, size = 18.dp)
        Spacer(Modifier.width(4.dp))
        Bar(tint, 18.dp)
        Spacer(Modifier.width(5.dp))
        Ic(LucideIcons.resolve(tag.icon) ?: LucideIcons.MAIL, 14.dp, if (inbox && unread) t.accent else t.dim)
        Spacer(Modifier.width(5.dp))
        Text(
            tag.name ?: stringResource(R.string.today_mail_today),
            style = t.body(13.sp, t.text, if (inbox) FontWeight.Bold else FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The count follows the name; a long name ellipsizes rather than pushing the count out
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        val shape: Shape = if (t.predator) pdParallelogramShape(5.dp) else RoundedCornerShape(10.dp)
        val filled = inbox && unread
        val ink = when {
            filled -> t.onAccent
            unread && !inbox -> tint
            unread -> t.accent
            else -> t.dim
        }
        Text(
            "${tag.unread} / ${tag.total}",
            style = t.label(11.sp, ink),
            maxLines = 1,
            modifier = Modifier.clip(shape).background(if (filled) t.accent else if (t.predator) t.pd.panel else t.well, shape)
                .let { if (filled) it else it.border(1.dp, if (unread) ink.copy(alpha = 0.4f) else t.line, shape) }
                .padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}

/** A quick link as a numbered row (Predator): number hex, divider, the link icon in its color and the label. */
@Composable
private fun ShortcutRow(s: ShortcutUi, number: Int, modifier: Modifier, openLink: (String) -> Unit) {
    val t = LocalTok.current
    val tint = LifeMappColors.parseHex(s.color)
    Box(modifier.padding(2.dp)) {
        Row(
            Modifier.fillMaxWidth().framed(t, t.row).clickable { openLink(s.url) }.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBadge(number, size = 20.dp)
            Spacer(Modifier.width(5.dp))
            Box(Modifier.width(1.dp).height(16.dp).background(t.line))
            Spacer(Modifier.width(6.dp))
            LinkGlyph(s, tint, 14.dp, linkFallback = true)
            Spacer(Modifier.width(6.dp))
            // Labels are user content: no uppercase
            Text(s.label, style = t.body(13.sp, t.text, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShortcutTile(s: ShortcutUi, modifier: Modifier, openLink: (String) -> Unit) {
    val t = LocalTok.current
    val tint = LifeMappColors.parseHex(s.color)
    Column(modifier.clickable { openLink(s.url) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint?.copy(alpha = 0.16f) ?: t.tint),
            contentAlignment = Alignment.Center,
        ) { LinkGlyph(s, tint, 22.dp) }
        Spacer(Modifier.height(3.dp))
        Text(s.label, style = t.body(11.sp, t.text).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The link's Lucide icon, its emoji, or (unbundled icon) the link glyph when [linkFallback], else the first letter
 * of the label. The link color is raised to icon contrast over the page, as the widget does.
 */
@Composable
private fun LinkGlyph(s: ShortcutUi, tint: Color?, size: Dp, linkFallback: Boolean = false) {
    val t = LocalTok.current
    val drawable = LucideIcons.resolve(s.icon)
    val bg = if (t.predator) t.row else t.page
    val iconTint = tint?.let { ColorContrast.ensure(it, bg, ColorContrast.GRAPHIC) } ?: t.ink
    when {
        drawable != null -> Ic(drawable, size, iconTint)
        LucideIcons.isEmoji(s.icon) -> Text(s.icon, style = t.body((size.value - 2).sp))
        linkFallback -> Ic(LucideIcons.LINK, size, iconTint)
        else -> Text((s.label.firstOrNull() ?: '?').uppercase(), style = t.label((size.value - 2).sp, tint?.let { ColorContrast.ensure(it, bg) } ?: t.ink))
    }
}
