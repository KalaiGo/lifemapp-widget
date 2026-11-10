package com.kalai73.lifemapp.widgets.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.kalai73.lifemapp.widgets.core.data.LifeMappConfig
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.runtime.rememberUpdatedState
import com.kalai73.lifemapp.widgets.core.data.content.ImageContent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicUi
import com.kalai73.lifemapp.widgets.core.designsystem.ColorContrast
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import androidx.compose.ui.platform.LocalContext
import com.kalai73.lifemapp.widgets.widget.voice.VoiceInputActivity

// ── Integrity ───────────────────────────────────────────────────────────────

/** The semantic red → green ramp of the web's score gauge; dark mode brightens the greens. */
private val RampLight = longArrayOf(0xFFE5484D, 0xFFEF5B3A, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF93BC2A, 0xFF3DB45F, 0xFF1E9E52)
private val RampDark = longArrayOf(0xFFE5484D, 0xFFEF5B3A, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFFA3C93A, 0xFF4CC26B, 0xFF22B35E)
internal val Warn = Color(0xFFF59E0B)

private fun ramp(i: Int, dark: Boolean) = Color((if (dark) RampDark else RampLight)[minOf(i, 7)])

/** LazyColumn key of one of the other Today domains (positions 2–5) */
internal fun domainKey(domain: IntegrityUi) = "domain-${domain.domainId}"

/** The domain's own name; its default by position when it has none or is encrypted and locked. */
@Composable
internal fun domainName(domain: IntegrityUi): String = domain.name?.takeIf { it.isNotBlank() } ?: when (domain.position) {
    1 -> stringResource(R.string.today_integrity)
    2 -> "Serenity"
    3 -> "Vitality"
    4 -> "Stability"
    5 -> "Tenacity"
    10 -> "Trayectory"
    11 -> "Resiliency"
    12 -> "Community"
    13 -> "Mastery"
    14 -> "Felicity"
    15 -> "Prosperity"
    16 -> "Residency"
    17 -> "Mobility"
    18 -> "Efficacy"
    19 -> "Property"
    else -> stringResource(R.string.today_locked)
}

/**
 * The web's domain card: Integrity (with the Reception above Reminders and the Warnings bar) or one of the other
 * Today domains, Serenity, Vitality, Stability and Tenacity, which only this screen shows (never the widgets).
 */
