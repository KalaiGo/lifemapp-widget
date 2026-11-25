package com.kalai73.lifemapp.widgets.ui

import android.util.TypedValue
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.ResourcesCompat
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.R as DesignR
import com.kalai73.lifemapp.widgets.core.designsystem.MonoFontFamily
import com.kalai73.lifemapp.widgets.core.designsystem.pdLabelStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.presentation.panel.ItemTags
import com.kalai73.lifemapp.widgets.ui.sidepanel.ReminderSheet
import com.kalai73.lifemapp.widgets.ui.sidepanel.ReminderValue
import com.kalai73.lifemapp.widgets.ui.sidepanel.reminderDateLabel
import com.kalai73.lifemapp.widgets.ui.sidepanel.statusColor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.kalai73.lifemapp.widgets.core.data.content.ItemHtml
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.designsystem.LocalPdPalette
import com.kalai73.lifemapp.widgets.core.designsystem.LocalUiStyle
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdButton
import com.kalai73.lifemapp.widgets.core.designsystem.pdCaptionStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdChamferShape
import com.kalai73.lifemapp.widgets.core.designsystem.pdPanel
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the editor's text area shows while and after the item's text loads. */
private sealed interface TextState {
    data object Loading : TextState
    data class Ready(val text: ItemText.Loaded) : TextState
    /** No editable text: [message] says why (image, locked, removed, failed to load) */
    data class Unavailable(val message: Int) : TextState
}

/** Editor height where it doesn't fill: the web's COMPACT_EDITOR_HEIGHT (140) plus 30% for a phone */
internal val COMPACT_EDITOR_HEIGHT = 182.dp
private val CREATED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * The web's item editor on a small screen (subsubdomain-editor-modal.tsx): a full-height panel with the "• ITEM"
 * badge, previous / next through [nav] and close; the title with the tag pill and the reminder; the rich text editor
 * filling the height left; and one footer row: Remove, created date, character count and Done. Predator draws the
 * chamfered panel and parallelograms; Classic the rounded card and pills.
 *
 * Done saves what changed (the name and / or the text) and closes; closing with changes asks to discard them.
 * Previous / next save first and stay when that fails; tags and the reminder save at once. The text is only sent when
 * it was edited, so formatting the app can't hold stays as stored unless it was.
 */
@Composable
internal fun ItemEditorDialog(
    /** The item to open on */
    item: TopicItemUi,
    /** Rename puts the cursor in the title, Edit content in the text */
    focusTitle: Boolean,
    loadText: suspend (String) -> ItemText,
    onSave: (itemId: String, name: String?, html: String?) -> Unit,
    onDelete: (itemId: String) -> Unit,
    onDismiss: () -> Unit,
    /** The items previous / next step through, in order */
    nav: List<TopicItemUi> = emptyList(),
    /** False to show the item without putting the cursor anywhere (opened from the widget, to read) */
    autoFocus: Boolean = true,
) {
    var activeId by remember(item.id) { mutableStateOf(item.id) }
    // The last known copy stays when a sync removes the item, so the editor doesn't jump to another one
    var shown by remember(item.id) { mutableStateOf(item) }
    val active = nav.firstOrNull { it.id == activeId } ?: shown
    LaunchedEffect(active) { shown = active }
    // Some callers are outside the screens' tokens
    CompositionLocalProvider(LocalTok provides rememberTokens()) {
        ItemEditorContent(
            active, focusTitle, loadText, onSave, onDelete, onDismiss, nav,
            autoFocus = autoFocus && activeId == item.id,
            onNavigate = { shown = it; activeId = it.id },
        )
    }
}

