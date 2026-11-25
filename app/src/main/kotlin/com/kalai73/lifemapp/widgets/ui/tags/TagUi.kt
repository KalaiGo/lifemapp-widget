package com.kalai73.lifemapp.widgets.ui.tags

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.TagColors
import com.kalai73.lifemapp.widgets.core.designsystem.pdChamferShape
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.model.CustomTag
import com.kalai73.lifemapp.widgets.core.presentation.panel.ItemTags
import com.kalai73.lifemapp.widgets.ui.Ic
import com.kalai73.lifemapp.widgets.ui.LocalTok
import com.kalai73.lifemapp.widgets.ui.framed

/**
 * Shared context-tag environment provided by [com.kalai73.lifemapp.widgets.ui.MainDashboard] so any card,
 * menu, editor, or side-panel screen can read custom tags, open the Tag Manager, and update the active filter.
 */
data class TagEnvironment(
    val activeTag: String = CustomTag.TAG_ALL,
    val customTags: List<CustomTag> = emptyList(),
    val onSelectFilter: (String) -> Unit = {},
    val onOpenManager: (onCreated: ((String) -> Unit)?) -> Unit = {},
    val onSaveCustomTags: (List<CustomTag>) -> Unit = {},
    val onDeleteCustomTag: (String) -> Unit = {},
)

val LocalTags = compositionLocalOf { TagEnvironment() }

/** Display name of a context tag ("all", "me", "work", "family", or custom tag id). */
@Composable
fun tagDisplayName(tag: String, customTags: List<CustomTag>): String = when (tag) {
    CustomTag.TAG_ALL -> stringResource(R.string.tag_all)
    "me" -> stringResource(R.string.tag_me)
    "work" -> stringResource(R.string.tag_work)
    "family" -> stringResource(R.string.tag_family)
    else -> customTags.firstOrNull { it.id == tag }?.name ?: stringResource(R.string.tag_me)
}

/**
 * Small colored icons marking the tags of a topic, item, or task (port of `item-tags.tsx TagBadges`).
 * Leaves out `me` unless [includeMe] is true (used on items & tasks).
 */
@Composable
fun TagBadges(
    tags: List<String>,
    customTags: List<CustomTag> = LocalTags.current.customTags,
    includeMe: Boolean = false,
    iconSize: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTok.current
    val shown = tags
        .filter { ItemTags.isKnownTag(it, customTags) }
        .filter { includeMe || it != ItemTags.DEFAULT_TAG }
    if (shown.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        shown.forEach { tag ->
            val label = tagDisplayName(tag, customTags)
            val color = TagColors.textColor(tag, customTags, t.dark, t.ink)
            Ic(TagColors.icon(tag), iconSize, color, desc = label)
        }
    }
}

/**
 * Floating context-tag filter button at the top-left of Today and Monthly (port of `TagFilterButton`),
 * plus [compact] inline pill variant (`TagFilterPill`, e.g. in the Tasks card header).
 */
