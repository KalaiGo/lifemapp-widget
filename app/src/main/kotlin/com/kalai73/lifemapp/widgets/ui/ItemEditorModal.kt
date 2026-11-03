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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.content.ItemHtml
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.designsystem.CondensedFontFamily
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

/**
 * Editor height: the web's COMPACT_EDITOR_HEIGHT (140) plus 30% for a phone; the expand button makes it
 * [EXPANDED_EDITOR_HEIGHT], three times as tall
 */
internal val COMPACT_EDITOR_HEIGHT = 182.dp
private val EXPANDED_EDITOR_HEIGHT = 546.dp
private val CREATED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/**
 * The web's item editor (subsubdomain-editor-modal.tsx), opened from an item's long-press menu: the "• ITEM" badge
 * with expand and close, the title as a large text field, the rich text editor, the created date with the
 * character count against the plan's limit, and Remove / Done. Predator draws the chamfered panel and the
 * parallelogram Done; Classic the rounded card and pill.
 *
 * Done saves what changed (the name and / or the text) and closes; closing with changes asks to discard them.
 * The text is only sent when it was edited, so formatting the app can't hold stays as stored unless it was.
 */
@Composable
internal fun ItemEditorDialog(
    item: TopicItemUi,
    /** Rename puts the cursor in the title, Edit content in the text */
    focusTitle: Boolean,
    loadText: suspend (String) -> ItemText,
    onSave: (name: String?, html: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** False to show the item without putting the cursor anywhere (opened from the widget, to read) */
    autoFocus: Boolean = true,
) {
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val pd = LocalPdPalette.current
    val colors = MaterialTheme.colorScheme
    // Predator inks from the palette, Classic from the Material scheme
    val text = if (predator) pd.text else colors.onSurface
    val dim = if (predator) pd.dim else colors.onSurfaceVariant
    val accentInk = if (predator) pd.ink else colors.primary
    val line = if (predator) pd.edge else colors.outlineVariant

    val nameKnown = item.name != null
    var name by remember(item.id) { mutableStateOf(TextFieldValue(item.name.orEmpty(), TextRange(item.name.orEmpty().length))) }
    var textState by remember(item.id) { mutableStateOf<TextState>(TextState.Loading) }
    var editor by remember(item.id) { mutableStateOf<RichEditText?>(null) }
    var tick by remember(item.id) { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

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
    val nameChanged = nameKnown && newName != item.name
    val textChanged = tick >= 0 && editor?.changed == true
    val dirty = nameChanged || textChanged
    val ready = textState as? TextState.Ready
    val length = if (tick >= 0) editor?.text?.length ?: 0 else 0
    val maxChars = ready?.text?.maxChars
    val overLimit = maxChars != null && length > maxChars
    val canSave = (!nameKnown || newName.isNotEmpty()) && !overLimit

    val close = { if (dirty) confirmDiscard = true else onDismiss() }
    val done = {
        if (!dirty) onDismiss()
        else if (canSave) onSave(if (nameChanged) newName else null, if (textChanged) editor?.text?.let(ItemHtml::toHtml) else null)
    }

    if (confirmRemove) RemoveItemDialog(item, onConfirm = { confirmRemove = false; onDelete() }, onDismiss = { confirmRemove = false })
    if (confirmDiscard) DiscardDialog(onDiscard = { confirmDiscard = false; onDismiss() }, onKeep = { confirmDiscard = false })

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val panel = if (predator) {
            Modifier.pdPanel(pd, cut = 14.dp)
        } else {
            val shape = RoundedCornerShape(24.dp)
            Modifier.clip(shape).background(colors.surface, shape).border(1.dp, line, shape)
        }
        // Anchored near the top as on the web (top-6), not centered; a tap beside the panel closes it
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp).widthIn(max = 480.dp).fillMaxWidth().imePadding()
                .then(panel)
                // Taps on the panel itself must not reach the backdrop
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: badge, expand, close
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemBadge(predator, accentInk)
                RoundIconButton(
                    if (expanded) LucideIcons.MINIMIZE else LucideIcons.MAXIMIZE,
                    stringResource(if (expanded) R.string.today_collapse else R.string.today_expand),
                    dim,
                ) { expanded = !expanded }
                Spacer(Modifier.weight(1f))
                RoundIconButton(LucideIcons.CLOSE, stringResource(R.string.item_close), dim, size = 32.dp) { close() }
            }

            // Hero title: a borderless large field over a faint rule
            Column {
                val titleStyle = TextStyle(
                    fontFamily = if (predator) CondensedFontFamily else FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.01).em,
                    color = if (nameKnown) text else dim,
                )
                if (nameKnown) {
                    Box {
                        if (name.text.isEmpty()) Text(stringResource(R.string.item_title_hint), style = titleStyle.copy(color = dim.copy(alpha = 0.35f)))
                        BasicTextField(
                            name, { name = it },
                            textStyle = titleStyle,
                            cursorBrush = SolidColor(accentInk),
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                        )
                    }
                } else {
                    Text(stringResource(R.string.today_locked), style = titleStyle)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(line.copy(alpha = 0.3f)))
            }

            // Content canvas: toolbar and editor in one bordered box
            when (val state = textState) {
                TextState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = COMPACT_EDITOR_HEIGHT), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = accentInk, strokeWidth = 2.dp)
                }
                is TextState.Unavailable -> Text(stringResource(state.message), style = MaterialTheme.typography.bodyMedium, color = dim)
                is TextState.Ready -> RichTextBox(
                    html = state.text.html,
                    hint = stringResource(R.string.item_text_hint),
                    height = if (expanded) EXPANDED_EDITOR_HEIGHT else COMPACT_EDITOR_HEIGHT,
                    editor = editor,
                    tick = tick,
                    focus = autoFocus && (!focusTitle || !nameKnown),
                    onEditor = { editor = it },
                    onChange = { tick++ },
                )
            }
            if (ready != null && ItemHtml.hasUnsupportedFormatting(ready.text.html)) {
                Text(stringResource(R.string.item_text_simplified), style = MaterialTheme.typography.bodySmall, color = dim)
            }

            // Created date and character count
            if (ready != null) {
                val meta = if (predator) pdCaptionStyle(10.sp, dim.copy(alpha = 0.7f), letterSpacing = 0.06.em)
                else TextStyle(fontSize = 11.sp, color = dim.copy(alpha = 0.6f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val created = ready.text.createdAt?.let(::formatCreated)
                    Text(created?.let { stringResource(R.string.item_created, it) }.orEmpty(), style = meta, modifier = Modifier.weight(1f))
                    if (maxChars != null) {
                        Text(
                            "$length/$maxChars",
                            style = if (overLimit) meta.copy(color = colors.error, fontWeight = FontWeight.SemiBold) else meta,
                        )
                    }
                }
            }

            // Footer: Remove on the left, Done on the right, over a faint rule
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(line.copy(alpha = 0.4f)))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(CircleShape)
                            .clickable(role = Role.Button) { confirmRemove = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(LucideIcons.TRASH), null, tint = dim, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.item_remove), style = TextStyle(fontSize = 12.sp, color = dim))
                    }
                    Spacer(Modifier.weight(1f))
                    DoneButton(predator, enabled = !dirty || canSave, onClick = done)
                }
            }
            }
        }
    }
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
    height: androidx.compose.ui.unit.Dp,
    editor: RichEditText?,
    tick: Int,
    focus: Boolean,
    onEditor: (RichEditText) -> Unit,
    onChange: () -> Unit,
) {
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val pd = LocalPdPalette.current
    val colors = MaterialTheme.colorScheme
    val text = if (predator) pd.text else colors.onSurface
    val dim = if (predator) pd.dim else colors.onSurfaceVariant
    val accentInk = if (predator) pd.ink else colors.primary
    val line = if (predator) pd.edge else colors.outlineVariant
    val boxShape = if (predator) pdChamferShape(6.dp) else RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(boxShape)
            .background(if (predator) pd.well.copy(alpha = 0.35f) else colors.onSurface.copy(alpha = 0.03f), boxShape)
            .border(1.dp, line.copy(alpha = if (predator) 1f else 0.4f), boxShape),
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
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}

