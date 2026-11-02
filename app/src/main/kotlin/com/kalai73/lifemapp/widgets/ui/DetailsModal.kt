package com.kalai73.lifemapp.widgets.ui

import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.presentation.EventUi
import com.kalai73.lifemapp.widgets.core.data.presentation.ItemHtml
import com.kalai73.lifemapp.widgets.core.data.presentation.TaskUi
import com.kalai73.lifemapp.widgets.core.designsystem.CondensedFontFamily
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdButton
import com.kalai73.lifemapp.widgets.core.designsystem.pdPanel

/** An event or task a widget tap asked the Today screen to show in its details modal. */
sealed interface TodayEntry {
    /** [start] is the snapshot's start, telling a repeating event's instances apart; null matches any */
    data class Event(val id: String, val start: String?) : TodayEntry
    data class Task(val id: String) : TodayEntry
}

/**
 * The web's event details modal (calendar/event-details-modal.tsx): title with the status badge, When, Location,
 * Description and the calendar, then "Open event" for the provider's page when there is one.
 */
@Composable
internal fun EventDetailsDialog(event: EventUi, openLink: (String) -> Unit, onDismiss: () -> Unit) {
    val t = LocalTok.current
    val color = LifeMappColors.parseHex(event.color) ?: Color(0xFF3B82F6)
    DetailsPanel(
        badge = stringResource(R.string.details_event_badge),
        tag = event.calendarName?.takeIf { it.isNotBlank() }?.let { it to color },
        title = event.title,
        status = event.status,
        onDismiss = onDismiss,
        footer = {
            event.link?.let { link ->
                PrimaryButton(LucideIcons.EXTERNAL_LINK, stringResource(R.string.details_open_event)) { openLink(link) }
            }
        },
    ) {
        Field(LucideIcons.CLOCK, stringResource(R.string.details_when), accent = true) {
            if (event.dateLabel.isNotEmpty()) Text(event.dateLabel, style = t.body(14.sp, t.text, FontWeight.Medium))
            Text(event.timeLabel ?: stringResource(R.string.today_all_day), style = t.body(13.sp, t.dim))
        }
        event.location?.let { location ->
            Field(LucideIcons.MAP_PIN, stringResource(R.string.details_location)) { LinkedText(location, t.text) }
        }
        event.description?.let { description ->
            Field(LucideIcons.ALIGN_LEFT, stringResource(R.string.details_description)) { LinkedText(description, t.dim) }
        }
        event.calendarName?.takeIf { it.isNotBlank() }?.let { calendar ->
            Field(LucideIcons.CALENDAR, stringResource(R.string.details_calendar)) { ColorLine(calendar, color) }
        }
    }
}

/**
 * The web's task details modal (tasks/task-details-modal.tsx), to read: title with the provider tag, due date,
 * priority, list, repeat and description, then Complete (tasks the app can complete) and the web's task list
 * for editing.
 */
@Composable
internal fun TaskDetailsDialog(task: TaskUi, onComplete: (String) -> Unit, onOpenWeb: (String) -> Unit, onDismiss: () -> Unit) {
    val t = LocalTok.current
    val tagColor = LifeMappColors.parseHex(task.tagColor) ?: Color(0xFF10B981)
    DetailsPanel(
        badge = stringResource(R.string.details_task_badge),
        tag = task.tagLabel?.let { it to tagColor },
        title = task.title,
        status = null,
        onDismiss = onDismiss,
        footer = {
            SecondaryButton(LucideIcons.EXTERNAL_LINK, stringResource(R.string.details_open_tasks)) {
                onOpenWeb(com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes.TASKS)
            }
            if (task.completable) {
                Spacer(Modifier.width(8.dp))
                PrimaryButton(LucideIcons.CHECK, stringResource(R.string.today_complete_task)) { onComplete(task.id); onDismiss() }
            }
        },
    ) {
        val overdue = task.overdue && !task.upcoming
        val dueColor = if (overdue) t.destructive else t.text
        Field(LucideIcons.CALENDAR, stringResource(R.string.details_due), accent = true) {
            val due = task.dueLabel ?: if (task.dueDate != null) stringResource(R.string.today_day_today) else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(due ?: stringResource(R.string.details_no_due), style = t.body(14.sp, if (due == null) t.dim else dueColor, FontWeight.Medium))
                if (overdue) {
                    Spacer(Modifier.width(6.dp))
                    Ic(LucideIcons.WARNING, 13.dp, t.destructive)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.details_overdue), style = t.caption(10.sp, t.destructive))
                }
            }
            if (task.recurring) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic(LucideIcons.REPEAT, 12.dp, t.dim)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.details_repeats), style = t.body(13.sp, t.dim))
                }
            }
        }
        Field(LucideIcons.FLAG, stringResource(R.string.details_priority)) {
            // Todoist-style: 4 is P1, the highest
            val p = (5 - task.priority).coerceIn(1, 4)
            ColorLine("P$p", t.priority(task.priority) ?: t.dim)
        }
        task.listName?.takeIf { it.isNotBlank() }?.let { list ->
            Field(LucideIcons.TASKS, stringResource(R.string.details_list)) {
                ColorLine(list, LifeMappColors.parseHex(task.listColor) ?: t.dim)
            }
        }
        when {
            task.description != null -> Field(LucideIcons.ALIGN_LEFT, stringResource(R.string.details_description)) {
                LinkedText(task.description!!, t.dim)
            }
            task.descriptionLocked -> Field(LucideIcons.LOCK, stringResource(R.string.details_description)) {
                Text(stringResource(R.string.details_description_locked), style = t.body(13.sp, t.dim))
            }
        }
    }
}

