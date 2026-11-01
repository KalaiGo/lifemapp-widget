package com.kalai73.lifemapp.widgets.widget.integrity

// The Integrity domain card as on the web's Today page (components/domains/integrity-domain-with-widgets.tsx),
// read-only: number, name and rating, the score gauge, the info box, the Reception (reminders by voice), each topic
// with its first items, then the Warnings bar. Editing, collapsing and "Add topic" stay on the web; taps open it.

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyListScope
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.kalai73.lifemapp.widgets.core.data.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.data.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.data.presentation.TopicUi
import com.kalai73.lifemapp.widgets.core.designsystem.ColorContrast
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdDrawables
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetColors
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.PdHex
import com.kalai73.lifemapp.widgets.widget.common.PdWrapFrame
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.widget.common.numberStyle
import com.kalai73.lifemapp.widgets.widget.common.openRoute
import com.kalai73.lifemapp.widgets.widget.common.textStyle
import com.kalai73.lifemapp.widgets.widget.voice.VoiceInputActivity

private const val ID_HEADER = -30L
private const val ID_WARNINGS = -31L
/** Topic rows: base + topic index × 2²⁰ + slot (0 Reception, 1 header, 2 empty, 3 "more", 16+ items), clear of
 *  the day (~10⁹), shortcut (~2·10⁹), event (~2·10¹⁰) and task (~2⁴⁰) ranges */
private const val ID_TOPIC_BASE = 3L shl 40

private const val SEGMENTS = 10

/** The card's rows, for the Dashboard's list. */
internal fun LazyListScope.integritySection(integrity: IntegrityUi) {
    item(itemId = ID_HEADER) { IntegrityHeader(integrity) }
    integrity.topics.forEachIndexed { index, topic ->
        val base = ID_TOPIC_BASE + (index.toLong() shl 20)
        // The Reception sits right above the Reminders topic, as on the web
        if (topic.reminders) item(itemId = base) { Reception() }
        item(itemId = base + 1) { TopicHeader(topic) }
        if (topic.items.isEmpty()) {
            item(itemId = base + 2) { NoContent(topic) }
        } else {
            itemsIndexed(topic.items, itemId = { i, _ -> base + 16 + i }) { i, item -> ItemRow(topic, item, i + 1) }
        }
        if (topic.more > 0) item(itemId = base + 3) { MoreLine(topic.more) }
    }
    item(itemId = ID_WARNINGS) { WarningsBar(integrity.warnings) }
}

// ── Colors ──────────────────────────────────────────────────────────────────

/** The semantic red → green ramp of pd-segment-gauge.tsx (--pd-g1…8, predator.css); dark mode brightens the greens. */
private val RampLight = longArrayOf(0xFFE5484D, 0xFFEF5B3A, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF93BC2A, 0xFF3DB45F, 0xFF1E9E52)
private val RampDark = longArrayOf(0xFFE5484D, 0xFFEF5B3A, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFFA3C93A, 0xFF4CC26B, 0xFF22B35E)

/** Segment [i] (0-based): the top three share the darkest green. */
@Composable
private fun ramp(i: Int): ColorProvider {
    val k = minOf(i, 7)
    return LocalWidgetColors.current.of(day = Color(RampLight[k]), night = Color(RampDark[k]))
}

/** The rating number on its ramp color: white on red and green, dark on orange and yellow (white is < 2.2:1 there). */
@Composable
private fun onRamp(rating: Int): ColorProvider {
    val k = (rating - 1).coerceIn(0, 7)
    return LocalWidgetColors.current.of(
        day = ColorContrast.onFill(Color(RampLight[k])),
        night = ColorContrast.onFill(Color(RampDark[k])),
    )
}

/** --pd-warn and its ink, the same amber on every scheme */
private val Warn = Color(0xFFF59E0B)
private val WarnOn = ColorProvider(Color(0xFF1A1004))

@Composable
private fun warnInk(): ColorProvider = LocalWidgetColors.current.of(day = Color(0xFFA04D05), night = Color(0xFFFBBF4D))

