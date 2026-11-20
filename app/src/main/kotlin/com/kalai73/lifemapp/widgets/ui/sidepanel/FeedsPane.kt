package com.kalai73.lifemapp.widgets.ui.sidepanel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.repository.FeedRepository
import com.kalai73.lifemapp.widgets.core.data.repository.FeedState
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.model.Feed
import com.kalai73.lifemapp.widgets.core.model.FeedFilter
import com.kalai73.lifemapp.widgets.core.model.FeedFolder
import com.kalai73.lifemapp.widgets.core.model.FeedItem
import com.kalai73.lifemapp.widgets.core.model.SmartView
import com.kalai73.lifemapp.widgets.ui.Ic
import com.kalai73.lifemapp.widgets.ui.LocalTok
import com.kalai73.lifemapp.widgets.ui.framed
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** FeedsContent on a phone: sidebar (smart views, folders, feeds), stream, reader; one pane at a time. */
@Composable
internal fun FeedsPane(vm: SidePanelViewModel, openLink: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val feeds by vm.feeds.collectAsState()
    when {
        state.pane == PanelPane.SIDEBAR -> FeedSidebar(vm, feeds)
        state.pane == PanelPane.READER && state.feedItemId != null ->
            feeds.items.firstOrNull { it.id == state.feedItemId }?.let { FeedReader(vm, it, feeds, openLink) } ?: FeedStream(vm, feeds)
        else -> FeedStream(vm, feeds)
    }
}

// ── Sidebar ─────────────────────────────────────────────────────────────────

private sealed interface FeedDialog {
    data class AddFeed(val folderId: String?) : FeedDialog
    data class EditFeed(val feed: Feed) : FeedDialog
    data class DeleteFeed(val feed: Feed) : FeedDialog
    data class NewFolder(val parentId: String?) : FeedDialog
    data class DeleteFolder(val folder: FeedFolder) : FeedDialog
}

@Composable
private fun FeedSidebar(vm: SidePanelViewModel, feeds: FeedState) {
    val t = LocalTok.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filter = feeds.filter
    var dialog by remember { mutableStateOf<FeedDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val failed = stringResource(R.string.panel_opml_failed)
    val exported = stringResource(R.string.panel_opml_exported)

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val xml = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        if (xml.isNullOrBlank()) android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_LONG).show() else vm.importOpml(xml)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val xml = vm.exportOpml() ?: return@launch
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray()) } != null }.getOrDefault(false)
            android.widget.Toast.makeText(context, if (ok) exported else failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    FeedDialogs(vm, feeds, dialog) { dialog = null }

    val select = { f: FeedFilter -> vm.setFeedFilter(f) }
    Column(Modifier.fillMaxSize()) {
        PaneBar(stringResource(R.string.panel_folders), onBack = { vm.showPane(PanelPane.STREAM) }) {
            PanelIconButton(LucideIcons.ADD, stringResource(R.string.panel_feeds_add), tint = t.ink) { dialog = FeedDialog.AddFeed(filter.folderId) }
            Box {
                PanelIconButton(LucideIcons.SETTINGS, stringResource(R.string.panel_feeds_manage)) { menu = true }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_new_folder)) }, { menu = false; dialog = FeedDialog.NewFolder(null) }, leadingIcon = { Ic(LucideIcons.FOLDER) })
                    DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_opml_import)) }, { menu = false; importer.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "*/*")) }, leadingIcon = { Ic(LucideIcons.ARROW_DOWN) })
                    DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_opml_export)) }, { menu = false; exporter.launch("lifemapp-feeds.opml") }, leadingIcon = { Ic(LucideIcons.ARROW_UP) })
                    DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_refresh)) }, { menu = false; vm.refreshFeeds() }, leadingIcon = { Ic(LucideIcons.RELOAD) })
                }
            }
        }
        Divider()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            val unreadTotal = feeds.feeds.sumOf { it.unreadCount }
            item {
                SmartRow(LucideIcons.INBOX, stringResource(R.string.panel_feeds_all), null, filter.smartView == SmartView.ALL && filter.isScopeless()) { select(FeedFilter(SmartView.ALL)) }
                SmartRow(LucideIcons.CIRCLE_ALERT, stringResource(R.string.panel_feeds_unread), unreadTotal, filter.smartView == SmartView.UNREAD && filter.isScopeless()) { select(FeedFilter(SmartView.UNREAD)) }
                SmartRow(LucideIcons.STAR, stringResource(R.string.panel_feeds_starred), null, filter.smartView == SmartView.STARRED && filter.isScopeless()) { select(FeedFilter(SmartView.STARRED)) }
                Divider()
            }
            fun folderRows(parentId: String?, depth: Int) {
                feeds.folders.filter { it.parentId == parentId }.sortedBy { it.position }.forEach { folder ->
                    item(key = "f-${folder.id}") {
                        val ids = remember(feeds.folders) { FeedRepository.folderSubtree(feeds.folders, folder.id) }
                        val unread = feeds.feeds.filter { it.folderId in ids }.sumOf { it.unreadCount }
                        val open = folder.id in expanded
                        FolderRow(
                            folder, depth, unread, selected = filter.folderId == folder.id && filter.feedId == null, open = open,
                            onToggle = { expanded = if (open) expanded - folder.id else expanded + folder.id },
                            onSelect = { select(filter.copy(folderId = folder.id, feedId = null, tag = null)) },
                            onAddFeed = { dialog = FeedDialog.AddFeed(folder.id) },
                            onAddFolder = { dialog = FeedDialog.NewFolder(folder.id) },
                            onRefresh = { select(filter.copy(folderId = folder.id, feedId = null, tag = null)); vm.refreshFeeds() },
                            onDelete = { dialog = FeedDialog.DeleteFolder(folder) }.takeIf { folder.domainId == null },
                        )
                    }
                    if (folder.id in expanded) {
                        folderRows(folder.id, depth + 1)
                        feedRows(feeds, folder.id, depth + 1, filter, select, { dialog = it }, vm)
                    }
                }
            }
            folderRows(null, 0)
            feedRows(feeds, null, 0, filter, select, { dialog = it }, vm)
            val tags = feeds.feeds.flatMap { it.tags }.distinct().sorted()
            if (tags.isNotEmpty()) item {
                Column(Modifier.padding(12.dp)) {
                    Text(t.s(stringResource(R.string.panel_tags)), style = t.caption(10.sp))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag ->
                            val selected = filter.tag == tag
                            PanelChip("#$tag", selected) { select(filter.copy(tag = if (selected) null else tag, folderId = null, feedId = null)) }
                        }
                    }
                }
            }
        }
    }
}

