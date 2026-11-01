package com.kalai73.lifemapp.widgets.widget.tasks

// The tasks card as on the web's Today page (components/domains/integrity-widgets.tsx, TaskListWidget), simplified:
// "Add task" / "Task lists" buttons, then numbered task rows. No list counters or footer links.

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.kalai73.lifemapp.widgets.core.data.presentation.DayKind
import com.kalai73.lifemapp.widgets.core.data.presentation.EventDayUi
import com.kalai73.lifemapp.widgets.core.data.presentation.TaskUi
import com.kalai73.lifemapp.widgets.widget.events.DayHeader
import com.kalai73.lifemapp.widgets.widget.events.RowTag
import java.time.LocalDate
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdDrawables
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.voice.VoiceInputActivity
import androidx.glance.appwidget.action.actionStartActivity
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.CompleteTaskAction
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetColors
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.PdBar
import com.kalai73.lifemapp.widgets.widget.common.PdHex
import com.kalai73.lifemapp.widgets.widget.common.PdWrapFrame
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.color
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.widget.common.numberStyle
import com.kalai73.lifemapp.widgets.widget.common.openRoute
import com.kalai73.lifemapp.widgets.widget.common.textStyle

/** "Add task" and "Task lists", sharing the row equally. Predator: parallelograms in the accent tint. */
@Composable
internal fun TaskActions() {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionButton(LucideIcons.ADD, context.getString(R.string.add_task), actionStartActivity(VoiceInputActivity.intent(context, typed = true)), GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(6.dp))
        ActionButton(LucideIcons.TASKS, context.getString(R.string.task_lists), openRoute(context, WebRoutes.TASKS), GlanceModifier.defaultWeight())
    }
}

@Composable
private fun ActionButton(icon: Int, label: String, onClick: Action, modifier: GlanceModifier) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    if (pd != null) {
        PdWrapFrame(PdShape.PARA, edge = pd.accent, fill = pd.accentTint, modifier = modifier.clickable(onClick)) { frame ->
            Row(
                modifier = frame.fillMaxWidth().padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, size = 12.dp, tint = pd.ink)
                Spacer(GlanceModifier.width(5.dp))
                Text(style.label(label), style = labelStyle(size = 11.sp, color = pd.ink), maxLines = 1)
            }
        }
    } else {
        Row(
            modifier = modifier.background(GlanceTheme.colors.secondaryContainer).cornerRadius(style.innerRadius)
                .clickable(onClick).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, size = 14.dp)
            Spacer(GlanceModifier.width(5.dp))
            Text(label, style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary), maxLines = 1)
        }
    }
}

/** The tasks' segment divider, labeled as on the web's Task List Widget: TODAY, TOMORROW, TOMORROW ++ 1. */
@Composable
internal fun TaskDayHeader(day: DayKind) {
    // DayHeader names TODAY and TOMORROW itself and prints the label for any other day
    DayHeader(EventDayUi(LocalDate.MIN, day, LocalContext.current.getString(R.string.tomorrow_plus_one), emptyList()), emphasized = true)
}

/**
 * A task as on the web's tasks card: numbered hex, square checkbox, list color bar, title, then the due date
 * with a warning when overdue, and the provider tag. Styled as [com.kalai73.lifemapp.widgets.widget.events.EventRow]
 * (spacing, tag, fading): tasks after today are faded, as the web dims them.
 */