@Composable
fun TagFilterDropdown(
    activeTag: String = LocalTags.current.activeTag,
    customTags: List<CustomTag> = LocalTags.current.customTags,
    onSelect: (String) -> Unit = LocalTags.current.onSelectFilter,
    onOpenManager: (onCreated: ((String) -> Unit)?) -> Unit = LocalTags.current.onOpenManager,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val t = LocalTok.current
    var expanded by remember { mutableStateOf(false) }
    val selected = ItemTags.resolveFilter(activeTag, customTags) ?: CustomTag.TAG_ALL
    val label = tagDisplayName(selected, customTags)
    val contentColor = if (selected == CustomTag.TAG_ALL) t.text else TagColors.textColor(selected, customTags, t.dark, t.ink)
    val ringColor = TagColors.ringColor(selected, customTags, if (t.predator) t.pd.edge else t.line)
    val filterAria = stringResource(R.string.tag_filter_aria, label)

    val shape: Shape = when {
        t.predator -> pdChamferShape(if (compact) 4.dp else 6.dp)
        else -> RoundedCornerShape(50)
    }
    val bg = when {
        compact -> if (t.predator) t.pd.well else t.card.copy(alpha = 0.75f)
        t.predator -> t.pd.panel
        t.dark -> Color(0xFF0A0A0A)
        else -> Color.White
    }

    Box(modifier) {
        Row(
            Modifier
                .let { if (!compact && !t.predator) it.shadow(6.dp, shape, clip = false) else it }
                .height(if (compact) 28.dp else 36.dp)
                .widthIn(max = if (compact) 144.dp else 188.dp)
                .clip(shape)
                .background(bg, shape)
                .border(if (compact) 1.dp else 1.5.dp, ringColor, shape)
                .clickable(onClickLabel = filterAria) { expanded = true }
                .semantics { contentDescription = filterAria }
                .padding(start = if (compact) 10.dp else 12.dp, end = if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        ) {
            Ic(TagColors.icon(selected), if (compact) 13.dp else 15.dp, contentColor)
            Text(
                label.uppercase(),
                style = if (t.predator) t.label(if (compact) 11.sp else 12.sp, contentColor)
                else t.body(if (compact) 11.sp else 12.sp, contentColor, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Ic(LucideIcons.CHEVRON_DOWN, 12.dp, contentColor.copy(alpha = 0.65f))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 168.dp, max = 230.dp),
        ) {
            Text(
                stringResource(R.string.tag_filter_title),
                style = t.caption(11.sp, t.dim),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            HorizontalDivider(color = t.line.copy(alpha = 0.5f))
            val choices = ItemTags.tagFilters(customTags)
            choices.forEach { choice ->
                val isSelected = choice == selected
                val choiceLabel = tagDisplayName(choice, customTags)
                val choiceColor = if (choice == CustomTag.TAG_ALL) t.text else TagColors.textColor(choice, customTags, t.dark, t.ink)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ic(TagColors.icon(choice), 15.dp, choiceColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                choiceLabel.uppercase(),
                                style = t.body(13.sp, t.text, if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Spacer(Modifier.width(6.dp))
                                Ic(LucideIcons.CHECK, 14.dp, t.ink)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    },
                )
            }
            HorizontalDivider(color = t.line.copy(alpha = 0.5f))
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(LucideIcons.ADD, 15.dp, t.dim)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tag_new), style = t.body(13.sp, t.text))
                    }
                },
                onClick = {
                    expanded = false
                    onOpenManager { createdId -> onSelect(createdId) }
                },
            )
            if (customTags.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ic(LucideIcons.PENCIL, 15.dp, t.dim)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tag_edit_tags), style = t.body(13.sp, t.text))
                        }
                    },
                    onClick = {
                        expanded = false
                        onOpenManager(null)
                    },
                )
            }
        }
    }
}

/**
 * Compact pill with tag summary (`Work +1`) that opens [TagPickerDialog], used in item editors
 * (port of `item-tags.tsx TagPill`).
 */