private fun FeedFilter.isScopeless() = folderId == null && feedId == null && tag == null

private fun androidx.compose.foundation.lazy.LazyListScope.feedRows(
    feeds: FeedState, folderId: String?, depth: Int, filter: FeedFilter,
    select: (FeedFilter) -> Unit, onDialog: (FeedDialog) -> Unit, vm: SidePanelViewModel,
) {
    feeds.feeds.filter { it.folderId == folderId }.sortedBy { it.position }.forEach { feed ->
        item(key = "feed-${feed.id}") {
            FeedRow(
                feed, depth, selected = filter.feedId == feed.id, syncing = feeds.syncingFeedId == feed.id,
                onSelect = { select(filter.copy(feedId = feed.id, folderId = null, tag = null)) },
                onEdit = { onDialog(FeedDialog.EditFeed(feed)) },
                onRefresh = { vm.refreshFeeds(feed.id) },
                onDelete = { onDialog(FeedDialog.DeleteFeed(feed)) },
            )
        }
    }
}

@Composable
private fun SmartRow(icon: Int, label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().background(if (selected) t.tint else androidx.compose.ui.graphics.Color.Transparent).rowClick(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, 14.dp, if (selected) t.onTint else t.dim)
        Spacer(Modifier.width(10.dp))
        Text(label, style = t.body(13.sp, if (selected) t.onTint else t.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        count?.takeIf { it > 0 }?.let { Text("$it", style = t.caption(10.sp)) }
    }
}

@Composable
private fun FolderRow(
    folder: FeedFolder, depth: Int, unread: Int, selected: Boolean, open: Boolean,
    onToggle: () -> Unit, onSelect: () -> Unit, onAddFeed: () -> Unit, onAddFolder: () -> Unit, onRefresh: () -> Unit, onDelete: (() -> Unit)?,
) {
    val t = LocalTok.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().background(if (selected) t.tint else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(onClick = onSelect, onLongClick = { menu = true })
                .padding(start = (6 + depth * 16).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Ic(if (open) LucideIcons.CHEVRON_DOWN else LucideIcons.CHEVRON_RIGHT, 14.dp, t.dim)
            }
            Ic(LucideIcons.FOLDER, 13.dp, if (selected) t.onTint else t.dim)
            Spacer(Modifier.width(6.dp))
            Text(folder.name, style = t.body(13.sp, if (selected) t.onTint else t.text, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (unread > 0) Text("$unread", style = t.caption(10.sp))
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_add)) }, { menu = false; onAddFeed() }, leadingIcon = { Ic(LucideIcons.ADD) })
            DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_new_subfolder)) }, { menu = false; onAddFolder() }, leadingIcon = { Ic(LucideIcons.FOLDER) })
            DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_refresh)) }, { menu = false; onRefresh() }, leadingIcon = { Ic(LucideIcons.RELOAD) })
            onDelete?.let { DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_delete_folder)) }, { menu = false; it() }, leadingIcon = { Ic(LucideIcons.TRASH, tint = t.destructive) }) }
        }
    }
}