@Composable
private fun ItemEditorContent(
    item: TopicItemUi,
    focusTitle: Boolean,
    loadText: suspend (String) -> ItemText,
    onSave: (itemId: String, name: String?, html: String?) -> Unit,
    onDelete: (itemId: String) -> Unit,
    onDismiss: () -> Unit,
    nav: List<TopicItemUi>,
    autoFocus: Boolean,
    onNavigate: (TopicItemUi) -> Unit,
) {
    val t = LocalTok.current
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val pd = LocalPdPalette.current
    val colors = MaterialTheme.colorScheme
    // Predator inks from the palette, Classic from the Material scheme
    val text = if (predator) pd.text else colors.onSurface
    val dim = if (predator) pd.dim else colors.onSurfaceVariant
    val accentInk = if (predator) pd.ink else colors.primary
    val line = if (predator) pd.edge else colors.outlineVariant

    // The name edits are compared with, kept while a save of this item updates it underneath
    val baseName = remember(item.id) { item.name }
    val nameKnown = baseName != null
    var name by remember(item.id) { mutableStateOf(TextFieldValue(baseName.orEmpty(), TextRange(baseName.orEmpty().length))) }
    var textState by remember(item.id) { mutableStateOf<TextState>(TextState.Loading) }
    var editor by remember(item.id) { mutableStateOf<RichEditText?>(null) }
    var tick by remember(item.id) { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var reminderOpen by remember(item.id) { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val editing = LocalItemEditing.current
    val meta by remember(item.id, editing) { editing?.meta(item.id) ?: flowOf(null) }.collectAsState(null)
    LaunchedEffect(editing) { editing?.loadCustomTags() }

    LaunchedEffect(item.id) {
        textState = try {
            when (val loaded = loadText(item.id)) {
                is ItemText.Loaded -> TextState.Ready(loaded)
                ItemText.Image -> TextState.Unavailable(R.string.item_text_image)
                ItemText.Locked -> TextState.Unavailable(R.string.error_item_locked)
                ItemText.Unreadable -> TextState.Unavailable(R.string.item_text_unreadable)
                ItemText.Gone -> TextState.Unavailable(R.string.item_gone)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            TextState.Unavailable(R.string.error_network)
        } catch (_: Exception) {
            TextState.Unavailable(R.string.item_text_failed)
        }
    }
    LaunchedEffect(item.id) { if (autoFocus && focusTitle && nameKnown) runCatching { titleFocus.requestFocus() } }

    val newName = name.text.trim()
    val nameChanged = nameKnown && newName != baseName
    val textChanged = tick >= 0 && editor?.changed == true
    val dirty = nameChanged || textChanged
    val ready = textState as? TextState.Ready
    val length = if (tick >= 0) editor?.text?.length ?: 0 else 0
    val maxChars = ready?.text?.maxChars
    val overLimit = maxChars != null && length > maxChars
    val canSave = (!nameKnown || newName.isNotEmpty()) && !overLimit

    val editedName = { if (nameChanged) newName else null }
    val editedHtml = { if (textChanged) editor?.text?.let(ItemHtml::toHtml) else null }
    val close = { if (!saving) { if (dirty) confirmDiscard = true else onDismiss() } }
    val done = {
        if (!dirty) onDismiss()
        else if (canSave) onSave(item.id, editedName(), editedHtml())
    }
    val index = nav.indexOfFirst { it.id == item.id }
    val previous = if (index > 0) nav[index - 1] else null
    val next = if (index >= 0) nav.getOrNull(index + 1) else null
    val canNavigate = !saving && (!dirty || (canSave && editing != null))
    val navigate = { target: TopicItemUi ->
        if (!dirty) onNavigate(target)
        else if (editing != null) {
            saving = true
            val n = editedName()
            val h = editedHtml()
            scope.launch {
                try {
                    if (editing.saveNow(item.id, n, h)) onNavigate(target)
                } finally {
                    saving = false
                }
            }
        }
    }

    if (confirmRemove) RemoveItemDialog(item, onConfirm = { confirmRemove = false; onDelete(item.id) }, onDismiss = { confirmRemove = false })
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onDismiss() }, onKeep = { confirmDiscard = false })

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (reminderOpen && editing != null) {
            val r = meta?.reminder
            ReminderSheet(
                ReminderValue(r?.value, r?.rule, r?.leadDays ?: 0),
                onChange = { editing.saveReminder(item.id, it.date, it.rule, it.leadDays) },
                onDismiss = { reminderOpen = false },
            )
        }
        val panel = if (predator) {
            Modifier.pdPanel(pd, cut = 12.dp)
        } else {
            val shape = RoundedCornerShape(24.dp)
            Modifier.clip(shape).background(colors.surface, shape).border(1.dp, line, shape)
        }
        // A tap beside the panel closes it
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.imePadding().padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight()
                    .then(panel)
                    // Taps on the panel itself must not reach the backdrop
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Header: badge, previous / next centered in the space left, close
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemBadge(predator, accentInk)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (index >= 0 && nav.size > 1) {
                            RoundIconButton(LucideIcons.CHEVRON_LEFT, stringResource(R.string.item_previous), dim, size = 32.dp, enabled = canNavigate && previous != null) {
                                previous?.let(navigate)
                            }
                            Text(
                                "${index + 1} / ${nav.size}",
                                style = if (predator) pdCaptionStyle(11.sp, dim.copy(alpha = 0.7f), letterSpacing = 0.em) else TextStyle(fontSize = 11.sp, color = dim.copy(alpha = 0.6f)),
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            RoundIconButton(LucideIcons.CHEVRON_RIGHT, stringResource(R.string.item_next), dim, size = 32.dp, enabled = canNavigate && next != null) {
                                next?.let(navigate)
                            }
                        }
                    }
                    RoundIconButton(LucideIcons.CLOSE, stringResource(R.string.item_close), dim, size = 32.dp) { close() }
                }

                // Hero title with the tag pill and the reminder, over a faint rule
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val titleStyle = TextStyle(
                        fontFamily = if (predator) MonoFontFamily else FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.025).em,
                        color = if (nameKnown) text else dim,
                    )
                    Box(Modifier.weight(1f)) {
                        if (nameKnown) {
                            if (name.text.isEmpty()) Text(stringResource(R.string.item_title_hint), style = titleStyle.copy(color = dim.copy(alpha = 0.35f)), maxLines = 1)
                            BasicTextField(
                                name, { name = it },
                                textStyle = titleStyle,
                                cursorBrush = SolidColor(accentInk),
                                singleLine = true,
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                            )
                        } else {
                            Text(stringResource(R.string.today_locked), style = titleStyle)
                        }
                    }
                    val known = meta
                    if (known != null && editing != null) {
                        TagPill(known) { picked -> editing.saveTags(item.id, ItemTags.toStore(known.topicTags, picked)) }
                        val reminder = known.reminder
                        val ink = reminder?.let { statusColor(it.status, dim, t.dark) } ?: dim
                        Pill(ink, stringResource(R.string.panel_set_reminder), onClick = { reminderOpen = true }) {
                            Icon(painterResource(LucideIcons.CALENDAR), stringResource(R.string.panel_set_reminder), tint = ink, modifier = Modifier.size(14.dp))
                            if (reminder != null) {
                                if (reminder.rule != null) Icon(painterResource(LucideIcons.REPEAT), null, tint = ink, modifier = Modifier.size(12.dp))
                                Text(reminderDateLabel(reminder.date), style = pillStyle(predator, ink), maxLines = 1)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(line.copy(alpha = 0.3f)))

                // Content canvas: toolbar and editor in one bordered box, filling the height left
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (val state = textState) {
                        TextState.Loading -> CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center), color = accentInk, strokeWidth = 2.dp)
                        is TextState.Unavailable -> Text(stringResource(state.message), style = MaterialTheme.typography.bodyMedium, color = dim)
                        // A new editor per item: the view is made once, with that item's text
                        is TextState.Ready -> key(item.id) {
                            RichTextBox(
                                html = state.text.html,
                                hint = stringResource(R.string.item_text_hint),
                                height = null,
                                editor = editor,
                                tick = tick,
                                focus = autoFocus && (!focusTitle || !nameKnown),
                                onEditor = { editor = it },
                                onChange = { tick++ },
                                modifier = Modifier.fillMaxHeight(),
                            )
                        }
                    }
                }
                if (ready != null && ItemHtml.hasUnsupportedFormatting(ready.text.html)) {
                    Text(stringResource(R.string.item_text_simplified), style = MaterialTheme.typography.bodySmall, color = dim)
                }

                // Footer over a faint rule: Remove, created date, character count, Done
                Box(Modifier.fillMaxWidth().height(1.dp).background(line.copy(alpha = 0.4f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(CircleShape)
                            .clickable(enabled = !saving, role = Role.Button) { confirmRemove = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(LucideIcons.TRASH), null, tint = dim, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.item_remove), maxLines = 1,
                            style = TextStyle(fontFamily = if (predator) MonoFontFamily else FontFamily.Default, fontSize = 12.sp, color = dim),
                        )
                    }
                    val metaStyle = if (predator) pdCaptionStyle(10.sp, dim.copy(alpha = 0.6f), letterSpacing = 0.em)
                    else TextStyle(fontSize = 10.sp, color = dim.copy(alpha = 0.6f))
                    Text(
                        ready?.text?.createdAt?.let(::formatCreated).orEmpty(), style = metaStyle,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                    if (maxChars != null) {
                        Text(
                            "$length/$maxChars", maxLines = 1,
                            style = if (overLimit) metaStyle.copy(color = colors.error, fontWeight = FontWeight.SemiBold) else metaStyle,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    DoneButton(predator, enabled = !saving && (!dirty || canSave), onClick = done)
                }
            }
        }
    }
}

