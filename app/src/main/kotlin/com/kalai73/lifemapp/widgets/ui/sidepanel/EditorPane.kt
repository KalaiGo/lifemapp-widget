package com.kalai73.lifemapp.widgets.ui.sidepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.content.ItemHtml
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelDomain
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelItem
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelReminder
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelTopic
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelUi
import com.kalai73.lifemapp.widgets.core.presentation.panel.ReminderDates
import com.kalai73.lifemapp.widgets.core.presentation.panel.ReminderStatus
import com.kalai73.lifemapp.widgets.core.presentation.panel.SidePanelPresenter
import com.kalai73.lifemapp.widgets.ui.COMPACT_EDITOR_HEIGHT
import com.kalai73.lifemapp.widgets.ui.DiscardDialog
import com.kalai73.lifemapp.widgets.ui.Ic
import com.kalai73.lifemapp.widgets.ui.LocalTok
import com.kalai73.lifemapp.widgets.ui.NumberBadge
import com.kalai73.lifemapp.widgets.ui.RichEditText
import com.kalai73.lifemapp.widgets.ui.RichTextBox
import com.kalai73.lifemapp.widgets.ui.Warn
import com.kalai73.lifemapp.widgets.ui.framed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ── Tree (LinksSidebar) ─────────────────────────────────────────────────────

/** The Folders pane: quick links, all, then domain › topic › item with link counts, and the tag chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PanelTree(ui: PanelUi, state: SidePanelState, vm: SidePanelViewModel) {
    val t = LocalTok.current
    val alerts = state.mode == PanelMode.ALERTS
    // Expanded domains and topics: the selection's, plus what was opened here
    var expanded by remember { mutableStateOf(setOfNotNull(state.scope.domainId, state.scope.topicId)) }
    fun count(n: Int) = n.takeIf { !alerts && state.mode == PanelMode.LINKS }
    Column(Modifier.fillMaxSize()) {
        PaneBar(stringResource(R.string.panel_folders), onBack = { vm.showPane(PanelPane.STREAM) })
        Divider()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            if (!alerts) item {
                TreeRow(0, LucideIcons.ZAP, stringResource(R.string.panel_quick_links), null, state.landing) { vm.showLanding() }
            }
            item {
                val all = stringResource(if (alerts) R.string.panel_all_alerts else if (state.mode == PanelMode.LINKS) R.string.panel_all_links else R.string.panel_all)
                TreeRow(0, if (alerts) LucideIcons.ALERTS else LucideIcons.LINK_2, all, count(ui.linkCount), !state.landing && state.scope.domainId == null) { vm.clearFilters() }
            }
            ui.domains.forEach { d ->
                item(key = d.id) {
                    val open = d.id in expanded
                    TreeRow(
                        0, null, "${d.position.toString().padStart(2, '0')}  ${panelDomainName(d)}", count(d.linkCount),
                        !state.landing && state.scope.domainId == d.id && state.scope.topicId == null,
                        chevron = if (d.topics.isEmpty()) null else open,
                        onChevron = { expanded = if (open) expanded - d.id else expanded + d.id },
                    ) { vm.selectDomain(d.id); expanded = expanded + d.id }
                }
                if (d.id in expanded) d.topics.forEach { topic ->
                    item(key = topic.id) {
                        val open = topic.id in expanded
                        TreeRow(
                            1, null, "${topic.number}. ${topic.name ?: stringResource(R.string.panel_untitled)}", count(topic.linkCount),
                            state.scope.topicId == topic.id && state.scope.itemId == null,
                            chevron = if (topic.items.isEmpty()) null else open,
                            onChevron = { expanded = if (open) expanded - topic.id else expanded + topic.id },
                        ) { vm.selectTopic(d.id, topic.id); expanded = expanded + topic.id }
                    }
                    if (topic.id in expanded) topic.items.forEach { item ->
                        item(key = item.id) {
                            TreeRow(
                                2, if (item.isImage) LucideIcons.IMAGE else if (item.reminder != null) LucideIcons.ALERTS else null,
                                item.name ?: stringResource(R.string.panel_untitled), count(item.linkCount),
                                state.scope.itemId == item.id,
                            ) { vm.selectItem(d.id, topic.id, item.id) }
                        }
                    }
                }
            }
            if (ui.tagCounts.isNotEmpty() || ui.domains.isNotEmpty()) item {
                Column(Modifier.padding(12.dp)) {
                    Text(t.s(stringResource(R.string.panel_tags)), style = t.caption(10.sp))
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val tags = (ui.tagCounts.map { it.first } + ItemTagsList).distinct()
                        tags.forEach { tag ->
                            val selected = state.scope.tag == tag
                            PanelChip("#${tagLabel(tag, ui)}", selected, count = ui.tagCounts.firstOrNull { it.first == tag }?.second) {
                                vm.selectTag(if (selected) null else tag)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ItemTagsList = listOf("me", "work", "family")

@Composable
private fun TreeRow(
    depth: Int, icon: Int?, label: String, count: Int?, selected: Boolean,
    chevron: Boolean? = null, onChevron: () -> Unit = {}, onClick: () -> Unit,
) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().background(if (selected) t.tint else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(start = (8 + depth * 16).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chevron != null) {
            Box(Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onChevron), contentAlignment = Alignment.Center) {
                Ic(if (chevron) LucideIcons.CHEVRON_DOWN else LucideIcons.CHEVRON_RIGHT, 14.dp, t.dim)
            }
        } else Spacer(Modifier.width(22.dp))
        icon?.let { Ic(it, 13.dp, if (selected) t.onTint else t.dim); Spacer(Modifier.width(6.dp)) }
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            style = t.body(if (depth == 0) 13.sp else 12.sp, if (selected) t.onTint else t.text, if (depth == 0 || selected) FontWeight.SemiBold else FontWeight.Normal),
        )
        count?.takeIf { it > 0 }?.let { Text("$it", style = t.caption(10.sp)) }
    }
}

// ── Structure view (LinksStructureView) ─────────────────────────────────────

/** The Editor tab without an item: the scoped domain's info box, its topics (renamable) and their items. */
@Composable
internal fun StructureView(vm: SidePanelViewModel, ui: PanelUi, state: SidePanelState) {
    val domains = ui.domain(state.scope.domainId)?.let(::listOf) ?: ui.domains
    val topicFilter = state.scope.topicId
    val query = state.scope.query.trim().lowercase()
    val tag = state.scope.tag
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ui.domains.isEmpty()) item { EmptyState(stringResource(R.string.panel_no_topics)) }
        domains.forEach { d ->
            item(key = "d-${d.id}") { DomainHeader(vm, d, editable = !ui.locked) }
            val topics = d.topics.filter { topicFilter == null || it.id == topicFilter }
            if (topics.isEmpty()) item(key = "empty-${d.id}") { EmptyState(stringResource(R.string.panel_no_topics)) }
            topics.forEach { topic ->
                val items = topic.items.filter { (tag == null || tag in it.tags) && (query.isEmpty() || it.name.orEmpty().lowercase().contains(query)) }
                item(key = "t-${topic.id}") { TopicCard(vm, d, topic, items, editable = !ui.locked) }
            }
        }
    }
}