@Composable
private fun FeedRow(feed: Feed, depth: Int, selected: Boolean, syncing: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onRefresh: () -> Unit, onDelete: () -> Unit) {
    val t = LocalTok.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().background(if (selected) t.tint else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(onClick = onSelect, onLongClick = { menu = true })
                .padding(start = (28 + depth * 16).dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(if (feed.platform == "youtube") LucideIcons.resolve("youtube")!! else LucideIcons.RSS, 12.dp, if (feed.lastFetchStatus == "error") t.destructive else t.dim)
            Spacer(Modifier.width(6.dp))
            Text(
                feed.title.ifBlank { feed.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                style = t.body(12.sp, if (selected) t.onTint else if (feed.isActive) t.text else t.dim, if (feed.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal),
            )
            if (syncing) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = t.accent)
            else if (feed.unreadCount > 0) Text("${feed.unreadCount}", style = t.caption(10.sp))
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_refresh)) }, { menu = false; onRefresh() }, leadingIcon = { Ic(LucideIcons.RELOAD) })
            DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_edit)) }, { menu = false; onEdit() }, leadingIcon = { Ic(LucideIcons.EDIT) })
            DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_delete)) }, { menu = false; onDelete() }, leadingIcon = { Ic(LucideIcons.TRASH, tint = t.destructive) })
        }
    }
}

// ── Dialogs (AddFeedDialog, EditFeedDialog, folders) ───────────────────────

