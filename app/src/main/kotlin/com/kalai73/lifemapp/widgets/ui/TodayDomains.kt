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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kalai73.lifemapp.widgets.core.designsystem.PdHexagonShape
import androidx.compose.ui.text.font.FontStyle
import com.kalai73.lifemapp.widgets.core.designsystem.PredatorColors
import com.kalai73.lifemapp.widgets.core.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.presentation.ShortcutUi
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicUi
import com.kalai73.lifemapp.widgets.core.designsystem.ColorContrast
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.ui.sidepanel.LocalPanel
import com.kalai73.lifemapp.widgets.ui.sidepanel.PanelMode
import com.kalai73.lifemapp.widgets.ui.sidepanel.PanelOpener
import com.kalai73.lifemapp.widgets.ui.tags.LocalTags
import com.kalai73.lifemapp.widgets.ui.tags.TagBadges
import com.kalai73.lifemapp.widgets.ui.tags.TagPickerDialog
import com.kalai73.lifemapp.widgets.core.presentation.panel.ItemTags
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
internal fun domainName(domain: IntegrityUi): String = domainName(domain.name, domain.position)

/** A domain's [name]; its default by [position] when it has none or is encrypted and locked. */
@Composable
internal fun domainName(name: String?, position: Int): String = name?.takeIf { it.isNotBlank() } ?: when (position) {
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
    20 -> "Delivery"
    21 -> "Solvency"
    22 -> "Visibility"
    23 -> "Fidelity"
    24 -> "Capacity"
    25 -> "Strategy"
    26 -> "Security"
    27 -> "Clarity"
    28 -> "Discovery"
    29 -> "Utility"
    else -> stringResource(R.string.today_locked)
}

/**
 * The web's domain card: Integrity (with the Reception above Reminders) or one of the other
 * Today domains, Serenity, Vitality, Stability and Tenacity, which only this screen shows (never the widgets).
 */
@Composable
internal fun DomainCard(
    integrity: IntegrityUi, onOpenWeb: (String) -> Unit,
    items: ItemHandlers,
    onToggleTopic: (String, Boolean) -> Unit,
    preActionContent: (@Composable () -> Unit)? = null,
    /** Integrity only: the user's quick links for the icon bar above the panel buttons */
    quickLinks: List<ShortcutUi> = emptyList(),
    openLink: (String) -> Unit = onOpenWeb,
) {
    val t = LocalTok.current
    val isIntegrity = integrity.position == 1
    val route = if (isIntegrity || integrity.domainId.isBlank()) WebRoutes.INTEGRITY else WebRoutes.domain(integrity.domainId)
    val context = LocalContext.current
    val linkCopied = stringResource(R.string.item_link_copied)
    // The item with a dialog open from its long-press menu: the editor (Edit) or the delete confirmation
    var editing by remember { mutableStateOf<Pair<TopicItemUi, ItemAction>?>(null) }
    var taggingItem by remember { mutableStateOf<Pair<TopicUi, TopicItemUi>?>(null) }
    var taggingTopic by remember { mutableStateOf<TopicUi?>(null) }
    val tagEnv = LocalTags.current
    taggingItem?.let { (topic, item) ->
        val effective = ItemTags.effective(topic.tags, item.tags)
        val locked = ItemTags.topicTags(topic.tags)
        TagPickerDialog(
            value = effective,
            locked = locked,
            required = false,
            onChange = { picked ->
                items.saveTags(item.id, topic.tags, picked)
                taggingItem = topic to item.copy(tags = ItemTags.itemTagsToStore(topic.tags, picked))
            },
            onOpenManager = {
                taggingItem = null
                tagEnv.onOpenManager { created ->
                    items.saveTags(item.id, topic.tags, effective + created)
                }
            },
            onEditTags = {
                taggingItem = null
                tagEnv.onOpenManager(null)
            },
            onDismiss = { taggingItem = null },
        )
    }
    taggingTopic?.let { topic ->
        TagPickerDialog(
            value = topic.tags,
            locked = emptyList(),
            required = true,
            onChange = { picked ->
                val next = ItemTags.topicTags(picked)
                items.saveTopicTags(topic.id, next)
                taggingTopic = topic.copy(tags = next)
            },
            onOpenManager = {
                taggingTopic = null
                tagEnv.onOpenManager { created ->
                    items.saveTopicTags(topic.id, ItemTags.topicTags(topic.tags + created))
                }
            },
            onEditTags = {
                taggingTopic = null
                tagEnv.onOpenManager(null)
            },
            onDismiss = { taggingTopic = null },
        )
    }
    editing?.let { (item, action) ->
        val close = { editing = null }
        when (action) {
            ItemAction.DELETE -> RemoveItemDialog(item, onConfirm = { items.delete(item.id); close() }, onDismiss = close)
            else -> ItemEditorDialog(
                item,
                focusTitle = true,
                loadText = items.loadText,
                nav = items.nav,
                onSave = { id, name, html -> items.save(id, name, html); close() },
                onDelete = { id -> items.delete(id); close() },
                onDismiss = close,
            )
        }
    }
    val onItemAction = { topic: TopicUi, item: TopicItemUi, action: ItemAction ->
        when (action) {
            ItemAction.EDIT, ItemAction.DELETE -> editing = item to action
            ItemAction.TAGS -> taggingItem = topic to item
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
    // The header opens the side panel's Editor on this domain (domain-card.tsx handleHeaderClick)
    val panel = LocalPanel.current
    val openHeader = {
        if (panel != null && integrity.domainId.isNotBlank()) panel.openOnDomain(integrity.domainId) else onOpenWeb(route)
    }
    // The web's domain card: one bordered, rounded (Predator: chamfered) surface around the whole domain
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp)
            .framed(t, t.card, edge = t.line, cut = if (t.predator) 8.dp else 24.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().clickable { openHeader() }) {
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
                val pdTint = PredatorColors.mix(t.card, t.accent, 0.16f)
                Text(
                    info,
                    style = (if (t.predator) t.caption(14.sp, t.text).copy(fontWeight = FontWeight.Bold)
                    else t.body(14.sp, t.text.copy(alpha = 0.9f), FontWeight.Bold).copy(fontStyle = FontStyle.Italic)).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().let {
                        // Predator: square tinted field (16% accent tint), thin edge, bracket corners and center ticks
                        if (t.predator) it.background(pdTint).border(1.dp, t.pd.edge).bracketCorners(t.pd.edge2)
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
            TopicHeader(
                topic,
                onToggle = { onToggleTopic(topic.id, !topic.collapsed) },
                onLongPress = if (!topic.reminders) ({ taggingTopic = topic }) else null,
            )
            // A collapsed topic shows its header only, as on the web card
            if (topic.collapsed && topic.items.isNotEmpty()) return@forEach
            val topicContent: @Composable () -> Unit = {
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
            if (topic.reminders) {
                val stripeColor = t.accent.copy(alpha = 0.4f)
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).let {
                        if (t.predator) it.background(t.pd.accentTint).border(1.dp, t.pd.edge).bracketCorners(t.pd.edge2).padding(vertical = 6.dp, horizontal = 4.dp)
                        else it.clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                            .background(t.tint.copy(alpha = 0.35f))
                            .drawBehind { drawLine(stripeColor, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), strokeWidth = 2.dp.toPx()) }
                            .padding(start = 4.dp, top = 4.dp, bottom = 6.dp, end = 4.dp)
                    },
                ) { topicContent() }
            } else {
                topicContent()
            }
        }
        // Integrity's buttons that open the side panel (PanelButtonsSection), at the bottom of the card
        if (isIntegrity && panel != null) PanelButtons(panel, quickLinks, openLink, onOpenWeb)
    }
}