@Composable
private fun DomainHeader(vm: SidePanelViewModel, d: PanelDomain, editable: Boolean) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.position.toString().padStart(2, '0'), style = t.label(18.sp, t.faint))
            Spacer(Modifier.width(8.dp))
            Text(t.s(panelDomainName(d)), style = if (t.predator) t.label(15.sp) else t.body(15.sp, t.ink, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        // DomainInfoBox: tap to edit inline; Done or leaving the field saves
        InlineTextEdit(
            value = d.infoBox.orEmpty(), hint = stringResource(R.string.panel_info_box_hint), enabled = editable,
            style = if (t.predator) t.caption(12.sp, t.text) else t.body(12.sp, t.onTint),
            modifier = Modifier.fillMaxWidth().framed(t, t.tint, edge = if (t.predator) t.pd.edge else t.tint, cut = 6.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        ) { vm.saveInfoBox(d.id, it) }
    }
}

@Composable
private fun TopicCard(vm: SidePanelViewModel, d: PanelDomain, topic: PanelTopic, items: List<PanelItem>, editable: Boolean) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().framed(t, t.card, edge = t.line, cut = 8.dp).padding(vertical = 6.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(topic.number)
            Spacer(Modifier.width(8.dp))
            // SubdomainHeader: the topic name renames inline
            InlineTextEdit(
                value = topic.name.orEmpty(), hint = stringResource(R.string.panel_untitled), enabled = editable && topic.name != null,
                style = if (t.predator) t.label(13.sp) else t.body(13.sp, t.ink, FontWeight.Bold), singleLine = true,
                modifier = Modifier.weight(1f),
            ) { if (it.isNotBlank()) vm.renameTopic(topic.id, it) }
        }
        if (items.isEmpty()) Text(stringResource(R.string.panel_no_items), style = t.body(12.sp, t.dim), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        items.forEachIndexed { index, item ->
            StructureItemRow(
                item, canUp = index > 0, canDown = index < topic.items.lastIndex,
                onOpen = { vm.selectItem(d.id, topic.id, item.id) },
                onMove = { up -> vm.moveItem(item.id, topic.id, up) },
            )
        }
    }
}

