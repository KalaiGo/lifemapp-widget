package com.kalai73.lifemapp.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.presentation.DayKind
import com.kalai73.lifemapp.widgets.core.presentation.EventDayUi
import com.kalai73.lifemapp.widgets.core.presentation.EventLimits
import com.kalai73.lifemapp.widgets.core.presentation.EventUi
import com.kalai73.lifemapp.widgets.core.presentation.TaskUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdDrawables
import com.kalai73.lifemapp.widgets.core.designsystem.PredatorColors
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes

// ── Sections ────────────────────────────────────────────────────────────────

/**
 * The web's Today widget card (events, tasks, shortcuts): a bordered surface with an accent ring, rounded (Predator:
 * chamfered), the faint "01" watermark top-left, the honeycomb corner, and the centered title badge with its icon.
 * [rule] draws the line under the header (Classic), as the web's events and tasks cards have.
 */
@Composable
internal fun WidgetCard(title: String, icon: Int, rule: Boolean, onTitle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTok.current
    val shape = t.shape(if (t.predator) 8.dp else 24.dp)
    Box(
        Modifier.fillMaxWidth().padding(top = 32.dp)
            // The web's border plus its inset primary/20 ring: the line tinted toward the accent
            .framed(t, t.card, edge = PredatorColors.mix(t.line, t.accent, 0.35f), shape = shape)
            .hexCorner(t.accent, if (t.dark) 0.5f else 1f),
    ) {
        Text(
            "01",
            style = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Black, color = t.text.copy(alpha = if (t.dark) 0.06f else 0.04f), lineHeight = 64.sp),
            modifier = Modifier.padding(start = 16.dp).offset(y = (-6).dp),
        )
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.let {
                        // Classic: the frosted title badge; Predator: the bare display label
                        if (t.predator) it else it.clip(RoundedCornerShape(12.dp)).background(t.page.copy(alpha = 0.6f))
                            .border(1.5.dp, t.line, RoundedCornerShape(12.dp))
                    }.clickable(onClick = onTitle).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ic(icon, 14.dp, t.ink)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (t.predator) t.s(title) else title.uppercase(),
                        style = if (t.predator) t.label(16.sp) else t.body(14.sp, t.ink, FontWeight.Black),
                        maxLines = 1,
                    )
                }
            }
            if (rule && !t.predator) Box(Modifier.fillMaxWidth().height(1.dp).background(t.line.copy(alpha = 0.6f)))
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp), content = content)
        }
    }
}

/** Centered day divider: a line, "TODAY" / "TOMORROW" / the date, a line. */
@Composable
private fun DayHeader(label: String, emphasized: Boolean = false) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(t.line))
        Text(
            t.s(label),
            style = (if (emphasized) t.ink else t.dim).let { c -> if (t.predator) t.label(11.sp, c) else t.body(12.sp, c, FontWeight.Bold) },
            modifier = Modifier.padding(horizontal = 10.dp), maxLines = 1,
        )
        Box(Modifier.weight(1f).height(1.dp).background(t.line))
    }
}

@Composable
internal fun EmptyLine(text: String, onClick: () -> Unit) {
    val t = LocalTok.current
    Text(text, style = t.body(13.sp, t.dim), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp))
}