private fun pillStyle(predator: Boolean, color: Color): TextStyle =
    if (predator) pdLabelStyle(11.sp, color, FontWeight.Medium, letterSpacing = 0.em)
    else TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)

/** The small bordered buttons beside the title: Predator's parallelogram, Classic's pill */
@Composable
private fun Pill(ink: Color, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val shape = remember(predator) { if (predator) pdParallelogramShape(8.dp) else CircleShape }
    val line = if (predator) LocalPdPalette.current.edge else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.height(28.dp).clip(shape).background(ink.copy(alpha = 0.04f)).border(1.dp, line.copy(alpha = 0.5f), shape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) { content() }
}

/** TagPill: the first tag ("Work +1"), opening a menu that switches each tag; the topic's own stay on. */
@Composable
private fun TagPill(meta: ItemMeta, onChange: (List<String>) -> Unit) {
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val ink = if (predator) LocalPdPalette.current.ink else MaterialTheme.colorScheme.primary
    var open by remember { mutableStateOf(false) }
    val options = ItemTags.BUILTIN + meta.customTags.keys
    // Tags the user deleted are left out of the pill but kept when others change
    val shown = meta.tags.filter { it in options }.ifEmpty { listOf(ItemTags.DEFAULT_TAG) }
    Box {
        Pill(ink, stringResource(R.string.item_tags), onClick = { open = true }) {
            Icon(painterResource(tagIcon(shown.first())), null, tint = ink, modifier = Modifier.size(14.dp))
            Text(
                tagName(shown.first(), meta) + if (shown.size > 1) " +${shown.size - 1}" else "",
                style = pillStyle(predator, ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp),
            )
            Icon(painterResource(LucideIcons.CHEVRON_DOWN), null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEach { tag ->
                val on = tag in meta.tags
                DropdownMenuItem(
                    text = { Text(tagName(tag, meta)) },
                    enabled = tag !in meta.topicTags,
                    leadingIcon = { Icon(painterResource(tagIcon(tag)), null, Modifier.size(16.dp)) },
                    trailingIcon = if (on) ({ Icon(painterResource(LucideIcons.CHECK), null, Modifier.size(14.dp)) }) else null,
                    onClick = { onChange(if (on) meta.tags - tag else meta.tags + tag) },
                )
            }
        }
    }
}

private fun tagIcon(tag: String): Int = when (tag) {
    "me" -> LucideIcons.USER
    "work" -> LucideIcons.BRIEFCASE
    "family" -> LucideIcons.USERS
    else -> LucideIcons.FLAG
}

@Composable
private fun tagName(tag: String, meta: ItemMeta): String = when (tag) {
    "me" -> stringResource(R.string.tag_me)
    "work" -> stringResource(R.string.tag_work)
    "family" -> stringResource(R.string.tag_family)
    else -> meta.customTags[tag] ?: tag
}

/**
 * The web's content canvas: the formatting toolbar over the rich text editor, in one bordered box (Predator's
 * chamfered well, Classic's rounded card). [onEditor] hands over the editor once made; [onChange] fires on every
 * text or selection change, so the caller's [tick] redraws the toolbar.
 */
@Composable
internal fun RichTextBox(
    html: String,
    hint: String,
    /** Null fills the height [modifier] gives */
    height: androidx.compose.ui.unit.Dp?,
    editor: RichEditText?,
    tick: Int,
    focus: Boolean,
    onEditor: (RichEditText) -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val pd = LocalPdPalette.current
    val colors = MaterialTheme.colorScheme
    val text = if (predator) pd.text else colors.onSurface
    val dim = if (predator) pd.dim else colors.onSurfaceVariant
    val accentInk = if (predator) pd.ink else colors.primary
    val line = if (predator) pd.edge else colors.outlineVariant
    val boxShape = if (predator) pdChamferShape(6.dp) else RoundedCornerShape(16.dp)
    // focus-within: accent border
    val focused = tick >= 0 && editor?.isFocused == true
    Column(
        modifier.fillMaxWidth()
            .clip(boxShape)
            .background(if (predator) pd.well.copy(alpha = 0.35f) else colors.onSurface.copy(alpha = 0.03f), boxShape)
            .border(1.dp, if (focused) accentInk.copy(alpha = 0.5f) else line.copy(alpha = if (predator) 1f else 0.4f), boxShape),
    ) {
        editor?.let { EditorToolbar(it, tick) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(line.copy(alpha = 0.4f)))
        val textColor = text.toArgb()
        val hintColor = dim.copy(alpha = 0.6f).toArgb()
        val linkColor = accentInk.toArgb()
        AndroidView(
            factory = { context ->
                RichEditText(context).apply {
                    background = null
                    gravity = Gravity.TOP or Gravity.START
                    isVerticalScrollBarEnabled = true
                    val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics).toInt()
                    setPadding(pad, pad, pad, pad)
                    // text-[0.8rem], leading-relaxed
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setLineSpacing(0f, 1.35f)
                    if (predator) typeface = ResourcesCompat.getFont(context, DesignR.font.jetbrains_mono_regular)
                    setTextColor(textColor)
                    setHintTextColor(hintColor)
                    setLinkTextColor(linkColor)
                    this.hint = hint
                    load(ItemHtml.toSpanned(html))
                    onStateChange = onChange
                    onEditor(this)
                    // load() opens the text at its beginning (cursor at the start, scrolled to the top)
                    if (focus) requestFocus()
                }
            },
            modifier = Modifier.fillMaxWidth().then(if (height != null) Modifier.height(height) else Modifier.weight(1f)),
        )
    }
}