@Composable
internal fun TaskRow(task: TaskUi, number: Int) {
    val context = LocalContext.current
    val pd = LocalPd.current
    val openTasks = openRoute(context, WebRoutes.TASKS)
    val colors = LocalWidgetColors.current
    val upcoming = task.upcoming
    // As on the web: the checkbox and bar in the task's color tier (red, orange, blue, or neutral), and later
    // tasks greyed out entirely (the web's opacity-50 grayscale), so no tier color on them
    val tier = if (upcoming) null else colors.priority(task.colorTier)
    val tagLabel = task.tagLabel
    val tagColor = LifeMappColors.parseHex(task.tagColor) ?: Color(0xFF10B981)

    if (pd != null) {
        val muted = if (upcoming) pd.faint else pd.dim
        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            PdHex(18.dp, edge = if (upcoming) pd.edge2 else pd.accent, fill = pd.panel) {
                Text("$number", style = numberStyle(9.sp, color = if (upcoming) pd.faint else pd.ink))
            }
            Spacer(GlanceModifier.width(5.dp))
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.row, fill = pd.row, modifier = GlanceModifier.defaultWeight()) { frame ->
                // The 30dp checkbox target centers an 18dp box, so its left edge sits 6dp in, as an event's bar does
                Row(modifier = frame.fillMaxWidth().padding(start = 0.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(task, PdDrawables.CHECK_SQUARE, size = 18.dp, tint = tier ?: muted)
                    PdBar(tier ?: pd.edge2, height = 26.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    Column(modifier = GlanceModifier.defaultWeight().clickable(openTasks)) {
                        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                task.title ?: context.getString(R.string.locked),
                                style = textStyle(
                                    size = 13.sp, weight = FontWeight.Bold,
                                    color = if (upcoming) pd.faint else if (task.title == null) pd.dim else pd.text,
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            if (tagLabel != null) RowTag(tagLabel, tagColor, upcoming)
                        }
                        DueLine(task, color = if (task.overdue && !upcoming) pd.destructive else muted, dim = muted)
                    }
                }
            }
        }
        return
    }

    // Classic: the same structure, flat
    val faded = GlanceTheme.colors.onSurfaceVariant
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$number",
            style = textStyle(size = 11.sp, weight = FontWeight.Bold, color = if (upcoming) faded else GlanceTheme.colors.primary).copy(textAlign = TextAlign.Center),
            modifier = GlanceModifier.width(16.dp),
        )
        Checkbox(task, LucideIcons.CHECK_OFF, size = 20.dp, tint = tier ?: faded)
        Column(modifier = GlanceModifier.defaultWeight().clickable(openTasks)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title ?: context.getString(R.string.locked),
                    style = textStyle(size = 13.sp, weight = FontWeight.Medium, color = if (task.title == null || upcoming) faded else GlanceTheme.colors.onBackground),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (tagLabel != null) RowTag(tagLabel, tagColor, upcoming)
            }
            DueLine(task, color = if (task.overdue && !upcoming) GlanceTheme.colors.error else faded, dim = faded)
        }
    }
}

/** The completion checkbox, shown for every task as on the web; it completes only tasks the widget can complete. */
@Composable
private fun Checkbox(task: TaskUi, icon: Int, size: androidx.compose.ui.unit.Dp, tint: ColorProvider) {
    val base = GlanceModifier.size(30.dp)
    Box(
        modifier = if (task.completable) {
            base.clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.TaskIdKey to task.id)))
        } else base,
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (task.completable) LocalContext.current.getString(R.string.complete_task) else null, size = size, tint = tint)
    }
}

/** Calendar icon, due date and time, a warning when overdue, and the repeat icon. */
@Composable
private fun DueLine(task: TaskUi, color: ColorProvider, dim: ColorProvider) {
    val due = task.dueLabel
    val pd = LocalPd.current
    // Predator spells out a missing date, as the web does; Classic keeps the row compact
    if (due == null && !task.recurring && pd == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (due == null && pd != null) {
            Icon(LucideIcons.CALENDAR, null, size = 10.dp, tint = dim)
            Spacer(GlanceModifier.width(3.dp))
            Text(LocalContext.current.getString(R.string.no_due_date), style = captionStyle(size = 9.sp, color = dim), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
        }
        if (due != null) {
            Icon(LucideIcons.CALENDAR, null, size = 10.dp, tint = color)
            Spacer(GlanceModifier.width(3.dp))
            Text(due, style = if (pd != null) captionStyle(size = 9.sp, color = color) else textStyle(size = 10.sp, color = color), maxLines = 1)
            if (task.overdue) {
                Spacer(GlanceModifier.width(3.dp))
                Icon(LucideIcons.WARNING, null, size = 10.dp, tint = color)
            }
            Spacer(GlanceModifier.width(6.dp))
        }
        if (task.recurring) Icon(LucideIcons.REPEAT, null, size = 10.dp, tint = dim)
    }
}