@Composable
private fun warnTintClassic(): ColorProvider = LocalWidgetColors.current.of(day = Color(0xFFFEF3C7), night = Color(0xFF3A2A08))

// ── Rows ────────────────────────────────────────────────────────────────────

/** "01 INTEGRITY" with the rating hex, the score gauge, then the info box ("Capture & Organize"). */
@Composable
private fun IntegrityHeader(integrity: IntegrityUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val name = integrity.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.integrity)
    val open = openRoute(context, WebRoutes.INTEGRITY)
    Column(modifier = GlanceModifier.fillMaxWidth().clickable(open).padding(top = 14.dp, bottom = 2.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("01", style = numberStyle(20.sp, color = pd?.faint ?: GlanceTheme.colors.outline), maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                style.label(name),
                style = if (pd != null) labelStyle(size = 16.sp, color = pd.ink)
                else textStyle(size = 15.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            integrity.rating?.let { RatingBadge(it) }
        }
        Spacer(GlanceModifier.height(8.dp))
        Gauge(integrity.rating)
        integrity.infoBox?.let { InfoBox(it) }
    }
}

@Composable
private fun RatingBadge(rating: Int) {
    val pd = LocalPd.current
    val fill = if (rating > 0) ramp(rating - 1) else pd?.well ?: GlanceTheme.colors.surfaceVariant
    if (pd != null) {
        PdHex(22.dp, edge = pd.edge2, fill = fill) {
            Text("$rating", style = numberStyle(11.sp, color = if (rating > 0) onRamp(rating) else pd.dim), maxLines = 1)
        }
    } else {
        Box(
            modifier = GlanceModifier.size(24.dp).cornerRadius(12.dp).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rating", style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = if (rating > 0) onRamp(rating) else GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
    }
}

/** Ten skewed segments, filled up to the rating; the rating's own segment stands taller, like the web's knob. */
@Composable
private fun Gauge(rating: Int?) {
    val pd = LocalPd.current
    val off = pd?.well ?: GlanceTheme.colors.surfaceVariant
    val filled = (rating ?: 0).coerceIn(0, SEGMENTS)
    // RemoteViews rows take at most 10 children, so the 3dp gaps are padding on the segments, not spacers
    Row(modifier = GlanceModifier.fillMaxWidth().height(12.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until SEGMENTS) {
            Image(
                provider = ImageProvider(PdDrawables.SEGMENT),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(if (i < filled) ramp(i) else off),
                modifier = GlanceModifier.defaultWeight().height(if (i == filled - 1) 12.dp else 7.dp)
                    .padding(start = if (i > 0) 1.5.dp else 0.dp, end = if (i < SEGMENTS - 1) 1.5.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun InfoBox(text: String) {
    val pd = LocalPd.current
    Spacer(GlanceModifier.height(8.dp))
    if (pd != null) {
        PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge2, fill = pd.accentTint, modifier = GlanceModifier.fillMaxWidth()) { frame ->
            Text(
                text,
                style = captionStyle(size = 11.sp, color = pd.text).copy(textAlign = TextAlign.Center),
                maxLines = 2,
                modifier = frame.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    } else {
        Text(
            text,
            style = textStyle(size = 12.sp, color = GlanceTheme.colors.onSecondaryContainer).copy(textAlign = TextAlign.Center),
            maxLines = 2,
            modifier = GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(LocalWidgetStyle.current.innerRadius).padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/** The landing zone above Reminders: tap to speak reminders (VoiceInputActivity in reminders mode). */
@Composable
private fun Reception() {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val speak = speakReminders()
    val label = context.getString(R.string.integrity_reception)
    val hint = context.getString(R.string.integrity_create_reminder)
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp)) {
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge2, fill = pd.well, modifier = GlanceModifier.fillMaxWidth().clickable(speak)) { frame ->
                Column(modifier = frame.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PdHex(32.dp, edge = pd.accent, fill = pd.panel) { Icon(LucideIcons.MIC, hint, size = 15.dp, tint = pd.ink) }
                    Spacer(GlanceModifier.height(6.dp))
                    Text(style.label(label), style = labelStyle(size = 13.sp, color = pd.text), maxLines = 1)
                    Text(hint, style = captionStyle(size = 10.sp, color = pd.dim), maxLines = 1)
                }
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(style.innerRadius).clickable(speak).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp).background(GlanceTheme.colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(LucideIcons.MIC, hint, size = 18.dp) }
                Spacer(GlanceModifier.height(6.dp))
                Text(label.uppercase(), style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                Text(hint, style = textStyle(size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            }
        }
    }
}

/** Numbered hex, name, item count, and "+" to edit on the web card (none for Reminders). */
@Composable
private fun TopicHeader(topic: TopicUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val accent = pd?.ink ?: GlanceTheme.colors.primary
    val dim = pd?.dim ?: GlanceTheme.colors.onSurfaceVariant
    val name = topic.name ?: context.getString(R.string.locked)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.defaultWeight().clickable(openRoute(context, WebRoutes.INTEGRITY)), verticalAlignment = Alignment.CenterVertically) {
            if (!topic.reminders) {
                // Decorative: the web card's collapse toggle (a widget topic is always expanded)
                Icon(LucideIcons.CHEVRON_DOWN, null, size = 12.dp, tint = dim)
                Spacer(GlanceModifier.width(4.dp))
            }
            if (pd != null) {
                PdHex(18.dp, edge = pd.accent, fill = pd.panel) { Text("${topic.number}", style = numberStyle(9.sp, color = pd.ink)) }
            } else {
                Box(
                    modifier = GlanceModifier.size(18.dp).cornerRadius(9.dp).background(GlanceTheme.colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("${topic.number}", style = textStyle(size = 10.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary)) }
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(
                if (topic.name != null) style.label(name) else name,
                style = if (pd != null) labelStyle(size = 13.sp, color = if (topic.name != null) accent else dim)
                else textStyle(size = 13.sp, weight = FontWeight.Bold, color = if (topic.name != null) accent else dim),
                maxLines = 1,
            )
            if (topic.count > 0) {
                Spacer(GlanceModifier.width(5.dp))
                Text("(${topic.count})", style = if (pd != null) captionStyle(size = 10.sp, color = dim) else textStyle(size = 11.sp, color = dim), maxLines = 1)
            }
        }
        // Reminders have no header button: the Reception landing zone above them speaks reminders
        if (!topic.reminders) {
            Box(modifier = GlanceModifier.size(28.dp).clickable(addAction(topic)), contentAlignment = Alignment.Center) {
                Icon(LucideIcons.ADD, context.getString(R.string.integrity_add_item), size = 14.dp, tint = pd?.ink ?: GlanceTheme.colors.primary)
            }
        }
    }
}

/**
 * An empty topic: a recessed, tappable row — an accent icon chip (mic for Reminders, "+" otherwise), "No content
 * yet", and a dim hint saying what the tap does. Indented to the item rows so it reads as the topic's content.
 */
@Composable
private fun NoContent(topic: TopicUi) {
    val context = LocalContext.current
    val pd = LocalPd.current
    val title = context.getString(R.string.integrity_no_content)
    val hint = context.getString(if (topic.reminders) R.string.integrity_create_reminder else R.string.integrity_add_item)
    val icon = if (topic.reminders) LucideIcons.MIC else LucideIcons.ADD
    val add = addAction(topic)
    Column(modifier = GlanceModifier.fillMaxWidth().padding(start = 10.dp, top = 3.dp, bottom = 3.dp)) {
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge, fill = pd.well, modifier = GlanceModifier.fillMaxWidth().clickable(add)) { frame ->
                Row(modifier = frame.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PdHex(26.dp, edge = pd.accent, fill = pd.accentTint) { Icon(icon, hint, size = 12.dp, tint = pd.accent) }
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(title, style = textStyle(size = 12.sp, weight = FontWeight.Medium, color = pd.text), maxLines = 1)
                        Text(hint, style = captionStyle(size = 10.sp, color = pd.dim), maxLines = 1)
                    }
                }
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(LocalWidgetStyle.current.innerRadius).clickable(add).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier.size(28.dp).cornerRadius(14.dp).background(GlanceTheme.colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, hint, size = 14.dp, tint = GlanceTheme.colors.primary) }
                Spacer(GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(title, style = textStyle(size = 13.sp, weight = FontWeight.Medium, color = GlanceTheme.colors.onSurface), maxLines = 1)
                    Text(hint, style = textStyle(size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                }
            }
        }
    }
}

/** "• 2.1 Item", with the warning icon when flagged. */
@Composable
private fun ItemRow(topic: TopicUi, item: TopicItemUi, number: Int) {
    val context = LocalContext.current
    val pd = LocalPd.current
    val dim = pd?.dim ?: GlanceTheme.colors.onSurfaceVariant
    val text = pd?.text ?: GlanceTheme.colors.onBackground
    val name = item.name?.takeIf { it.isNotBlank() }
        ?: if (item.name == null) context.getString(R.string.locked) else if (item.isImage) context.getString(R.string.integrity_image) else ""
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(openRoute(context, WebRoutes.INTEGRITY)).padding(start = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = GlanceModifier.size(5.dp).cornerRadius(3.dp).background(pd?.faint ?: GlanceTheme.colors.outline)) {}
        Spacer(GlanceModifier.width(7.dp))
        Text("${topic.number}.$number", style = if (pd != null) captionStyle(size = 10.sp, color = dim) else textStyle(size = 10.sp, color = dim), maxLines = 1)
        Spacer(GlanceModifier.width(5.dp))
        if (item.warning) {
            Icon(LucideIcons.WARNING, context.getString(R.string.integrity_warnings), size = 12.dp, tint = warnInk())
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(
            name,
            style = textStyle(size = 13.sp, weight = FontWeight.Medium, color = if (item.name == null) dim else text),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun MoreLine(more: Int) {
    val context = LocalContext.current
    val pd = LocalPd.current
    val dim = pd?.dim ?: GlanceTheme.colors.onSurfaceVariant
    Text(
        context.getString(R.string.integrity_more, more),
        style = if (pd != null) captionStyle(size = 10.sp, color = dim) else textStyle(size = 11.sp, color = dim),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().clickable(openRoute(context, WebRoutes.INTEGRITY)).padding(start = 22.dp, top = 2.dp, bottom = 2.dp),
    )
}

/** The amber "WARNINGS" bar with its count, opening the web's warnings list. */
@Composable
private fun WarningsBar(count: Int) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val open = openRoute(context, WebRoutes.WARNINGS)
    val label = context.getString(R.string.integrity_warnings)
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = ColorProvider(Warn), fill = pd.tint(Warn, 0.14f), modifier = GlanceModifier.fillMaxWidth().clickable(open)) { frame ->
                WarningsContent(frame, style.label(label), count, labelStyle(size = 14.sp, color = warnInk()), badgeSize = 22.dp)
            }
        } else {
            WarningsContent(
                GlanceModifier.background(warnTintClassic()).cornerRadius(style.innerRadius).clickable(open),
                label, count, textStyle(size = 13.sp, weight = FontWeight.Bold, color = warnInk()), badgeSize = 22.dp,
            )
        }
    }
}

@Composable
private fun WarningsContent(modifier: GlanceModifier, label: String, count: Int, labelStyle: androidx.glance.text.TextStyle, badgeSize: Dp) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(GlanceModifier.defaultWeight())
        Icon(LucideIcons.WARNING, null, size = 13.dp, tint = warnInk())
        Spacer(GlanceModifier.width(6.dp))
        Text(label, style = labelStyle, maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier.size(badgeSize).cornerRadius(4.dp).background(ColorProvider(Warn)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", style = numberStyle(12.sp, color = WarnOn), maxLines = 1)
        }
    }
}

// ── Actions ─────────────────────────────────────────────────────────────────

@Composable
private fun speakReminders(): Action = actionStartActivity(VoiceInputActivity.intent(LocalContext.current, reminders = true))

/** "+" of a topic: Reminders are added by voice; other topics are edited on the web. */
@Composable
private fun addAction(topic: TopicUi): Action =
    if (topic.reminders) speakReminders() else openRoute(LocalContext.current, WebRoutes.INTEGRITY)