@Composable
private fun StructureItemRow(item: PanelItem, canUp: Boolean, canDown: Boolean, onOpen: () -> Unit, onMove: (Boolean) -> Unit) {
    val t = LocalTok.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.warning) { Ic(LucideIcons.WARNING, 12.dp, Warn); Spacer(Modifier.width(6.dp)) }
            Column(Modifier.weight(1f)) {
                Text(item.name ?: stringResource(R.string.panel_untitled), style = t.body(13.sp, t.text, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val preview = item.content?.let { ItemHtml.toSpanned(it).toString().replace(Regex("\\s+"), " ").trim() }.orEmpty()
                if (preview.isNotEmpty()) Text(preview, style = t.body(11.sp, t.dim), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            item.reminder?.let { Spacer(Modifier.width(6.dp)); ReminderChip(it, compact = true) }
            if (item.isImage) Ic(LucideIcons.IMAGE, 13.dp, t.dim)
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.panel_open_editor)) }, { menu = false; onOpen() }, leadingIcon = { Ic(LucideIcons.EDIT) })
            if (canUp) DropdownMenuItem({ Text(stringResource(R.string.item_move_up)) }, { menu = false; onMove(true) }, leadingIcon = { Ic(LucideIcons.ARROW_UP) })
            if (canDown) DropdownMenuItem({ Text(stringResource(R.string.item_move_down)) }, { menu = false; onMove(false) }, leadingIcon = { Ic(LucideIcons.ARROW_DOWN) })
        }
    }
}

/** Text shown as is until tapped, then edited in place: the keyboard's Done or leaving the field saves a change. */
@Composable
private fun InlineTextEdit(
    value: String, hint: String, enabled: Boolean, style: TextStyle, modifier: Modifier,
    singleLine: Boolean = false, onSave: (String) -> Unit,
) {
    val t = LocalTok.current
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val commit = { if (text.trim() != value.trim()) onSave(text) }
    Box(modifier) {
        if (text.isEmpty()) Text(hint, style = style.copy(color = t.dim.copy(alpha = 0.6f)))
        BasicTextField(
            text, { text = it }, enabled = enabled, textStyle = style, singleLine = singleLine, cursorBrush = SolidColor(t.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                if (focused && !state.isFocused) commit()
                focused = state.isFocused
            },
        )
    }
}

// ── Alerts (LinksStream reminders mode) ─────────────────────────────────────