/** "• ITEM": the web's rounded primary badge with its dot (also "• EVENT", "• TASK" on the details modals) */
@Composable
internal fun ItemBadge(predator: Boolean, ink: Color, label: String = stringResource(R.string.item_badge).uppercase()) {
    val shape = remember(predator) { if (predator) pdParallelogramShape(8.dp) else CircleShape }
    Row(
        Modifier.clip(shape)
            .background(ink.copy(alpha = 0.1f))
            .border(1.dp, ink.copy(alpha = 0.25f), shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = if (predator) pdLabelStyle(10.sp, ink, letterSpacing = 0.05.em)
            else TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.08.em, color = ink),
        )
    }
}

/** The web's `ghost` `size="icon"` round button */
@Composable
internal fun RoundIconButton(icon: Int, description: String, tint: Color, size: androidx.compose.ui.unit.Dp = 28.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
    }
}

/** "✓ Done": Predator's parallelogram accent button, Classic's primary pill */
@Composable
private fun DoneButton(predator: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.item_done)
    if (predator) {
        PdButton(onClick = onClick, enabled = enabled, modifier = Modifier.height(28.dp), padding = PaddingValues(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(LucideIcons.CHECK), null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(label.uppercase())
            }
        }
    } else {
        val colors = MaterialTheme.colorScheme
        Row(
            Modifier.height(32.dp).clip(CircleShape)
                .background(if (enabled) colors.primary else colors.primary.copy(alpha = 0.4f))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(LucideIcons.CHECK), null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary))
        }
    }
}

@Composable
internal fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit, message: Int = R.string.item_discard_text) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.item_discard_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.item_discard), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.item_keep_editing)) } },
    )
}

/** "dd.MM.yyyy" in the device's zone, as the web shows it; null for an unreadable timestamp */
private fun formatCreated(iso: String): String? = runCatching {
    val instant = runCatching { OffsetDateTime.parse(iso).toInstant() }
        // timestamp without time zone: UTC, as Supabase stores it
        .getOrElse { java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC) }
    instant.atZone(ZoneId.systemDefault()).format(CREATED_FORMAT)
}.getOrNull()