@Composable
fun TagPill(
    value: List<String>,
    locked: List<String> = emptyList(),
    required: Boolean = false,
    disabled: Boolean = false,
    customTags: List<CustomTag> = LocalTags.current.customTags,
    onOpenManager: (onCreated: ((String) -> Unit)?) -> Unit = LocalTags.current.onOpenManager,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTok.current
    var pickerOpen by remember { mutableStateOf(false) }
    val picked = value.filter { ItemTags.isKnownTag(it, customTags) }.ifEmpty { listOf(ItemTags.DEFAULT_TAG) }
    val first = picked.first()
    val firstColor = TagColors.textColor(first, customTags, t.dark, t.ink)
    val shape: Shape = if (t.predator) pdParallelogramShape(6.dp) else RoundedCornerShape(50)

    Row(
        modifier
            .height(28.dp)
            .widthIn(max = 156.dp)
            .clip(shape)
            .background(t.well.copy(alpha = 0.6f), shape)
            .border(1.dp, t.line, shape)
            .alpha(if (disabled) 0.5f else 1f)
            .clickable(enabled = !disabled) { pickerOpen = true }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        picked.take(3).forEach { tag ->
            Ic(TagColors.icon(tag), 12.dp, TagColors.textColor(tag, customTags, t.dark, t.ink))
        }
        Text(
            tagDisplayName(first, customTags),
            style = t.body(11.sp, firstColor, FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (picked.size > 1) {
            Text("+${picked.size - 1}", style = t.caption(10.sp, firstColor.copy(alpha = 0.75f)))
        }
        Ic(LucideIcons.CHEVRON_DOWN, 11.dp, t.dim)
    }

    if (pickerOpen) {
        TagPickerDialog(
            value = picked,
            locked = locked,
            required = required,
            customTags = customTags,
            onChange = onChange,
            onOpenManager = {
                pickerOpen = false
                onOpenManager { created ->
                    onChange(if (created in picked) picked else picked + created)
                }
            },
            onEditTags = {
                pickerOpen = false
                onOpenManager(null)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/**
 * Multi-select context tag picker dialog (port of `TagMenuSub` / `TagCheckboxItems`).
 * Supports [locked] tags (inherited from a topic — shown checked & disabled) and [required]
 * (topics must keep at least one tag checked).
 */
@Composable
fun TagPickerDialog(
    value: List<String>,
    locked: List<String> = emptyList(),
    required: Boolean = false,
    customTags: List<CustomTag> = LocalTags.current.customTags,
    onChange: (List<String>) -> Unit,
    onOpenManager: () -> Unit,
    onEditTags: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTok.current
    val known = value.filter { ItemTags.isKnownTag(it, customTags) }
    val allOptions = ItemTags.allTags(customTags)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 380.dp)
                .framed(t, t.card, edge = t.line, cut = 10.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tag_label_many), style = t.label(14.sp, t.ink))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                allOptions.forEach { tag ->
                    val checked = tag in known
                    val isLocked = tag in locked
                    val canToggle = !isLocked && !(required && known.size == 1 && known.first() == tag)
                    val color = TagColors.textColor(tag, customTags, t.dark, t.ink)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = canToggle) {
                                onChange(ItemTags.toggleTag(known, tag))
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = if (canToggle) { _ -> onChange(ItemTags.toggleTag(known, tag)) } else null,
                            enabled = canToggle,
                            colors = CheckboxDefaults.colors(checkedColor = t.accent),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Ic(TagColors.icon(tag), 16.dp, color)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tagDisplayName(tag, customTags),
                                style = t.body(14.sp, if (canToggle) t.text else t.dim, FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isLocked) {
                                Text(stringResource(R.string.tag_inherited), style = t.caption(10.sp, t.faint))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = t.line.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOpenManager) {
                        Ic(LucideIcons.ADD, 14.dp, t.ink)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tag_new), style = t.body(12.sp, t.ink, FontWeight.Medium))
                    }
                    if (customTags.isNotEmpty()) {
                        TextButton(onClick = onEditTags) {
                            Text(stringResource(R.string.tag_edit_tags), style = t.body(12.sp, t.dim))
                        }
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.item_done), style = t.body(13.sp, t.ink, FontWeight.Bold))
                }
            }
        }
    }
}

/**
 * Single-select context tag picker for tasks (`TaskEditPanel`, port of `task-details-modal.tsx`).
 */
@Composable
fun TaskTagPicker(
    selectedTag: String,
    customTags: List<CustomTag> = LocalTags.current.customTags,
    onSelect: (String) -> Unit,
    onOpenManager: (onCreated: ((String) -> Unit)?) -> Unit = LocalTags.current.onOpenManager,
    modifier: Modifier = Modifier,
) {
    val t = LocalTok.current
    var expanded by remember { mutableStateOf(false) }
    val current = if (ItemTags.isKnownTag(selectedTag, customTags)) selectedTag else ItemTags.DEFAULT_TAG
    val color = TagColors.textColor(current, customTags, t.dark, t.ink)
    val shape: Shape = if (t.predator) pdChamferShape(4.dp) else RoundedCornerShape(10.dp)

    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(t.well, shape)
                .border(1.dp, t.line, shape)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(TagColors.icon(current), 15.dp, color)
            Spacer(Modifier.width(8.dp))
            Text(
                tagDisplayName(current, customTags),
                style = t.body(13.sp, color, FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Ic(LucideIcons.CHEVRON_DOWN, 14.dp, t.dim)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ItemTags.allTags(customTags).forEach { tag ->
                val tagColor = TagColors.textColor(tag, customTags, t.dark, t.ink)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ic(TagColors.icon(tag), 15.dp, tagColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tagDisplayName(tag, customTags),
                                style = t.body(13.sp, t.text, if (tag == current) FontWeight.Bold else FontWeight.Normal),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(tag)
                    },
                )
            }
            HorizontalDivider(color = t.line.copy(alpha = 0.5f))
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(LucideIcons.ADD, 15.dp, t.dim)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tag_new), style = t.body(13.sp, t.text))
                    }
                },
                onClick = {
                    expanded = false
                    onOpenManager { created -> onSelect(created) }
                },
            )
        }
    }
}
