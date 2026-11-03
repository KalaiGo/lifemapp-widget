package com.kalai73.lifemapp.widgets.ui

import android.util.TypedValue
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.content.ItemHtml
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.designsystem.CondensedFontFamily
import com.kalai73.lifemapp.widgets.core.designsystem.LocalPdPalette
import com.kalai73.lifemapp.widgets.core.designsystem.LocalUiStyle
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** What the item's long-press menu offers. */
internal enum class ItemAction { EDIT, MOVE_UP, MOVE_DOWN, TOGGLE_WARNING, COPY_LINK, DELETE }

/** What the Today screen does with an item (subsubdomain); bundled so the domain cards take one parameter. */
internal class ItemHandlers(
    val loadText: suspend (itemId: String) -> ItemText,
    /** New name and / or HTML text, null keeping either */
    val save: (itemId: String, name: String?, html: String?) -> Unit,
    val delete: (itemId: String) -> Unit,
    val move: (itemId: String, topicId: String, up: Boolean) -> Unit,
    val setWarning: (itemId: String, warning: Boolean) -> Unit,
    /** An image item's picture */
    val loadImage: suspend (itemId: String) -> ItemImage,
    /** Collapses (true) or expands an item: an image item's header tap */
    val setCollapsed: (itemId: String, collapsed: Boolean) -> Unit,
)

/**
 * The item's long-press menu in the layout of the web card's menu (subsubdomain-item.tsx): Edit | Move up,
 * Move down | Add / Remove Warning Icon (blue, yellow in dark mode) | Copy link | Delete (red), each with its
 * Lucide icon, groups split by separators, in the web dropdown's compact rows. Transfer, Create task and
 * Properties are web-only for now.
 */
@Composable
internal fun ItemMenu(
    item: TopicItemUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (ItemAction) -> Unit,
) {
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val pd = LocalPdPalette.current
    val colors = MaterialTheme.colorScheme
    val text = if (predator) pd.text else colors.onSurface
    val line = if (predator) pd.edge else colors.outlineVariant
    val dark = if (predator) pd.isDark else colors.surface.luminance() < 0.5f
    // text-blue-600 / dark:text-yellow-400
    val warningColor = if (dark) Color(0xFFFACC15) else Color(0xFF2563EB)
    val deleteColor = if (predator) pd.destructive else colors.error
    val font = if (predator) CondensedFontFamily else FontFamily.Default

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        // rounded-md border bg-popover p-1 shadow-md
        shape = RoundedCornerShape(6.dp),
        containerColor = if (predator) pd.raised else colors.surface,
        border = BorderStroke(1.dp, line),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.widthIn(min = 160.dp).padding(horizontal = 4.dp),
    ) {
        MenuRow(LucideIcons.EDIT, stringResource(R.string.item_edit), text, font) { onAction(ItemAction.EDIT) }
        MenuSeparator(line)
        MenuRow(LucideIcons.ARROW_UP, stringResource(R.string.item_move_up), text, font, enabled = canMoveUp) { onAction(ItemAction.MOVE_UP) }
        MenuRow(LucideIcons.ARROW_DOWN, stringResource(R.string.item_move_down), text, font, enabled = canMoveDown) { onAction(ItemAction.MOVE_DOWN) }
        MenuSeparator(line)
        MenuRow(
            LucideIcons.WARNING,
            stringResource(if (item.warning) R.string.item_remove_warning else R.string.item_add_warning),
            warningColor, font,
        ) { onAction(ItemAction.TOGGLE_WARNING) }
        MenuSeparator(line)
        MenuRow(LucideIcons.LINK_2, stringResource(R.string.item_copy_link), text, font) { onAction(ItemAction.COPY_LINK) }
        MenuSeparator(line)
        MenuRow(LucideIcons.TRASH, stringResource(R.string.item_delete), deleteColor, font) { onAction(ItemAction.DELETE) }
    }
}

/** `px-2 py-1.5 text-sm rounded-sm` with a 16 dp icon and `mr-2` */
@Composable
private fun MenuRow(icon: Int, label: String, color: Color, font: FontFamily, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) color else color.copy(alpha = 0.45f)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(fontFamily = font, fontSize = 14.sp, color = tint))
    }
}

/** `-mx-1 my-1 h-px bg-muted` */
@Composable
private fun MenuSeparator(color: Color) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(color.copy(alpha = 0.5f)))
}

@Composable
internal fun RemoveItemDialog(item: TopicItemUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_remove_title)) },
        text = { Text(item.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.today_locked)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.item_remove), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.item_cancel)) } },
    )
}

