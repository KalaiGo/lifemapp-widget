package com.kalai73.lifemapp.widgets.ui.sidepanel

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.presentation.ShortcutUi
import com.kalai73.lifemapp.widgets.core.presentation.panel.LinkExtractor
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelDomain
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelLink
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelScope
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelSort
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelUi
import com.kalai73.lifemapp.widgets.core.presentation.panel.SidePanelPresenter
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.ui.Ic
import com.kalai73.lifemapp.widgets.ui.LocalTok
import com.kalai73.lifemapp.widgets.ui.LockedBanner
import com.kalai73.lifemapp.widgets.ui.NumberBadge
import com.kalai73.lifemapp.widgets.ui.ShortcutGrid
import com.kalai73.lifemapp.widgets.ui.domainName

/**
 * LinksContent on a phone: the tree ([PanelTree], "Folders") or the stream for the current tab: Editor (structure
 * view, or the inline editor for an item), Links (link rows), Alerts (items with a reminder); the quick links
 * landing replaces the stream when chosen in the tree.
 */
@Composable
internal fun LinksPane(
    vm: SidePanelViewModel,
    state: SidePanelState,
    shortcuts: List<ShortcutUi>,
    openLink: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    val ui by vm.panel.collectAsState()
    if (state.pane == PanelPane.SIDEBAR) {
        PanelTree(ui, state, vm)
        return
    }
    val item = ui.item(state.scope.itemId)
    Column(Modifier.fillMaxSize()) {
        if (ui.locked) Box(Modifier.padding(horizontal = 12.dp)) { LockedBanner(onUnlock) }
        when {
            state.landing -> QuickLinksLanding(shortcuts, openLink, onOpenWeb) { vm.showPane(PanelPane.SIDEBAR) }
            item != null && state.mode != PanelMode.LINKS -> InlineItemEditor(vm, ui, item)
            else -> {
                StreamHeader(ui, state, vm)
                when (state.mode) {
                    PanelMode.EDITOR -> StructureView(vm, ui, state)
                    PanelMode.ALERTS -> AlertsStream(vm, ui, state)
                    else -> LinksStream(vm, ui, state, openLink)
                }
            }
        }
    }
}

/** The domain's name, its default by position when it has none (locked) */
@Composable
internal fun panelDomainName(d: PanelDomain): String = domainName(d.name, d.position)

/** Scope button, search, and (Links, Alerts) sort and layout: LinksStream's header */
@Composable
private fun StreamHeader(ui: PanelUi, state: SidePanelState, vm: SidePanelViewModel) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
        val scope = scopeLabel(ui, state)
        ScopeButton(scope, stringResource(R.string.panel_links_scope, scope), Modifier.fillMaxWidth()) { vm.showPane(PanelPane.SIDEBAR) }
        Spacer(Modifier.height(8.dp))
        SearchField(state.scope.query, vm::setQuery, Modifier.fillMaxWidth())
        if (state.mode != PanelMode.EDITOR) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val links = state.mode == PanelMode.LINKS
                // Links have no Structure chip: content order is the default, and tapping the selected sort again returns to it
                val sortBy = { sort: PanelSort -> vm.setSort(if (links && state.sort == sort) PanelSort.STRUCTURE else sort) }
                if (!links) PanelChip(stringResource(R.string.panel_sort_date), state.sort == PanelSort.STRUCTURE) { vm.setSort(PanelSort.STRUCTURE) }
                PanelChip(stringResource(R.string.panel_sort_newest), state.sort == PanelSort.NEWEST) { sortBy(PanelSort.NEWEST) }
                PanelChip(stringResource(R.string.panel_sort_alpha), state.sort == PanelSort.ALPHA, icon = LucideIcons.SORT_ALPHA) { sortBy(PanelSort.ALPHA) }
                if (links) {
                    Box(Modifier.width(1.dp).height(20.dp).background(t.line))
                    HeadingToggles(ui, state, vm)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Divider()
}

/**
 * LinksStream's scopeLabel: "13 Mastery › Reading › Item" down to the selected level, else the tag, else
 * All links / All alerts.
 */
@Composable
private fun scopeLabel(ui: PanelUi, state: SidePanelState): String {
    val t = LocalTok.current
    val scope = state.scope
    val untitled = stringResource(R.string.panel_untitled)
    val path = listOfNotNull(
        ui.domain(scope.domainId)?.let { "${it.position.toString().padStart(2, '0')} ${panelDomainName(it)}" },
        ui.topic(scope.topicId)?.let { it.name ?: untitled },
        ui.item(scope.itemId)?.let { it.name ?: untitled },
    )
    val tag = scope.tag?.let { "#${tagLabel(it, ui)}" }
    return when {
        path.isNotEmpty() -> path.joinToString(" › ") + (tag?.let { " · $it" } ?: "")
        tag != null -> tag
        else -> t.s(stringResource(if (state.mode == PanelMode.ALERTS) R.string.panel_all_alerts else if (state.mode == PanelMode.LINKS) R.string.panel_all_links else R.string.panel_all))
    }
}