@Composable
internal fun AlertsStream(vm: SidePanelViewModel, ui: PanelUi, state: SidePanelState) {
    val t = LocalTok.current
    val names = ui.domains.associate { it.id to panelDomainName(it) }
    val alerts = remember(ui, state.scope, state.sort, names) {
        SidePanelPresenter.filterAlerts(ui, state.scope, state.sort) { names[it.id].orEmpty() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        if (alerts.isEmpty()) item {
            EmptyState(stringResource(R.string.panel_no_alerts), stringResource(R.string.panel_show_all).takeIf { state.scope != com.kalai73.lifemapp.widgets.core.presentation.panel.PanelScope() }) { vm.clearFilters() }
        }
        items(alerts, key = { it.item.id }) { (item, d, topic) ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.selectItem(d.id, topic.id, item.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.name ?: stringResource(R.string.panel_untitled), style = t.body(13.sp, t.text, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                item.reminder?.let { ReminderChip(it, compact = true) }
            }
            Divider()
        }
    }
}

// ── Reminder (reminder-picker.tsx) ──────────────────────────────────────────

@Composable
internal fun reminderDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> stringResource(R.string.panel_today)
    today.plusDays(1) -> stringResource(R.string.panel_tomorrow)
    today.minusDays(1) -> stringResource(R.string.panel_yesterday)
    else -> date.format(if (date.year == today.year) DateTimeFormatter.ofPattern("d MMM") else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

@Composable
internal fun ruleLabel(rule: String): String = when (ReminderDates.RECURRENCE_PRESETS.indexOf(rule)) {
    0 -> stringResource(R.string.panel_rule_daily)
    1 -> stringResource(R.string.panel_rule_weekdays)
    2 -> stringResource(R.string.panel_rule_weekly)
    3 -> stringResource(R.string.panel_rule_biweekly)
    4 -> stringResource(R.string.panel_rule_monthly)
    5 -> stringResource(R.string.panel_rule_bimonthly)
    6 -> stringResource(R.string.panel_rule_quarterly)
    7 -> stringResource(R.string.panel_rule_every_6_months)
    8 -> stringResource(R.string.panel_rule_yearly)
    else -> stringResource(R.string.panel_rule_custom)
}

@Composable
private fun statusLabel(status: ReminderStatus): String = stringResource(
    when (status) {
        ReminderStatus.OVERDUE -> R.string.panel_status_overdue
        ReminderStatus.DUE -> R.string.panel_status_due
        ReminderStatus.CREATED -> R.string.panel_status_created
        ReminderStatus.UPCOMING -> R.string.panel_status_upcoming
    },
)

/** The reminder badge: date (and repeat), colored by status */
@Composable
internal fun ReminderChip(reminder: PanelReminder, compact: Boolean) {
    val t = LocalTok.current
    val color = statusColor(reminder.status, t.dim, t.dark)
    Row(
        Modifier.framed(t, color.copy(alpha = 0.12f), edge = color.copy(alpha = 0.35f), cut = 3.dp).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(LucideIcons.CALENDAR, 11.dp, color)
        if (reminder.rule != null) { Spacer(Modifier.width(2.dp)); Ic(LucideIcons.REPEAT, 11.dp, color) }
        Spacer(Modifier.width(4.dp))
        val text = reminderDateLabel(reminder.date) + if (!compact) " · ${statusLabel(reminder.status)}" else ""
        Text(text, style = t.body(10.sp, color, FontWeight.SemiBold), maxLines = 1)
    }
}

/** What the reminder sheet edits: the web's ReminderValue */
private data class ReminderValue(val date: String?, val rule: String?, val leadDays: Int)

/** ReminderPanel as a bottom sheet: quick days, a date, repeat presets and when the task is created. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderSheet(value: ReminderValue, onChange: (ReminderValue) -> Unit, onDismiss: () -> Unit) {
    val t = LocalTok.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var picking by remember { mutableStateOf(false) }
    val setDay = { day: LocalDate -> onChange(value.copy(date = ReminderDates.atNine(day, zone))); onDismiss() }
    val currentDay = ReminderDates.parse(value.date, zone)
    if (picking) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (currentDay ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    pickerState.selectedDateMillis?.let { setDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text(stringResource(R.string.panel_save)) }
            },
            dismissButton = { TextButton({ picking = false }) { Text(stringResource(R.string.panel_cancel)) } },
        ) { DatePicker(pickerState) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t.s(stringResource(R.string.panel_reminder)), style = if (t.predator) t.label(14.sp) else t.body(15.sp, t.ink, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelChip(stringResource(R.string.panel_today), currentDay == today) { setDay(today) }
                PanelChip(stringResource(R.string.panel_tomorrow), currentDay == today.plusDays(1)) { setDay(today.plusDays(1)) }
                PanelChip(stringResource(R.string.panel_in_7_days), currentDay == today.plusDays(7)) { setDay(today.plusDays(7)) }
                PanelChip(currentDay?.let { reminderDateLabel(it, today) } ?: stringResource(R.string.panel_pick_date), false, icon = LucideIcons.CALENDAR) { picking = true }
                if (value.date != null) PanelChip(stringResource(R.string.panel_clear), false) { onChange(ReminderValue(null, null, value.leadDays)); onDismiss() }
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(LucideIcons.REPEAT, 14.dp, t.ink)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.panel_repeat), style = t.body(12.sp, t.dim, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                if (value.rule != null) Text(
                    stringResource(R.string.panel_dont_repeat), style = t.body(11.sp, t.destructive),
                    modifier = Modifier.clip(CircleShape).clickable { onChange(value.copy(rule = null)) }.padding(4.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReminderDates.RECURRENCE_PRESETS.forEach { rule ->
                    val selected = value.rule == rule
                    PanelChip(ruleLabel(rule), selected) {
                        onChange(value.copy(date = value.date ?: ReminderDates.atNine(today, zone), rule = if (selected) null else rule))
                    }
                }
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(LucideIcons.TASKS, 14.dp, t.ink)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.panel_task_lead), style = t.body(12.sp, t.dim, FontWeight.SemiBold))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReminderDates.LEAD_DAYS_PRESETS.forEach { days ->
                    PanelChip(if (days == 0) stringResource(R.string.panel_lead_on_day) else stringResource(R.string.panel_lead_before, days), value.leadDays == days) {
                        onChange(value.copy(date = value.date ?: ReminderDates.atNine(today, zone), leadDays = days))
                    }
                }
            }
        }
    }
}

// ── Inline editor (SubsubdomainInlineEditor) ────────────────────────────────

/** How long the editor waits after the last change before it saves (useAutoSave) */
private const val AUTOSAVE_DELAY_MS = 1500L

/**
 * An item opened in the panel: its name, the rich text editor and the reminder, saved as you type (after a short
 * pause, and when the item is left). Over the plan's character limit nothing is saved until it's back under.
 */
@Composable
internal fun InlineItemEditor(vm: SidePanelViewModel, ui: PanelUi, item: PanelItem) {
    val t = LocalTok.current
    val scope = rememberCoroutineScope()
    var loaded by remember(item.id) { mutableStateOf<ItemText?>(null) }
    var loadFailed by remember(item.id) { mutableStateOf(false) }
    var editor by remember(item.id) { mutableStateOf<RichEditText?>(null) }
    var tick by remember(item.id) { mutableIntStateOf(0) }
    var name by remember(item.id) { mutableStateOf(item.name.orEmpty()) }
    var savedName by remember(item.id) { mutableStateOf(item.name.orEmpty()) }
    /** The editor's text as last saved, to tell edits from what is stored */
    var savedHtml by remember(item.id) { mutableStateOf<String?>(null) }
    var status by remember(item.id) { mutableStateOf(SaveStatus.IDLE) }
    var reminderOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        loaded = try {
            vm.loadItemText(item.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            loadFailed = true; null
        } catch (_: Exception) {
            loadFailed = true; null
        }
    }

    val ready = loaded as? ItemText.Loaded
    val html = if (tick >= 0 && editor?.changed == true) editor?.text?.let(ItemHtml::toHtml) else null
    val length = if (tick >= 0) editor?.text?.length ?: 0 else 0
    val maxChars = ready?.maxChars ?: ui.limits.maxItemChars
    val overLimit = length > maxChars
    val nameDirty = name.trim().isNotEmpty() && name.trim() != savedName && item.name != null
    val textDirty = html != null && html != savedHtml
    val dirty = nameDirty || textDirty

    // Saves the latest name and text; a newer edit made while it ran is saved by the next round
    val save: suspend () -> Unit = save@{
        if (!dirty || overLimit) return@save
        val newName = name.trim().takeIf { nameDirty }
        val newHtml = html.takeIf { textDirty }
        status = SaveStatus.SAVING
        if (vm.saveItem(item.id, newName, newHtml)) {
            newName?.let { savedName = it }
            newHtml?.let { savedHtml = it }
            status = SaveStatus.SAVED
        } else status = SaveStatus.FAILED
    }
    val latestSave by rememberUpdatedState(save)
    LaunchedEffect(item.id, tick, name) {
        if (!dirty) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        latestSave()
    }
    // Leaving the item (another item, Back, closing the panel) saves what is pending, in the view model's scope
    // since this composable's own scope ends with it
    val pending by rememberUpdatedState(if (dirty && !overLimit) name.trim().takeIf { nameDirty } to html.takeIf { textDirty } else null)
    DisposableEffect(item.id) {
        onDispose { pending?.let { (n, h) -> vm.saveItemDetached(item.id, n, h) } }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.item_remove_title)) },
        text = { Text(item.name ?: stringResource(R.string.panel_untitled)) },
        confirmButton = { TextButton({ confirmDelete = false; vm.deleteItem(item.id); vm.back() }) { Text(stringResource(R.string.item_remove), color = t.destructive) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.panel_cancel)) } },
    )
    if (reminderOpen) {
        val r = item.reminder
        ReminderSheet(
            ReminderValue(r?.value, r?.rule, r?.leadDays ?: 0),
            onChange = { vm.saveReminder(item.id, it.date, it.rule, it.leadDays) },
            onDismiss = { reminderOpen = false },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PaneBar(
            ui.topic(item.topicId)?.name ?: stringResource(R.string.panel_untitled),
            onBack = { vm.back() },
        ) {
            SaveIndicator(status, overLimit) { scope.launch { latestSave() } }
            PanelIconButton(LucideIcons.TRASH, stringResource(R.string.item_remove), enabled = !ui.locked) { confirmDelete = true }
        }
        Divider()
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Title
            Box {
                val titleStyle = t.body(19.sp, t.text, FontWeight.SemiBold)
                if (name.isEmpty()) Text(stringResource(R.string.item_title_hint), style = titleStyle.copy(color = t.dim.copy(alpha = 0.4f)))
                BasicTextField(
                    name, { name = it }, enabled = item.name != null && !ui.locked, textStyle = titleStyle, maxLines = 2,
                    cursorBrush = SolidColor(t.accent), modifier = Modifier.fillMaxWidth(),
                )
            }
            // Reminder and tags
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val r = item.reminder
                Box(Modifier.clip(CircleShape).clickable(enabled = !ui.locked) { reminderOpen = true }) {
                    if (r != null) ReminderChip(r, compact = false)
                    else PanelChip(stringResource(R.string.panel_set_reminder), false, icon = LucideIcons.CALENDAR) { reminderOpen = true }
                }
                if (r != null) PanelIconButton(LucideIcons.CLOSE, stringResource(R.string.panel_clear_reminder), size = 24.dp) {
                    vm.saveReminder(item.id, null, null, r.leadDays)
                }
                Spacer(Modifier.weight(1f))
                item.tags.forEach { Text("#${tagLabel(it, ui)}", style = t.caption(10.sp, t.ink)) }
            }
            when {
                item.isImage || loaded == ItemText.Image -> Text(stringResource(R.string.item_text_image), style = t.body(12.sp, t.dim))
                loaded == ItemText.Locked -> Text(stringResource(R.string.error_item_locked), style = t.body(12.sp, t.dim))
                loaded == ItemText.Gone -> Text(stringResource(R.string.item_gone), style = t.body(12.sp, t.dim))
                loaded == ItemText.Unreadable -> Text(stringResource(R.string.item_text_unreadable), style = t.body(12.sp, t.dim))
                loadFailed -> Text(stringResource(R.string.item_text_failed), style = t.body(12.sp, t.dim))
                ready == null -> Box(Modifier.fillMaxWidth().heightIn(min = COMPACT_EDITOR_HEIGHT), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = t.accent, strokeWidth = 2.dp)
                }
                else -> {
                    RichTextBox(
                        html = ready.html, hint = stringResource(R.string.item_text_hint), height = 420.dp,
                        editor = editor, tick = tick, focus = false,
                        onEditor = { editor = it }, onChange = { tick++ },
                    )
                    if (ItemHtml.hasUnsupportedFormatting(ready.html)) Text(stringResource(R.string.item_text_simplified), style = t.body(11.sp, t.dim))
                    Text(
                        "$length/$maxChars", style = if (overLimit) t.body(11.sp, t.destructive, FontWeight.SemiBold) else t.caption(10.sp),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

/** AutoSaveIndicator: Saving… / Saved / Not saved (tap to retry) */
@Composable
private fun SaveIndicator(status: SaveStatus, overLimit: Boolean, onRetry: () -> Unit) {
    val t = LocalTok.current
    val (text, color) = when {
        overLimit -> stringResource(R.string.panel_save_failed) to t.destructive
        status == SaveStatus.SAVING -> stringResource(R.string.panel_saving) to t.dim
        status == SaveStatus.SAVED -> stringResource(R.string.panel_saved) to t.dim
        status == SaveStatus.FAILED -> stringResource(R.string.panel_save_failed) to t.destructive
        else -> return
    }
    Text(
        text, style = t.caption(10.sp, color), maxLines = 1,
        modifier = Modifier.clip(CircleShape).clickable(enabled = status == SaveStatus.FAILED, onClick = onRetry).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