/**
 * The quick links icon bar, then Editor, Feeds, Links / Journal, Budget, Alerts in two rows of three
 * (integrity-domain-with-widgets.tsx PanelButtonsSection).
 */
@Composable
private fun PanelButtons(panel: PanelOpener, quickLinks: List<ShortcutUi>, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickLinksBar(panel, quickLinks, openLink, onOpenWeb)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Modifier.weight(1f), LucideIcons.CONTENT, stringResource(R.string.panel_editor)) { panel.open(PanelMode.EDITOR) }
            ActionButton(Modifier.weight(1f), LucideIcons.RSS, stringResource(R.string.today_feeds)) { panel.open(PanelMode.FEEDS) }
            ActionButton(Modifier.weight(1f), LucideIcons.LINK_2, stringResource(R.string.today_links)) { panel.open(PanelMode.LINKS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Modifier.weight(1f), LucideIcons.JOURNAL, stringResource(R.string.today_journal)) { onOpenWeb(WebRoutes.JOURNAL) }
            ActionButton(Modifier.weight(1f), LucideIcons.BUDGET, stringResource(R.string.today_budget)) { onOpenWeb(WebRoutes.BUDGET) }
            ActionButton(Modifier.weight(1f), LucideIcons.ALERTS, stringResource(R.string.panel_alerts)) { panel.open(PanelMode.ALERTS) }
        }
    }
}

/** Icon buttons per row in the quick links bar; more links wrap onto the next row */
private const val QUICK_LINKS_PER_ROW = 7

/**
 * The web's QuickLinksIconBar: a Manage button (the Links tab on its quick links) followed by the user's quick links
 * as icon-only buttons sorted by name. Without links, an "Add quick link" button opens the web's quick links page.
 */