@Composable
internal fun tagLabel(tag: String, ui: PanelUi): String = when (tag) {
    "me" -> stringResource(R.string.tag_me)
    "work" -> stringResource(R.string.tag_work)
    "family" -> stringResource(R.string.tag_family)
    else -> ui.customTags[tag] ?: tag
}

/** H1–H6 toggles: a link is hidden when any heading it sits under is switched off */
@Composable
private fun HeadingToggles(ui: PanelUi, state: SidePanelState, vm: SidePanelViewModel) {
    val present = remember(ui.links) { (setOf(1, 2, 3) + ui.links.flatMap { it.headingLevels }).sorted() }
    val allHidden = (1..6).all { it in state.hiddenHeadingLevels }
    PanelChip(stringResource(R.string.panel_all), !allHidden && state.hiddenHeadingLevels.isEmpty()) { vm.toggleHeadingLevel(null) }
    present.forEach { level -> PanelChip("H$level", level !in state.hiddenHeadingLevels) { vm.toggleHeadingLevel(level) } }
}

// ── Links ───────────────────────────────────────────────────────────────────

@Composable
private fun LinksStream(vm: SidePanelViewModel, ui: PanelUi, state: SidePanelState, openLink: (String) -> Unit) {
    val t = LocalTok.current
    val names = ui.domains.associate { it.id to panelDomainName(it) }
    val filtered = remember(ui, state.scope, state.sort, names) {
        SidePanelPresenter.filterLinks(ui, state.scope, state.sort) { names[it.id].orEmpty() }
    }
    val visible = remember(filtered, state.hiddenHeadingLevels) {
        if (state.hiddenHeadingLevels.isEmpty()) filtered else filtered.filterNot { link -> link.headingLevels.any { it in state.hiddenHeadingLevels } }
    }
    val untitled = stringResource(R.string.panel_untitled)
    val sections = remember(visible, state.sort, state.scope, untitled) { SidePanelPresenter.linkSections(visible, state.sort, state.scope, untitled) }
    val openItem = { domainId: String, topicId: String, itemId: String -> vm.setMode(PanelMode.EDITOR); vm.selectItem(domainId, topicId, itemId) }
    var editing by remember { mutableStateOf<PanelLink?>(null) }
    var removing by remember { mutableStateOf<PanelLink?>(null) }
    editing?.let { link -> EditLinkDialog(link, onSave = { url, label -> vm.updateLink(link.itemId, link.url, url, label); editing = null }, onDismiss = { editing = null }) }
    removing?.let { link ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.panel_remove_link)) },
            text = { Text(stringResource(R.string.panel_remove_link_text, link.itemName ?: stringResource(R.string.panel_untitled))) },
            confirmButton = { TextButton({ vm.removeLink(link.itemId, link.url); removing = null }) { Text(stringResource(R.string.item_remove)) } },
            dismissButton = { TextButton({ removing = null }) { Text(stringResource(R.string.panel_cancel)) } },
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        if (visible.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.panel_no_links),
                    stringResource(R.string.panel_show_all).takeIf { state.scope != PanelScope() || state.hiddenHeadingLevels.isNotEmpty() },
                ) {
                    vm.clearFilters()
                    if (state.hiddenHeadingLevels.isNotEmpty()) vm.toggleHeadingLevel(null)
                }
            }
        }
        val entry: @Composable (PanelLink) -> Unit = { link ->
            LinkEntry(
                link,
                onOpen = { openLink(link.url) },
                onEditItem = { openItem(link.domainId, link.topicId, link.itemId) },
                onEdit = { editing = link },
                onRemove = { removing = link },
            )
        }
        if (sections == null) {
            items(visible, key = { it.id }) { entry(it) }
        } else {
            sections.forEachIndexed { index, section ->
                if (section.title.isNotBlank()) item(key = "h-${section.key}") {
                    SectionHeader(section, first = index == 0, editable = !ui.locked) { openItem(section.domainId, section.topicId, section.itemId) }
                }
                items(section.links, key = { it.id }) { entry(it) }
            }
        }
        val hidden = filtered.size - visible.size
        if (hidden > 0) item { Text(stringResource(R.string.panel_hidden_links, hidden), style = t.caption(10.sp), modifier = Modifier.padding(8.dp)) }
    }
}