/** "• ITEM": the web's rounded primary badge with its dot (also "• EVENT", "• TASK" on the details modals) */
@Composable
internal fun ItemBadge(predator: Boolean, ink: Color, label: String = stringResource(R.string.item_badge).uppercase()) {
    Row(
        Modifier.clip(CircleShape)
            .background(ink.copy(alpha = 0.1f))
            .border(1.dp, ink.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = if (predator) pdCaptionStyle(10.sp, ink, letterSpacing = 0.1.em).copy(fontWeight = FontWeight.Bold)
            else TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.08.em, color = ink),
        )
    }
}

/** The web's `ghost` `size="icon"` round button */
@Composable
internal fun RoundIconButton(icon: Int, description: String, tint: Color, size: androidx.compose.ui.unit.Dp = 28.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** "✓ Done": Predator's parallelogram accent button, Classic's primary pill */
@Composable
private fun DoneButton(predator: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.item_done)
    if (predator) {
        PdButton(onClick = onClick, enabled = enabled, modifier = Modifier.height(36.dp)) {
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

/** "dd.MM.yyyy HH:mm" in the device's zone, as the web shows it; null for an unreadable timestamp */
private fun formatCreated(iso: String): String? = runCatching {
    val instant = runCatching { OffsetDateTime.parse(iso).toInstant() }
        // timestamp without time zone: UTC, as Supabase stores it
        .getOrElse { java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC) }
    instant.atZone(ZoneId.systemDefault()).format(CREATED_FORMAT)
}.getOrNull()