/** The web toolbar's text colors (editor-toolbar.tsx COLOR_OPTIONS); null is Default. */
private val TEXT_COLORS: List<Pair<String, Long?>> = listOf(
    "Default" to null,
    "Black" to 0xFF000000, "Dark Gray" to 0xFF374151, "Gray" to 0xFF6B7280, "Light Gray" to 0xFF9CA3AF,
    "Red" to 0xFFEF4444, "Orange" to 0xFFF97316, "Yellow" to 0xFFEAB308, "Green" to 0xFF22C55E,
    "Blue" to 0xFF3B82F6, "Indigo" to 0xFF6366F1, "Purple" to 0xFFA855F7, "Pink" to 0xFFEC4899,
)

/**
 * The web editor's toolbar, in its expanded form for what an item's HTML can hold: undo / redo, then bold,
 * italic, underline, strikethrough, then text color, bullet list, link and clear formatting. 28 dp ghost buttons
 * with 14 dp Lucide icons in muted rounded groups, the active ones highlighted. [tick] only makes it redraw.
 */
@Composable
internal fun EditorToolbar(editor: RichEditText, @Suppress("UNUSED_PARAMETER") tick: Int) {
    var linking by remember { mutableStateOf<IntRange?>(null) }
    var palette by remember { mutableStateOf(false) }
    linking?.let { range ->
        LinkDialog(
            onDismiss = { linking = null },
            onConfirm = { url -> editor.setLink(url, range.first, range.last); linking = null },
        )
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolGroup {
            ToolButton(LucideIcons.UNDO, stringResource(R.string.format_undo), enabled = editor.canUndo) { editor.undo() }
            ToolButton(LucideIcons.REDO, stringResource(R.string.format_redo), enabled = editor.canRedo) { editor.redo() }
        }
        ToolSeparator()
        ToolGroup {
            ToolButton(LucideIcons.BOLD, stringResource(R.string.format_bold), active = editor.isActive(Mark.BOLD)) { editor.toggle(Mark.BOLD) }
            ToolButton(LucideIcons.ITALIC, stringResource(R.string.format_italic), active = editor.isActive(Mark.ITALIC)) { editor.toggle(Mark.ITALIC) }
            ToolButton(LucideIcons.UNDERLINE, stringResource(R.string.format_underline), active = editor.isActive(Mark.UNDERLINE)) { editor.toggle(Mark.UNDERLINE) }
            ToolButton(LucideIcons.STRIKETHROUGH, stringResource(R.string.format_strikethrough), active = editor.isActive(Mark.STRIKE)) { editor.toggle(Mark.STRIKE) }
        }
        ToolSeparator()
        Box {
            ToolGroup {
                ToolButton(LucideIcons.PALETTE, stringResource(R.string.format_text_color), enabled = editor.hasSelection) { palette = true }
            }
            ColorMenu(expanded = palette, onDismiss = { palette = false }) { color -> palette = false; editor.setColor(color) }
        }
        ToolGroup {
            ToolButton(LucideIcons.BULLET_LIST, stringResource(R.string.format_bullet_list), active = editor.isBulletList()) { editor.toggleBulletList() }
        }
        ToolGroup {
            ToolButton(LucideIcons.LINK, stringResource(R.string.format_link)) {
                linking = minOf(editor.selectionStart, editor.selectionEnd)..maxOf(editor.selectionStart, editor.selectionEnd)
            }
        }
        ToolGroup {
            ToolButton(LucideIcons.CLEAR_FORMATTING, stringResource(R.string.format_clear)) { editor.clearFormatting() }
        }
    }
}

/** `flex items-center gap-0.5 p-1 bg-muted/30 rounded-md` */
@Composable
private fun ToolGroup(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** `w-px h-5 bg-border mx-0.5` */
@Composable
private fun ToolSeparator() {
    Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** A `ghost` / `secondary` (active) `h-7 w-7` button with a 14 dp icon */
@Composable
private fun ToolButton(icon: Int, description: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        active -> colors.onSecondaryContainer
        else -> colors.onSurface
    }
    Box(
        Modifier.size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) colors.secondaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(14.dp))
    }
}

/** The web's "Text Color" menu: a 4-column grid of 24 dp swatches */
@Composable
private fun ColorMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.format_text_color),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TEXT_COLORS.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (name, value) ->
                        Box(
                            Modifier.size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(value?.let { Color(it) } ?: colors.surface)
                                .border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp))
                                .clickable(onClickLabel = name, role = Role.Button) { onPick(value?.toInt()) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (value == null) Icon(painterResource(LucideIcons.CLOSE), name, tint = colors.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The web's link panel: an address for the selection (or, with just a cursor, inserted as the link text) */
@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val address = url.trim().let { if (it.isEmpty() || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(it)) it else "https://$it" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.format_link)) },
        text = {
            OutlinedTextField(
                url, { url = it },
                label = { Text(stringResource(R.string.format_link_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(address) }, enabled = address.isNotEmpty()) { Text(stringResource(R.string.item_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.item_cancel)) } },
    )
}