@Composable
private fun FeedDialogs(vm: SidePanelViewModel, feeds: FeedState, dialog: FeedDialog?, onDismiss: () -> Unit) {
    when (dialog) {
        null -> Unit
        is FeedDialog.AddFeed -> {
            var url by rememberSaveable { mutableStateOf("") }
            var title by rememberSaveable { mutableStateOf("") }
            var folderId by rememberSaveable { mutableStateOf(dialog.folderId) }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text(stringResource(R.string.panel_feeds_add)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(url, { url = it; error = null }, label = { Text(stringResource(R.string.panel_feeds_url_hint)) }, singleLine = true, isError = error != null,
                            supportingText = { error?.let { Text(it) } })
                        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.panel_feeds_title_hint)) }, singleLine = true)
                        FolderPicker(feeds.folders, folderId) { folderId = it }
                    }
                },
                confirmButton = {
                    TextButton(enabled = url.isNotBlank() && !busy, onClick = {
                        busy = true
                        vm.subscribe(url, title, folderId) { problem -> busy = false; if (problem == null) onDismiss() else error = problem }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.panel_feeds_add_button)) }
                },
                dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(stringResource(R.string.panel_cancel)) } },
            )
        }
        is FeedDialog.EditFeed -> {
            val feed = dialog.feed
            var title by rememberSaveable { mutableStateOf(feed.title) }
            var folderId by rememberSaveable { mutableStateOf(feed.folderId) }
            var interval by rememberSaveable { mutableIntStateOf(feed.fetchIntervalMinutes) }
            var active by rememberSaveable { mutableStateOf(feed.isActive) }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text(stringResource(R.string.panel_feeds_edit)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.panel_feeds_title)) }, singleLine = true, isError = error != null,
                            supportingText = { error?.let { Text(it) } })
                        Text(feed.url, style = LocalTok.current.caption(10.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FolderPicker(feeds.folders, folderId) { folderId = it }
                        Text(stringResource(R.string.panel_feeds_interval), style = LocalTok.current.body(12.sp, LocalTok.current.dim))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            INTERVALS.forEach { m -> PanelChip(stringResource(R.string.panel_feeds_minutes, m), interval == m) { interval = m } }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.panel_feeds_active), modifier = Modifier.weight(1f))
                            Switch(active, { active = it })
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = title.isNotBlank() && !busy, onClick = {
                        busy = true
                        vm.updateFeed(feed, title, folderId, interval, active) { problem -> busy = false; if (problem == null) onDismiss() else error = problem }
                    }) { Text(stringResource(R.string.panel_save)) }
                },
                dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(stringResource(R.string.panel_cancel)) } },
            )
        }
        is FeedDialog.DeleteFeed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.panel_feeds_delete)) },
            text = { Text(stringResource(R.string.panel_feeds_delete_text, dialog.feed.title.ifBlank { dialog.feed.url })) },
            confirmButton = { TextButton({ vm.deleteFeed(dialog.feed.id); onDismiss() }) { Text(stringResource(R.string.panel_delete), color = LocalTok.current.destructive) } },
            dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.panel_cancel)) } },
        )
        is FeedDialog.NewFolder -> {
            var name by rememberSaveable { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text(stringResource(if (dialog.parentId == null) R.string.panel_feeds_new_folder else R.string.panel_feeds_new_subfolder)) },
                text = {
                    OutlinedTextField(name, { name = it; error = null }, label = { Text(stringResource(R.string.panel_feeds_folder_name)) }, singleLine = true,
                        isError = error != null, supportingText = { error?.let { Text(it) } })
                },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank() && !busy, onClick = {
                        busy = true
                        vm.createFolder(name, dialog.parentId) { problem -> busy = false; if (problem == null) onDismiss() else error = problem }
                    }) { Text(stringResource(R.string.panel_feeds_create)) }
                },
                dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(stringResource(R.string.panel_cancel)) } },
            )
        }
        is FeedDialog.DeleteFolder -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.panel_feeds_delete_folder)) },
            text = { Text(stringResource(R.string.panel_feeds_delete_folder_text, dialog.folder.name)) },
            confirmButton = { TextButton({ vm.deleteFolder(dialog.folder.id); onDismiss() }) { Text(stringResource(R.string.panel_delete), color = LocalTok.current.destructive) } },
            dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.panel_cancel)) } },
        )
    }
}

/** The fetch intervals offered (the route accepts 5–1440 minutes) */
private val INTERVALS = listOf(15, 30, 60, 180, 360, 720, 1440)

@Composable
private fun FolderPicker(folders: List<FeedFolder>, selected: String?, onSelect: (String?) -> Unit) {
    val t = LocalTok.current
    var open by remember { mutableStateOf(false) }
    val name = folders.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.panel_feeds_no_folder)
    Box {
        Row(
            Modifier.fillMaxWidth().framed(t, t.well.copy(alpha = 0.3f), edge = t.line, cut = 4.dp).clickable { open = true }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(LucideIcons.FOLDER, 14.dp, t.dim)
            Spacer(Modifier.width(8.dp))
            Text("${stringResource(R.string.panel_feeds_folder)}: $name", style = t.body(13.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Ic(LucideIcons.CHEVRON_DOWN, 14.dp, t.dim)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_no_folder)) }, { open = false; onSelect(null) })
            fun depthOf(f: FeedFolder): Int {
                var d = 0
                var p = f.parentId
                while (p != null && d < 8) { d++; p = folders.firstOrNull { it.id == p }?.parentId }
                return d
            }
            ordered(folders, null).forEach { f ->
                DropdownMenuItem({ Text("${"   ".repeat(depthOf(f))}${f.name}") }, { open = false; onSelect(f.id) })
            }
        }
    }
}

/** Folders depth-first in position order */
private fun ordered(folders: List<FeedFolder>, parentId: String?): List<FeedFolder> =
    folders.filter { it.parentId == parentId }.sortedBy { it.position }.flatMap { listOf(it) + ordered(folders, it.id) }

// ── Stream ──────────────────────────────────────────────────────────────────

/**
 * useScopeLabel: the selected feed, or the selected folder's place in the tree ("… › Parent › Folder", the two
 * innermost folders), prefixed by the tag; null for a plain smart view.
 */
