package com.kalai73.lifemapp.widgets.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.TagColors
import com.kalai73.lifemapp.widgets.core.designsystem.pdChamferShape
import com.kalai73.lifemapp.widgets.core.model.CustomTag
import com.kalai73.lifemapp.widgets.core.presentation.panel.ItemTags
import com.kalai73.lifemapp.widgets.core.presentation.panel.TagNameError
import com.kalai73.lifemapp.widgets.ui.Ic
import com.kalai73.lifemapp.widgets.ui.LocalTok
import com.kalai73.lifemapp.widgets.ui.framed

/**
 * Dialog to create, rename, recolor, and delete user-created context tags
 * (port of `lifeMappWeb/frontend/src/components/domains/tag-manager-dialog.tsx`).
 *
 * When [onCreated] is non-null, runs in `create` mode: adding a tag hands its id to [onCreated] and closes.
 * Otherwise runs in `manage` mode: shows existing tags for inline rename, recolor, and delete.
 */
@Composable
fun TagManagerDialog(
    customTags: List<CustomTag>,
    onCreated: ((String) -> Unit)? = null,
    onSaveCustomTags: (List<CustomTag>) -> Unit,
    onDeleteCustomTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTok.current
    val createMode = onCreated != null
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(ItemTags.nextTagColor(customTags)) }
    var touched by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val atLimit = customTags.size >= ItemTags.MAX_CUSTOM_TAGS
    val error = ItemTags.tagNameError(name, customTags)

    fun addTag() {
        touched = true
        if (error != null || atLimit) return
        val newTag = CustomTag(
            id = ItemTags.newCustomTagId(),
            name = name.trim(),
            color = color,
        )
        val updated = customTags + newTag
        onSaveCustomTags(updated)
        if (createMode) {
            onCreated.invoke(newTag.id)
            onDismiss()
            return
        }
        name = ""
        touched = false
        color = ItemTags.nextTagColor(updated)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 440.dp)
                .heightIn(max = 620.dp)
                .imePadding()
                .framed(t, t.card, edge = t.line, cut = 12.dp)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (createMode) R.string.tag_new_title else R.string.tag_manager_title),
                    style = t.label(16.sp, t.ink),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Ic(LucideIcons.CLOSE, 16.dp, t.dim, stringResource(R.string.panel_close))
                }
            }
            Text(stringResource(R.string.tag_manager_description), style = t.body(12.sp, t.dim))

            // New tag form
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val inputShape: Shape = if (t.predator) pdChamferShape(4.dp) else RoundedCornerShape(10.dp)
                val borderColor = if (touched && error != null) t.destructive else t.line
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(inputShape)
                        .background(t.well, inputShape)
                        .border(1.dp, borderColor, inputShape)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (name.isEmpty()) {
                        Text(stringResource(R.string.tag_name_placeholder), style = t.body(13.sp, t.faint))
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { if (it.length <= ItemTags.MAX_TAG_NAME_LENGTH) name = it },
                        enabled = !atLimit,
                        singleLine = true,
                        textStyle = t.body(13.sp, t.text),
                        cursorBrush = SolidColor(t.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTag() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }

                val btnShape: Shape = if (t.predator) pdChamferShape(4.dp) else RoundedCornerShape(10.dp)
                Row(
                    Modifier
                        .height(38.dp)
                        .clip(btnShape)
                        .background(if (atLimit) t.well else t.accent, btnShape)
                        .clickable(enabled = !atLimit, onClick = ::addTag)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Ic(LucideIcons.ADD, 14.dp, if (atLimit) t.dim else t.onAccent)
                    Text(
                        stringResource(R.string.tag_add),
                        style = t.body(13.sp, if (atLimit) t.dim else t.onAccent, FontWeight.Bold),
                    )
                }
            }

            if (atLimit) {
                Text(stringResource(R.string.tag_limit_reached, ItemTags.MAX_CUSTOM_TAGS), style = t.caption(11.sp, t.dim))
            } else if (touched && error != null) {
                Text(tagErrorText(error), style = t.caption(11.sp, t.destructive))
            }

            if (!atLimit) {
                ColorSwatches(value = color, onChange = { color = it })
            }

            // Existing custom tags (manage mode)
            if (!createMode) {
                HorizontalDivider(color = t.line.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.tag_your_tags), style = t.body(13.sp, t.text, FontWeight.Bold))
                    Text(
                        stringResource(R.string.tag_count, customTags.size, ItemTags.MAX_CUSTOM_TAGS),
                        style = t.caption(11.sp, t.dim),
                    )
                }
                if (customTags.isEmpty()) {
                    Text(stringResource(R.string.tag_no_custom), style = t.body(13.sp, t.dim))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        customTags.forEach { tag ->
                            CustomTagRow(
                                tag = tag,
                                customTags = customTags,
                                onUpdate = { updated ->
                                    onSaveCustomTags(customTags.map { if (it.id == tag.id) updated else it })
                                },
                                onDelete = { onDeleteCustomTag(tag.id) },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.tag_task_label_hint), style = t.caption(11.sp, t.dim))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.item_done), style = t.body(13.sp, t.ink, FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomTagRow(
    tag: CustomTag,
    customTags: List<CustomTag>,
    onUpdate: (CustomTag) -> Unit,
    onDelete: () -> Unit,
) {
    val t = LocalTok.current
    var draftName by remember(tag.id, tag.name) { mutableStateOf(tag.name) }
    var showColors by remember(tag.id) { mutableStateOf(false) }
    var confirmingDelete by remember(tag.id) { mutableStateOf(false) }

    val error = if (draftName.trim() == tag.name) null else ItemTags.tagNameError(draftName, customTags, tag.id)
    fun commitName() {
        val trimmed = draftName.trim()
        if (trimmed == tag.name) return
        if (error != null) draftName = tag.name else onUpdate(tag.copy(name = trimmed))
    }

    if (confirmingDelete) {
        val shape: Shape = if (t.predator) pdChamferShape(4.dp) else RoundedCornerShape(10.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(t.destructive.copy(alpha = 0.08f), shape)
                .border(1.dp, t.destructive.copy(alpha = 0.45f), shape)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tag_delete_confirm, tag.name), style = t.body(12.sp, t.text))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.panel_cancel), style = t.body(12.sp, t.dim))
                }
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.panel_delete), style = t.body(12.sp, t.destructive, FontWeight.Bold))
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val swatchShape: Shape = if (t.predator) pdChamferShape(3.dp) else CircleShape
            val colorDesc = stringResource(R.string.tag_color)
            Box(
                Modifier
                    .size(24.dp)
                    .clip(swatchShape)
                    .background(TagColors.custom(tag.color).swatch, swatchShape)
                    .clickable(onClickLabel = colorDesc) { showColors = !showColors }
                    .semantics { contentDescription = colorDesc },
            )
            val inputShape: Shape = if (t.predator) pdChamferShape(4.dp) else RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(inputShape)
                    .background(t.well, inputShape)
                    .border(1.dp, if (error != null) t.destructive else t.line, inputShape)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = draftName,
                    onValueChange = { if (it.length <= ItemTags.MAX_TAG_NAME_LENGTH) draftName = it },
                    singleLine = true,
                    textStyle = t.body(13.sp, t.text),
                    cursorBrush = SolidColor(t.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitName() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (draftName.trim() != tag.name && error == null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = ::commitName),
                    contentAlignment = Alignment.Center,
                ) {
                    Ic(LucideIcons.CHECK, 16.dp, t.ink, stringResource(R.string.panel_save))
                }
            }
            val delDesc = stringResource(R.string.tag_delete, tag.name)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = delDesc) { confirmingDelete = true },
                contentAlignment = Alignment.Center,
            ) {
                Ic(LucideIcons.TRASH, 15.dp, t.dim, delDesc)
            }
        }
        if (error != null) {
            Text(tagErrorText(error), style = t.caption(11.sp, t.destructive), modifier = Modifier.padding(start = 32.dp))
        }
        if (showColors) {
            Box(Modifier.padding(start = 32.dp)) {
                ColorSwatches(
                    value = tag.color,
                    size = 22.dp,
                    onChange = { nextColor ->
                        onUpdate(tag.copy(color = nextColor))
                        showColors = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(
    value: String,
    onChange: (String) -> Unit,
    size: Dp = 26.dp,
) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdChamferShape(3.dp) else CircleShape
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ItemTags.CUSTOM_TAG_COLORS.forEach { colorName ->
            val selected = colorName == value
            val desc = tagColorLabel(colorName)
            Box(
                Modifier
                    .size(size)
                    .clip(shape)
                    .background(TagColors.custom(colorName).swatch, shape)
                    .let { if (selected) it.border(2.dp, t.text.copy(alpha = 0.85f), shape) else it }
                    .clickable(onClickLabel = desc) { onChange(colorName) }
                    .semantics { contentDescription = desc },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Ic(LucideIcons.CHECK, 12.dp, Color.White)
            }
        }
    }
}

@Composable
private fun tagErrorText(error: TagNameError): String = when (error) {
    TagNameError.EMPTY -> stringResource(R.string.tag_error_empty)
    TagNameError.TOO_LONG -> stringResource(R.string.tag_error_too_long, ItemTags.MAX_TAG_NAME_LENGTH)
    TagNameError.DUPLICATE -> stringResource(R.string.tag_error_duplicate)
}

@Composable
private fun tagColorLabel(color: String): String = when (color) {
    "emerald" -> stringResource(R.string.tag_color_emerald)
    "amber" -> stringResource(R.string.tag_color_amber)
    "violet" -> stringResource(R.string.tag_color_violet)
    "teal" -> stringResource(R.string.tag_color_teal)
    "orange" -> stringResource(R.string.tag_color_orange)
    "fuchsia" -> stringResource(R.string.tag_color_fuchsia)
    "lime" -> stringResource(R.string.tag_color_lime)
    "indigo" -> stringResource(R.string.tag_color_indigo)
    "slate" -> stringResource(R.string.tag_color_slate)
    else -> color
}