@Composable
internal fun DomainCard(
    integrity: IntegrityUi, onOpenWeb: (String) -> Unit,
    items: ItemHandlers,
    onToggleTopic: (String, Boolean) -> Unit,
    preActionContent: (@Composable () -> Unit)? = null,
) {
    val t = LocalTok.current
    val isIntegrity = integrity.position == 1
    val route = if (isIntegrity || integrity.domainId.isBlank()) WebRoutes.INTEGRITY else WebRoutes.domain(integrity.domainId)
    val context = LocalContext.current
    val linkCopied = stringResource(R.string.item_link_copied)
    // The item with a dialog open from its long-press menu: the editor (Edit) or the delete confirmation
    var editing by remember { mutableStateOf<Pair<TopicItemUi, ItemAction>?>(null) }
    editing?.let { (item, action) ->
        val close = { editing = null }
        when (action) {
            ItemAction.DELETE -> RemoveItemDialog(item, onConfirm = { items.delete(item.id); close() }, onDismiss = close)
            else -> ItemEditorDialog(
                item,
                focusTitle = true,
                loadText = items.loadText,
                onSave = { name, html -> items.save(item.id, name, html); close() },
                onDelete = { items.delete(item.id); close() },
                onDismiss = close,
            )
        }
    }
    val onItemAction = { topic: TopicUi, item: TopicItemUi, action: ItemAction ->
        when (action) {
            ItemAction.EDIT, ItemAction.DELETE -> editing = item to action
            ItemAction.MOVE_UP -> items.move(item.id, topic.id, true)
            ItemAction.MOVE_DOWN -> items.move(item.id, topic.id, false)
            ItemAction.TOGGLE_WARNING -> items.setWarning(item.id, !item.warning)
            ItemAction.COPY_LINK -> {
                // The web's item link (item-links.ts): Today domains open at /today/<topic>/<item>
                val url = "${LifeMappConfig().webUrl}/today/${topic.id}/${item.id}"
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(url, url))
                Toast.makeText(context, linkCopied, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val name = domainName(integrity)
    // The web's domain card: one bordered, rounded (Predator: chamfered) surface around the whole domain
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp)
            .framed(t, t.card, edge = t.line, cut = if (t.predator) 8.dp else 24.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().clickable { onOpenWeb(route) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(integrity.position.toString().padStart(2, '0'), style = t.label(22.sp, t.faint))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (integrity.name != null) t.s(name) else name,
                    style = (if (t.predator) t.label(17.sp) else t.body(16.sp, t.ink, FontWeight.Bold)).copy(textAlign = TextAlign.Center),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                integrity.rating?.let { RatingBadge(it) }
            }
            Spacer(Modifier.height(8.dp))
            Gauge(integrity.rating)
            integrity.infoBox?.let { info ->
                Spacer(Modifier.height(8.dp))
                Text(
                    info,
                    style = (if (t.predator) t.caption(12.sp, t.text) else t.body(12.sp, t.onTint)).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().let {
                        // Predator: square tinted field, thin edge, bracket corners and center ticks (styleguide info box)
                        if (t.predator) it.background(t.pd.accentTint).border(1.dp, t.pd.edge).bracketCorners(t.pd.edge2)
                        else it.framed(t, t.tint)
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        preActionContent?.let {
            Spacer(Modifier.height(10.dp))
            it()
        }
        integrity.topics.forEach { topic ->
            // The Reception sits right above the Reminders topic, as on the web
            if (topic.reminders) Reception()
            TopicHeader(topic) { onToggleTopic(topic.id, !topic.collapsed) }
            // A collapsed topic shows its header only, as on the web card
            if (topic.collapsed && topic.items.isNotEmpty()) return@forEach
            if (topic.items.isEmpty()) NoContent(topic) { onOpenWeb(route) }
            else topic.items.forEachIndexed { i, item ->
                ItemRow(
                    topic, item, i + 1,
                    canMoveUp = i > 0,
                    // Items beyond the listed ones can still follow the last listed one
                    canMoveDown = i < maxOf(topic.count, topic.items.size) - 1,
                    onAction = { onItemAction(topic, item, it) },
                    onToggle = { items.setCollapsed(item.id, !item.collapsed) },
                )
                // An expanded image item shows its picture, as on the web card
                if (item.isImage && !item.collapsed) ItemPicture(item, items.loadImage)
            }
            if (topic.more > 0) {
                Text(
                    stringResource(R.string.today_items_more, topic.more), style = t.caption(10.sp),
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
                )
            }
        }
        // The Warnings bar counts flagged items of every domain; the web shows it on the Integrity card only
        if (isIntegrity) WarningsBar(integrity.warnings) { onOpenWeb(WebRoutes.WARNINGS) }
    }
}

@Composable
private fun RatingBadge(rating: Int) {
    val t = LocalTok.current
    val fill = if (rating > 0) ramp(rating - 1, t.dark) else t.well
    val on = if (rating > 0) ColorContrast.onFill(fill) else t.dim
    if (t.predator) {
        Hex(26.dp, edge = t.pd.edge2, fill = fill) { Text("$rating", style = t.label(12.sp, on)) }
    } else {
        Box(Modifier.size(26.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Text("$rating", style = t.body(12.sp, on, FontWeight.Bold))
        }
    }
}

/** Ten skewed segments, filled up to the rating; the rating's own segment stands taller, like the web's knob. */
@Composable
private fun Gauge(rating: Int?) {
    val t = LocalTok.current
    val filled = (rating ?: 0).coerceIn(0, 10)
    val shape: Shape = if (t.predator) pdParallelogramShape(2.dp) else RoundedCornerShape(3.dp)
    Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 10) {
            Box(
                Modifier.weight(1f).height(if (i == filled - 1) 14.dp else 8.dp).clip(shape)
                    .background(if (i < filled) ramp(i, t.dark) else t.well, shape),
            )
        }
    }
}

/** The landing zone above Reminders: mic to speak reminders, pencil to type one (VoiceInputActivity). */
@Composable
private fun Reception() {
    val t = LocalTok.current
    val context = LocalContext.current
    val hint = stringResource(R.string.today_create_reminder)
    val record = stringResource(R.string.today_record)
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .let {
                // Predator: the web's target reticle (square well, thin edge, accent corner brackets and center ticks)
                if (t.predator) it.background(t.pd.well).border(1.dp, t.pd.edge).bracketCorners(t.accent)
                else it.framed(t, t.well, edge = t.well, cut = 8.dp)
            }
            .padding(horizontal = 12.dp, vertical = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceptionButton(LucideIcons.MIC, record) { context.startActivity(VoiceInputActivity.intent(context, reminders = true)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t.s(stringResource(R.string.today_reception)), style = if (t.predator) t.label(14.sp, t.text) else t.body(13.sp, t.dim, FontWeight.Bold))
            Text(hint, style = t.caption(10.sp), maxLines = 1)
        }
        ReceptionButton(LucideIcons.PENCIL, hint) { context.startActivity(VoiceInputActivity.intent(context, reminders = true, typed = true)) }
    }
}

@Composable
private fun ReceptionButton(icon: Int, desc: String, onClick: () -> Unit) {
    val t = LocalTok.current
    Box(Modifier.clickable(onClickLabel = desc, onClick = onClick)) {
        if (t.predator) Hex(36.dp, edge = t.accent, fill = t.pd.panel) { Ic(icon, 16.dp, t.ink, desc) }
        else Box(Modifier.size(38.dp).clip(CircleShape).background(t.tint), contentAlignment = Alignment.Center) { Ic(icon, 18.dp, t.onTint, desc) }
    }
}

/**
 * Chevron, numbered hex, name and item count. One tap anywhere on it collapses or expands the topic, as the web
 * card's chevron does; Reminders (always expanded) and a topic without items have nothing to fold.
 */
@Composable
internal fun TopicHeader(topic: TopicUi, onToggle: () -> Unit) {
    val t = LocalTok.current
    val name = topic.name ?: stringResource(R.string.today_locked)
    val toggles = !topic.reminders && topic.items.isNotEmpty()
    val label = stringResource(if (topic.collapsed) R.string.today_expand else R.string.today_collapse)
    Row(
        Modifier.fillMaxWidth()
            .let { if (toggles) it.clickable(onClickLabel = label, onClick = onToggle) else it }
            .padding(top = 20.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (topic.reminders) Text("•", style = t.body(14.sp, t.dim, FontWeight.Bold), modifier = Modifier.width(14.dp), textAlign = TextAlign.Center)
        else Ic(if (topic.collapsed) LucideIcons.CHEVRON_RIGHT else LucideIcons.CHEVRON_DOWN, 12.dp, t.dim, null).also { Spacer(Modifier.width(2.dp)) }
        Spacer(Modifier.width(4.dp))
        NumberBadge(topic.number, size = 22.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            if (topic.name != null) t.s(name) else name,
            style = if (t.predator) t.label(14.sp, if (topic.name != null) t.ink else t.dim) else t.body(14.sp, if (topic.name != null) t.ink else t.dim, FontWeight.Bold),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (topic.count > 0) Text("(${topic.count})", style = t.caption(11.sp), modifier = Modifier.padding(start = 6.dp))
    }
}

/** An empty topic: a recessed row with an accent chip, "No content yet" and what a tap does. */
@Composable
private fun NoContent(topic: TopicUi, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, top = 3.dp, bottom = 3.dp)
            .framed(t, if (t.predator) t.pd.well else t.well, edge = if (t.predator) t.pd.edge else t.well)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (topic.reminders) LucideIcons.MIC else LucideIcons.ADD
        if (t.predator) Hex(28.dp, edge = t.accent, fill = t.pd.accentTint) { Ic(icon, 13.dp, t.accent) }
        else Box(Modifier.size(28.dp).clip(CircleShape).background(t.tint), contentAlignment = Alignment.Center) { Ic(icon, 14.dp, t.onTint) }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.today_no_content), style = t.body(13.sp, t.text, FontWeight.Medium))
    }
}

/**
 * An item (subsubdomain), "• 2.1 Item" indented under the topic's hex as on the web card, with the warning icon
 * when flagged, a text icon when it has content and an image icon when it is (or its text holds) a picture. A tap
 * on a text item's header opens its edit modal (text items don't expand in the app); an image item's header shows
 * a chevron and a tap on it expands or collapses the picture below (saved like the web card's chevron). A long
 * press on the title opens the item menu (edit, move, warning, copy link, delete).
 */
@Composable
internal fun ItemRow(
    topic: TopicUi, item: TopicItemUi, number: Int,
    canMoveUp: Boolean, canMoveDown: Boolean,
    onAction: (ItemAction) -> Unit,
    onToggle: () -> Unit,
) {
    val t = LocalTok.current
    val name = item.name?.takeIf { it.isNotBlank() } ?: if (item.name == null) stringResource(R.string.today_locked) else ""
    val menuLabel = stringResource(R.string.item_menu)
    val toggleLabel = stringResource(if (item.collapsed) R.string.today_expand else R.string.today_collapse)
    val editLabel = stringResource(R.string.item_edit)
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    // The gesture detector outlives recompositions (keyed by the item): it reads the latest toggle through this
    val toggle by rememberUpdatedState(onToggle)
    val action by rememberUpdatedState(onAction)
    val toggles = item.isImage
    Box {
        Row(
            Modifier.fillMaxWidth()
                .pointerInput(item.id, toggles) {
                    detectTapGestures(
                        onTap = { if (toggles) toggle() else action(ItemAction.EDIT) },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                    )
                }
                .semantics {
                    if (toggles) onClick(toggleLabel) { toggle(); true }
                    else onClick(editLabel) { action(ItemAction.EDIT); true }
                    onLongClick(menuLabel) { menuOpen = true; true }
                }
                .padding(start = 18.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(11.dp), contentAlignment = Alignment.Center) {
                if (toggles) Ic(if (item.collapsed) LucideIcons.CHEVRON_RIGHT else LucideIcons.CHEVRON_DOWN, 11.dp, t.dim, null)
                else Box(Modifier.size(5.dp).clip(CircleShape).background(t.faint))
            }
            Spacer(Modifier.width(6.dp))
            Text("${topic.number}.$number", style = t.caption(11.sp), maxLines = 1)
            Spacer(Modifier.width(5.dp))
            if (item.warning) {
                Ic(LucideIcons.WARNING, 13.dp, if (t.dark) Color(0xFFFBBF4D) else Color(0xFFA04D05))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                name, style = t.body(14.sp, if (item.name == null) t.dim else t.text, FontWeight.Bold),
                // fill = false: the title only takes the width it needs, so the content icon sits right after it
                // (a long title still ellipsizes and leaves room for the icon)
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            // The text isn't listed here (items don't expand): the icon says there is some to see via Edit content
            if (item.hasContent) {
                Spacer(Modifier.width(6.dp))
                Ic(LucideIcons.CONTENT, 13.dp, t.dim, stringResource(R.string.item_has_content))
            }
            // The web header's image icon: an image item, or text that embeds a picture
            if (item.isImage || ImageContent.embedsImage(item.content)) {
                Spacer(Modifier.width(6.dp))
                Ic(LucideIcons.IMAGE, 13.dp, t.dim, stringResource(R.string.item_has_image))
            }
        }
        ItemMenu(
            item, canMoveUp, canMoveDown,
            expanded = menuOpen, onDismiss = { menuOpen = false }, onAction = { menuOpen = false; onAction(it) },
        )
    }
}

/** The amber "WARNINGS" bar with its count. */
@Composable
private fun WarningsBar(count: Int, onClick: () -> Unit) {
    val t = LocalTok.current
    val ink = if (t.dark) Color(0xFFFBBF4D) else Color(0xFFA04D05)
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)
            .framed(t, if (t.predator) t.tinted(Warn, 0.14f) else if (t.dark) Color(0xFF3A2A08) else Color(0xFFFEF3C7), edge = if (t.predator) Warn else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Ic(LucideIcons.WARNING, 14.dp, ink)
        Spacer(Modifier.width(6.dp))
        Text(t.s(stringResource(R.string.today_warnings)), style = if (t.predator) t.label(14.sp, ink) else t.body(14.sp, ink, FontWeight.Bold))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Warn), contentAlignment = Alignment.Center) {
            Text("$count", style = t.label(12.sp, Color(0xFF1A1004)))
        }
    }
}