/**
 * The shared modal shell, as the item editor's: Predator's chamfered panel or Classic's rounded card near the top,
 * the badge (with the calendar / provider tag) and close, the large title over a faint rule, the scrolling
 * fields, and the footer buttons on the right. A tap beside the panel closes it.
 */
@Composable
private fun DetailsPanel(
    badge: String,
    tag: Pair<String, Color>?,
    title: String?,
    status: String?,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTok.current
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val panel = if (t.predator) {
            Modifier.pdPanel(t.pd, cut = 14.dp)
        } else {
            val shape = RoundedCornerShape(24.dp)
            Modifier.clip(shape).background(colors.surface, shape).border(1.dp, t.line, shape)
        }
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp).widthIn(max = 480.dp).fillMaxWidth()
                    .then(panel)
                    // Taps on the panel itself must not reach the backdrop
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemBadge(t.predator, t.ink, badge.uppercase())
                    tag?.let { (label, color) -> Tag(label, color) }
                    Spacer(Modifier.weight(1f))
                    RoundIconButton(LucideIcons.CLOSE, stringResource(R.string.item_close), t.dim, size = 32.dp, onClick = onDismiss)
                }

                Column {
                    Text(
                        title ?: stringResource(R.string.today_locked),
                        style = TextStyle(
                            fontFamily = if (t.predator) CondensedFontFamily else FontFamily.Default,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.01).em,
                            color = if (title == null) t.dim else t.text,
                        ),
                    )
                    status?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it.uppercase(),
                            style = t.label(10.sp, t.dim),
                            modifier = Modifier.clip(t.shape(3.dp)).background(t.well).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(t.line.copy(alpha = 0.3f)))
                }

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )

                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(t.line.copy(alpha = 0.4f)))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        footer()
                    }
                }
            }
        }
    }
}

/** A labeled section: icon and caption, then its lines indented under the caption, as the web's modal sections. */
@Composable
private fun Field(icon: Int, label: String, accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTok.current
    val color = if (accent) t.ink else t.dim
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, 14.dp, color)
            Spacer(Modifier.width(8.dp))
            Text(t.s(label), style = t.label(11.sp, color))
        }
        Column(Modifier.padding(start = 22.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** A dot in [color] before [text] (calendar, list, priority). */
@Composable
private fun ColorLine(text: String, color: Color) {
    val t = LocalTok.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = t.body(14.sp, t.text, FontWeight.Medium))
    }
}

/** The calendar or provider tag next to the badge, as on the rows */
@Composable
private fun Tag(label: String, color: Color) {
    val t = LocalTok.current
    Text(
        t.s(label), style = t.label(9.sp, color), maxLines = 1,
        modifier = Modifier.background(t.tinted(color, 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private val HTML_TAG = Regex("<[a-zA-Z/][^>]*>")

/**
 * Text with working links: HTML (task descriptions, Google event descriptions) as rich text, plain text with its
 * web addresses, e-mail addresses and phone numbers linked. Selectable, so it can be copied.
 */
@Composable
private fun LinkedText(value: String, color: Color) {
    val t = LocalTok.current
    val text: CharSequence = remember(value) {
        if (HTML_TAG.containsMatchIn(value)) ItemHtml.toSpanned(value)
        else SpannableString(value).also { Linkify.addLinks(it, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS) }
    }
    val textColor = color.toArgb()
    val linkColor = t.ink.toArgb()
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setLineSpacing(0f, 1.35f)
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            if (view.text.toString() != text.toString()) view.text = text
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The footer's main action: Predator's parallelogram accent button, Classic's primary pill */
@Composable
private fun PrimaryButton(icon: Int, label: String, onClick: () -> Unit) {
    val t = LocalTok.current
    if (t.predator) {
        PdButton(onClick = onClick, modifier = Modifier.height(36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(label.uppercase())
            }
        }
    } else {
        val colors = MaterialTheme.colorScheme
        Row(
            Modifier.height(32.dp).clip(CircleShape).background(colors.primary)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary))
        }
    }
}

/** A quieter footer action: icon and text, as the item editor's Remove */
@Composable
private fun SecondaryButton(icon: Int, label: String, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, 14.dp, t.dim)
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(fontSize = 12.sp, color = t.dim))
    }
}