@Composable
private fun QuickLinksBar(panel: PanelOpener, quickLinks: List<ShortcutUi>, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit) {
    val t = LocalTok.current
    val manage = stringResource(R.string.quick_links_manage)
    val links = remember(quickLinks) { quickLinks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }) }
    if (links.isEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickLinkButton(Modifier.weight(1f), manage, accent = true, onClick = panel::openQuickLinks) { Ic(LucideIcons.PANEL, 15.dp, t.accent) }
            ActionButton(Modifier.weight(1f), LucideIcons.ADD, stringResource(R.string.quick_links_add)) { onOpenWeb(WebRoutes.QUICK_LINKS) }
        }
        return
    }
    // null stands for the Manage button, which leads the first row
    val cells: List<ShortcutUi?> = listOf(null) + links
    val perRow = minOf(cells.size, QUICK_LINKS_PER_ROW)
    cells.chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { link ->
                if (link == null) {
                    QuickLinkButton(Modifier.weight(1f), manage, accent = true, onClick = panel::openQuickLinks) { Ic(LucideIcons.PANEL, 15.dp, t.accent) }
                } else {
                    QuickLinkButton(Modifier.weight(1f), link.label, onClick = { openLink(link.url) }) {
                        LinkGlyph(link, LifeMappColors.parseHex(link.color), 15.dp, linkFallback = true)
                    }
                }
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** An icon-only quick link button, outlined like the panel buttons; the Manage button's outline is the accent. */
@Composable
private fun QuickLinkButton(modifier: Modifier, label: String, accent: Boolean = false, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdParallelogramShape(8.dp) else RoundedCornerShape(10.dp)
    Box(
        modifier.height(32.dp).clip(shape).border(1.dp, if (accent) t.accent else t.line, shape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { icon() }
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
                // Predator: the web's target reticle (square well, accent corner brackets and center ticks)
                if (t.predator) it.background(t.pd.well).bracketCorners(t.accent)
                else it.framed(t, t.well, edge = t.well, cut = 8.dp)
            }
            .padding(horizontal = if (t.predator) 40.dp else 12.dp, vertical = if (t.predator) 34.dp else 27.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceptionButton(LucideIcons.MIC, record) { context.startActivity(VoiceInputActivity.intent(context, reminders = true)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(hint, style = t.caption(11.sp), maxLines = 1)
        }
        ReceptionButton(LucideIcons.PENCIL, hint) { context.startActivity(VoiceInputActivity.intent(context, reminders = true, typed = true)) }
    }
}

@Composable
private fun ReceptionButton(icon: Int, desc: String, onClick: () -> Unit) {
    val t = LocalTok.current
    Box(Modifier.clickable(onClickLabel = desc, onClick = onClick)) {
        if (t.predator) {
            // Open hexagon: dashed accent outline over the well, accent icon
            val edge = t.accent.copy(alpha = 0.7f)
            Box(
                Modifier.size(44.dp, 48.dp).drawBehind {
                    val s = 1.dp.toPx()
                    val outline = PdHexagonShape.createOutline(Size(size.width - s, size.height - s), layoutDirection, this)
                    translate(s / 2, s / 2) {
                        drawOutline(
                            outline, edge,
                            style = Stroke(s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Ic(icon, 18.dp, t.accent, desc) }
        }
        else Box(Modifier.size(38.dp).clip(CircleShape).background(t.tint), contentAlignment = Alignment.Center) { Ic(icon, 18.dp, t.onTint, desc) }
    }
}

/**
 * Chevron, numbered hex, name and item count. One tap anywhere on it collapses or expands the topic, as the web
 * card's chevron does; Reminders (always expanded) and a topic without items have nothing to fold.
 */
@Composable
internal fun TopicHeader(topic: TopicUi, onToggle: () -> Unit, onLongPress: (() -> Unit)? = null) {
    val t = LocalTok.current
    val haptics = LocalHapticFeedback.current
    val name = topic.name ?: stringResource(R.string.today_locked)
    val toggles = !topic.reminders && topic.items.isNotEmpty()
    val label = stringResource(if (topic.collapsed) R.string.today_expand else R.string.today_collapse)
    val tagLabel = stringResource(R.string.tag_label_many)
    val latestToggle by rememberUpdatedState(onToggle)
    val latestLongPress by rememberUpdatedState(onLongPress)
    Row(
        Modifier.fillMaxWidth()
            .let {
                if (toggles || onLongPress != null) {
                    it.pointerInput(topic.id, toggles, onLongPress != null) {
                        detectTapGestures(
                            onTap = if (toggles) ({ latestToggle() }) else null,
                            onLongPress = if (onLongPress != null) ({
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestLongPress?.invoke()
                            }) else null,
                        )
                    }.semantics {
                        if (toggles) onClick(label) { latestToggle(); true }
                        if (onLongPress != null) onLongClick(tagLabel) { latestLongPress?.invoke(); true }
                    }
                } else it
            }
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
        if (!topic.reminders) {
            TagBadges(topic.tags, includeMe = false, modifier = Modifier.padding(start = 6.dp))
        }
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
        Text(stringResource(if (topic.reminders) R.string.today_no_reminders else R.string.today_no_content), style = t.body(13.sp, t.text, FontWeight.Medium))
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
            if (item.tags.isNotEmpty()) {
                TagBadges(item.tags, includeMe = true, modifier = Modifier.padding(start = 6.dp))
            }
        }
        ItemMenu(
            item, canMoveUp, canMoveDown,
            expanded = menuOpen, onDismiss = { menuOpen = false }, onAction = { menuOpen = false; onAction(it) },
        )
    }
}