private fun scopeLabel(feeds: FeedState, filter: FeedFilter): String? {
    val place = when {
        filter.feedId != null -> feeds.feeds.firstOrNull { it.id == filter.feedId }?.let { it.title.ifBlank { it.url } }
        filter.folderId != null -> {
            val byId = feeds.folders.associateBy { it.id }
            val path = ArrayDeque<String>()
            var folder = byId[filter.folderId]
            while (folder != null && path.size < 4) {
                path.addFirst(folder.name)
                folder = folder.parentId?.let(byId::get)
            }
            path.takeIf { it.isNotEmpty() }?.let { (if (it.size > 2) "… › " else "") + it.takeLast(2).joinToString(" › ") }
        }
        else -> null
    }
    return when {
        filter.tag != null -> place?.let { "#${filter.tag} · $it" } ?: "#${filter.tag}"
        else -> place
    }
}

@Composable
private fun FeedStream(vm: SidePanelViewModel, feeds: FeedState) {
    val t = LocalTok.current
    val filter = feeds.filter
    var menu by remember { mutableStateOf(false) }
    val title = scopeLabel(feeds, filter) ?: stringResource(
        when (filter.smartView) {
            SmartView.ALL -> R.string.panel_feeds_all
            SmartView.UNREAD -> R.string.panel_feeds_unread
            SmartView.STARRED -> R.string.panel_feeds_starred
        },
    )
    var query by remember(filter) { mutableStateOf(filter.search) }
    LaunchedEffect(query) {
        if (query == filter.search) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        vm.setFeedFilter(filter.copy(search = query))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ScopeButton(t.s(title), stringResource(R.string.panel_feeds_scope, title), Modifier.weight(1f)) { vm.showPane(PanelPane.SIDEBAR) }
            Spacer(Modifier.width(4.dp))
            if (feeds.syncingFeedId != null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = t.accent)
            else PanelIconButton(LucideIcons.RELOAD, stringResource(R.string.panel_feeds_refresh)) { vm.refreshFeeds() }
            Box {
                PanelIconButton(LucideIcons.MARK_ALL_READ, stringResource(R.string.panel_feeds_mark_all_read)) { menu = true }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_mark_all_read)) }, { menu = false; vm.markAllFeedItemsRead(null) })
                    listOf(1, 3, 7).forEach { days ->
                        DropdownMenuItem({ Text(stringResource(R.string.panel_feeds_mark_older, days)) }, { menu = false; vm.markAllFeedItemsRead(days) })
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            SearchField(query, { query = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelChip(stringResource(R.string.panel_feeds_unread), filter.smartView == SmartView.UNREAD) { vm.setFeedFilter(filter.copy(smartView = SmartView.UNREAD)) }
                PanelChip(stringResource(R.string.panel_feeds_all), filter.smartView == SmartView.ALL) { vm.setFeedFilter(filter.copy(smartView = SmartView.ALL)) }
                PanelChip(stringResource(R.string.panel_feeds_starred), filter.smartView == SmartView.STARRED, icon = LucideIcons.STAR) { vm.setFeedFilter(filter.copy(smartView = SmartView.STARRED)) }
                PanelChip(stringResource(if (filter.oldestFirst) R.string.panel_feeds_oldest_first else R.string.panel_feeds_newest_first), false) {
                    vm.setFeedFilter(filter.copy(oldestFirst = !filter.oldestFirst))
                }
            }
        }
        Divider()
        when {
            !feeds.loaded && (feeds.loadingItems || feeds.loadingFeeds) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = t.accent)
            }
            feeds.failed && feeds.items.isEmpty() -> EmptyState(stringResource(R.string.panel_feeds_offline), stringResource(R.string.panel_feeds_retry)) { vm.reloadFeeds() }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (feeds.items.isEmpty() && !feeds.loadingItems) item { EmptyState(stringResource(R.string.panel_feeds_empty)) }
                items(feeds.items, key = { it.id }) { item -> FeedItemRow(vm, item) }
                if (feeds.hasMore) item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        if (feeds.loadingMore) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.accent)
                        else Text(
                            stringResource(R.string.panel_feeds_load_more), style = t.body(13.sp, t.accent, FontWeight.SemiBold),
                            modifier = Modifier.clip(CircleShape).clickable { vm.loadMoreFeedItems() }.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedItemRow(vm: SidePanelViewModel, item: FeedItem) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().clickable { vm.openFeedItem(item.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(if (item.isRead) androidx.compose.ui.graphics.Color.Transparent else t.accent))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = t.body(13.sp, if (item.isRead) t.dim else t.text, if (item.isRead) FontWeight.Normal else FontWeight.SemiBold), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(item.feed?.title, relativeTime(item.publishedAt)).joinToString(" · "),
                style = t.caption(10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        item.thumbnailUrl?.let { url ->
            Spacer(Modifier.width(8.dp))
            Thumbnail(vm, url, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)))
        }
        if (item.isStarred) { Spacer(Modifier.width(4.dp)); Ic(LucideIcons.STAR, 12.dp, t.accent) }
    }
    Divider()
}