/** A section's header (LinksStream): topic › item › headings, its link count, and an editor shortcut */
@Composable
private fun SectionHeader(section: SidePanelPresenter.LinkSection, first: Boolean, editable: Boolean, onEditItem: () -> Unit) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(top = if (first) 0.dp else 12.dp)) {
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                section.context.forEach { part ->
                    Text(part.label, style = t.body(11.sp, if (part.isItem) t.ink else t.dim, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(" › ", style = t.caption(11.sp))
                }
                Text(section.title, style = t.body(12.sp, t.text, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.width(6.dp))
            Text(section.links.size.toString(), style = t.caption(10.sp))
            if (editable) PanelIconButton(LucideIcons.EDIT, stringResource(R.string.panel_open_editor), size = 28.dp, onClick = onEditItem)
        }
        Divider()
    }
}

@Composable
private fun LinkEntry(link: PanelLink, onOpen: () -> Unit, onEditItem: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    val t = LocalTok.current
    val context = LocalContext.current
    val copied = stringResource(R.string.item_link_copied)
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(t.tint), contentAlignment = Alignment.Center) {
                Ic(LucideIcons.resolve(if (link.hostname.contains("youtu")) "youtube" else "globe") ?: LucideIcons.LINK, 14.dp, t.onTint)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(link.label, style = t.body(13.sp, t.text, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(link.hostname, style = t.caption(10.sp), maxLines = 1)
            }
            PanelIconButton(LucideIcons.EXTERNAL_LINK, stringResource(R.string.panel_feeds_open_original), size = 28.dp, onClick = onOpen)
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.panel_open_editor)) }, { menu = false; onEditItem() }, leadingIcon = { Ic(LucideIcons.CONTENT) })
            DropdownMenuItem({ Text(stringResource(R.string.panel_edit_link)) }, { menu = false; onEdit() }, leadingIcon = { Ic(LucideIcons.EDIT) })
            DropdownMenuItem({ Text(stringResource(R.string.panel_copy_link)) }, {
                menu = false
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(link.label, link.url))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }, leadingIcon = { Ic(LucideIcons.LINK_2) })
            DropdownMenuItem({ Text(stringResource(R.string.panel_remove_link)) }, { menu = false; onRemove() }, leadingIcon = { Ic(LucideIcons.TRASH, tint = t.destructive) })
        }
    }
    Divider()
}

/** EditLinkDialog: the link's address and label, written back into its item's text */
@Composable
private fun EditLinkDialog(link: PanelLink, onSave: (url: String, label: String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(link.url) }
    var label by rememberSaveable { mutableStateOf(link.anchorLabel) }
    val valid = LinkExtractor.normalizeExternalUrl(url) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.panel_edit_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.panel_link_url)) }, singleLine = true, isError = !valid,
                    supportingText = { if (!valid) Text(stringResource(R.string.panel_link_invalid)) })
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.panel_link_label)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ onSave(url, label) }, enabled = valid) { Text(stringResource(R.string.panel_save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.panel_cancel)) } },
    )
}

/** QuickLinksLanding: the quick links grid; they are added and changed on the web */
@Composable
private fun QuickLinksLanding(shortcuts: List<ShortcutUi>, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit, onFolders: () -> Unit) {
    val t = LocalTok.current
    Column(Modifier.fillMaxSize()) {
        PaneBar(stringResource(R.string.panel_quick_links)) {
            PanelIconButton(LucideIcons.FOLDER, stringResource(R.string.panel_folders), tint = t.ink, onClick = onFolders)
        }
        Divider()
        Column(Modifier.padding(12.dp)) {
            if (shortcuts.isEmpty()) EmptyState(stringResource(R.string.today_no_links))
            else ShortcutGrid(shortcuts, openLink)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.panel_quick_links_manage), style = t.body(13.sp, t.accent, FontWeight.SemiBold),
                modifier = Modifier.clip(CircleShape).clickable { onOpenWeb(WebRoutes.QUICK_LINKS) }.padding(8.dp),
            )
        }
    }
}

/** A reminder status's color (getReminderBadgeStyles): red overdue, amber due, emerald created, muted upcoming */
internal fun statusColor(status: com.kalai73.lifemapp.widgets.core.presentation.panel.ReminderStatus, dim: Color, dark: Boolean): Color = when (status) {
    com.kalai73.lifemapp.widgets.core.presentation.panel.ReminderStatus.OVERDUE -> if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
    com.kalai73.lifemapp.widgets.core.presentation.panel.ReminderStatus.DUE -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
    com.kalai73.lifemapp.widgets.core.presentation.panel.ReminderStatus.CREATED -> if (dark) Color(0xFF34D399) else Color(0xFF059669)
    com.kalai73.lifemapp.widgets.core.presentation.panel.ReminderStatus.UPCOMING -> dim
}