@Composable
internal fun EventsSection(ui: WidgetUi, onOpenEvent: (EventUi) -> Unit, onOpenWeb: (String) -> Unit) {
    // The user's events settings from the web (days to show, event cap), today always listed
    val (days, more) = EventLimits.apply(ui.eventDays, ui.today, ui.eventsDaysAhead, ui.eventsMaxEvents)
    val t = LocalTok.current
    WidgetCard(stringResource(R.string.today_events), LucideIcons.CALENDAR, rule = true, onTitle = { onOpenWeb(WebRoutes.EVENTS) }) {
        when {
            !ui.calendarConnected && ui.hasData -> EmptyLine(stringResource(R.string.today_not_connected)) { onOpenWeb(WebRoutes.SETTINGS) }
            days.none { it.events.isNotEmpty() } -> EmptyLine(stringResource(R.string.today_no_events)) { onOpenWeb(WebRoutes.EVENTS) }
            else -> {
                // Events are numbered across days (1, 2, 3 today, 4, 5 tomorrow), as on the web
                var offset = 0
                for (day in days) {
                    DayHeader(dayName(day))
                    if (day.events.isEmpty()) EmptyLine(stringResource(R.string.today_no_events)) { onOpenWeb(WebRoutes.EVENTS) }
                    day.events.forEachIndexed { i, e -> EventRow(e, offset + i + 1, day, onOpenEvent) }
                    offset += day.events.size
                }
                if (more > 0) {
                    Text(
                        stringResource(R.string.today_items_more, more), style = t.caption(10.sp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun dayName(day: EventDayUi): String = when (day.kind) {
    DayKind.TODAY -> stringResource(R.string.today_day_today)
    DayKind.TOMORROW -> stringResource(R.string.today_day_tomorrow)
    DayKind.OTHER -> day.label
}

/**
 * Numbered hex, calendar color bar, title with the calendar tag, then clock, time and date. Days after today are
 * faded; today's past events are faded with the title struck through (the web's isTodayPastEvent styling).
 */
@Composable
private fun EventRow(event: EventUi, number: Int, day: EventDayUi, onOpen: (EventUi) -> Unit) {
    val t = LocalTok.current
    val color = LifeMappColors.parseHex(event.color) ?: Color(0xFF3B82F6)
    val upcoming = day.kind != DayKind.TODAY
    val past = event.past && !upcoming
    val faded = upcoming || past
    val dimmed = faded || event.secondary
    val time = listOfNotNull(event.timeLabel ?: stringResource(R.string.today_all_day), day.label.ifEmpty { null }).joinToString(" · ")
    val calendar = event.calendarName?.takeIf { it.isNotBlank() }
    val titleColor = if (faded) t.faint else if (event.secondary) t.dim else t.text
    val captionColor = if (faded) t.faint else t.dim
    val barColor = if (dimmed) color.copy(alpha = 0.45f) else color
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberBadge(number, faded = faded)
        // Secondary calendars are indented from the left, as on the web's events card
        Spacer(Modifier.width(if (event.secondary) 18.dp else 6.dp))
        Row(
            // ...and more compact, without the card background, so primary events stand out
            Modifier.weight(1f).framed(t, if (event.secondary) Color.Transparent else t.row).clickable { onOpen(event) }
                .padding(horizontal = 8.dp, vertical = if (event.secondary) 3.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bar(barColor, 30.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        event.title,
                        style = t.body(14.sp, titleColor, FontWeight.Bold).let { if (past) it.copy(textDecoration = TextDecoration.LineThrough) else it },
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (calendar != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.s(calendar),
                            style = t.label(9.sp, if (dimmed) color.copy(alpha = 0.55f) else color),
                            maxLines = 1,
                            modifier = Modifier.background(t.tinted(color, if (dimmed) 0.06f else 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic(LucideIcons.CLOCK, 11.dp, captionColor)
                    Spacer(Modifier.width(4.dp))
                    Text(time, style = t.caption(10.sp, captionColor), maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun TasksSection(ui: WidgetUi, onComplete: (String) -> Unit, onOpenTask: (TaskUi) -> Unit, onOpenWeb: (String) -> Unit) {
    val t = LocalTok.current
    WidgetCard(stringResource(R.string.today_tasks), LucideIcons.TASKS, rule = true, onTitle = { onOpenWeb(WebRoutes.TASKS) }) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Modifier.weight(1f), LucideIcons.ADD, stringResource(R.string.today_add_task)) { onOpenWeb(WebRoutes.NEW_TASK) }
            ActionButton(Modifier.weight(1f), LucideIcons.TASKS, stringResource(R.string.today_task_lists)) { onOpenWeb(WebRoutes.TASKS) }
        }
        if (ui.completionFailed) {
            Text(stringResource(R.string.today_failed_completion), style = t.body(12.sp, t.destructive), modifier = Modifier.padding(vertical = 4.dp))
        }
        if (ui.tasks.isEmpty()) {
            EmptyLine(stringResource(R.string.today_no_tasks)) { onOpenWeb(WebRoutes.TASKS) }
        } else {
            // Segmented as on the web's Task List Widget: TODAY, TOMORROW, TOMORROW ++ 1, numbered across them.
            // Today always gets its divider; an empty one says so, as the web's "No tasks today" box does.
            if (ui.tasks.first().day != DayKind.TODAY) {
                DayHeader(taskDayName(DayKind.TODAY), emphasized = true)
                EmptyLine(stringResource(R.string.today_no_tasks)) { onOpenWeb(WebRoutes.TASKS) }
            }
            ui.tasks.forEachIndexed { i, task ->
                if (i == 0 || ui.tasks[i - 1].day != task.day) DayHeader(taskDayName(task.day), emphasized = true)
                TaskRow(task, i + 1, onComplete) { onOpenTask(task) }
            }
        }
    }
}

@Composable
private fun taskDayName(day: DayKind): String = when (day) {
    DayKind.TODAY -> stringResource(R.string.today_day_today)
    DayKind.TOMORROW -> stringResource(R.string.today_day_tomorrow)
    DayKind.OTHER -> stringResource(R.string.today_day_later)
}

/** Numbered hex, task tier color bar, checkbox, title with provider tag, then due date. Days after today are faded. */
@Composable
private fun TaskRow(task: TaskUi, number: Int, onComplete: (String) -> Unit, onOpen: () -> Unit) {
    val t = LocalTok.current
    val upcoming = task.upcoming
    val muted = if (upcoming) t.faint else t.dim
    // The web's color tier (red, orange, blue, neutral) for the bar and checkbox; upcoming tasks dimmed
    val priority = if (upcoming) null else t.priority(task.colorTier)
    val barColor = priority ?: (if (t.predator) t.pd.edge2 else t.line)
    val dueColor = if (task.overdue && !upcoming) t.destructive else muted
    val titleColor = if (upcoming) t.faint else if (task.title == null) t.dim else t.text
    val tagLabel = task.tagLabel
    val tagColor = LifeMappColors.parseHex(task.tagColor) ?: Color(0xFF10B981)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberBadge(number, faded = upcoming)
        Spacer(Modifier.width(6.dp))
        Row(Modifier.weight(1f).framed(t, t.row).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Bar(barColor, 30.dp)
            // Completion checkbox (LIFE MAPP tasks only), or a gap of the same width
            if (task.completable) {
                Box(Modifier.size(40.dp).clickable { onComplete(task.id) }, contentAlignment = Alignment.Center) {
                    Ic(
                        if (t.predator) PdDrawables.CHECK_SQUARE else LucideIcons.CHECK_OFF, 20.dp,
                        priority ?: muted, stringResource(R.string.today_complete_task),
                    )
                }
            } else {
                Spacer(Modifier.width(40.dp))
            }
            Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title ?: stringResource(R.string.today_locked),
                        style = t.body(14.sp, titleColor, FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tagLabel != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.s(tagLabel),
                            style = t.label(9.sp, if (upcoming) tagColor.copy(alpha = 0.55f) else tagColor),
                            maxLines = 1,
                            modifier = Modifier.background(t.tinted(tagColor, if (upcoming) 0.06f else 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                if (task.dueLabel != null || task.recurring) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        task.dueLabel?.let {
                            Ic(LucideIcons.CALENDAR, 11.dp, dueColor)
                            Spacer(Modifier.width(4.dp))
                            Text(it, style = t.caption(10.sp, dueColor), maxLines = 1)
                            if (task.overdue && !upcoming) {
                                Spacer(Modifier.width(4.dp))
                                Ic(LucideIcons.WARNING, 11.dp, dueColor)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (task.recurring) Ic(LucideIcons.REPEAT, 11.dp, muted)
                    }
                }
            }
        }
    }
}