@Composable
private fun Thumbnail(vm: SidePanelViewModel, url: String, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, url) { value = vm.feedImage(url) }
    image?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) }
}

@Composable
private fun relativeTime(iso: String): String? {
    val instant = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: runCatching { Instant.parse(iso) }.getOrNull() ?: return null
    val age = Duration.between(instant, Instant.now())
    return when {
        age.isNegative || age.toMinutes() < 1 -> "now"
        age.toHours() < 1 -> "${age.toMinutes()}m"
        age.toDays() < 1 -> "${age.toHours()}h"
        age.toDays() < 7 -> "${age.toDays()}d"
        else -> instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

// ── Reader ──────────────────────────────────────────────────────────────────

/** FeedReader: title, source and date, picture, the text (light HTML rendering), and read / star / open original. */
@Composable
private fun FeedReader(vm: SidePanelViewModel, item: FeedItem, feeds: FeedState, openLink: (String) -> Unit) {
    val t = LocalTok.current
    val html = item.contentHtml?.takeIf { it.isNotBlank() } ?: item.summary.orEmpty()
    val body: AnnotatedString = remember(html, t.accent) {
        runCatching {
            AnnotatedString.fromHtml(
                stripMedia(html),
                linkStyles = TextLinkStyles(style = SpanStyle(color = t.accent)),
                linkInteractionListener = { link -> (link as? LinkAnnotation.Url)?.url?.let(openLink) },
            )
        }.getOrElse { AnnotatedString(html) }
    }
    Column(Modifier.fillMaxSize()) {
        PaneBar(item.feed?.title ?: feeds.feeds.firstOrNull { it.id == item.feedId }?.title.orEmpty(), onBack = { vm.back() }) {
            PanelIconButton(
                if (item.isRead) LucideIcons.CIRCLE_ALERT else LucideIcons.CHECK,
                stringResource(if (item.isRead) R.string.panel_feeds_mark_unread else R.string.panel_feeds_mark_read),
            ) { vm.setFeedItemRead(item.id, !item.isRead) }
            PanelIconButton(
                LucideIcons.STAR, stringResource(if (item.isStarred) R.string.panel_feeds_unstar else R.string.panel_feeds_star),
                tint = if (item.isStarred) t.accent else t.dim,
            ) { vm.setFeedItemStarred(item.id, !item.isStarred) }
            PanelIconButton(LucideIcons.EXTERNAL_LINK, stringResource(R.string.panel_feeds_open_original), enabled = item.url.isNotBlank()) { openLink(item.url) }
        }
        Divider()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.title, style = t.body(18.sp, t.text, FontWeight.Bold))
            Text(listOfNotNull(item.author, relativeTime(item.publishedAt)).joinToString(" · "), style = t.caption(10.sp))
            item.thumbnailUrl?.let { Thumbnail(vm, it, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) }
            Text(body, style = t.body(14.sp, t.text).copy(lineHeight = 21.sp))
            if (item.url.isNotBlank()) Text(
                stringResource(R.string.panel_feeds_open_original), style = t.body(13.sp, t.accent, FontWeight.SemiBold),
                modifier = Modifier.clip(CircleShape).clickable { openLink(item.url) }.padding(vertical = 8.dp),
            )
        }
    }
}

/** The light renderer can't draw pictures, video or scripts: drop them rather than show their raw markup */
private fun stripMedia(html: String): String = html
    .replace(Regex("<(script|style|iframe|video|audio|object)\\b[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
    .replace(Regex("<(img|source|embed)\\b[^>]*>", RegexOption.IGNORE_CASE), "")
