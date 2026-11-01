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
    DayHeader(EventDayUi(LocalDate.MIN, day, LocalContext.current.getString(R.string.tomorrow_plus_one), emptyList()))
}

/**
 * A task as on the web's tasks card: numbered hex, square checkbox, list color bar, title, then the due date
 * with a warning when overdue. Tasks after today are faded, as the web dims them (and as upcoming events are).
 */
@Composable
internal fun TaskRow(task: TaskUi, number: Int) {
    val context = LocalContext.current
    val pd = LocalPd.current
    val openTasks = openRoute(context, WebRoutes.TASKS)
    val colors = LocalWidgetColors.current
    val upcoming = task.upcoming
    val rawList = LifeMappColors.parseHex(task.listColor)
    val fade: (Color) -> ColorProvider = { pd?.tint(it, 0.45f) ?: colors.tint(it, 0.45f) }
    val priority = if (upcoming) LifeMappColors.priority(task.priority, dark = false)?.let(fade) else colors.priority(task.priority)
    val listColor = if (upcoming) rawList?.let(fade) else rawList?.let(colors::graphic)

    if (pd != null) {
        val muted = if (upcoming) pd.faint else pd.dim
        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            PdHex(18.dp, edge = if (upcoming) pd.edge2 else pd.accent, fill = pd.panel) {
                Text("$number", style = numberStyle(9.sp, color = if (upcoming) pd.faint else pd.ink))
            }
            Spacer(GlanceModifier.width(3.dp))
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.row, fill = pd.row, modifier = GlanceModifier.defaultWeight()) { frame ->
                Row(modifier = frame.fillMaxWidth().padding(start = 0.dp, end = 6.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(task, PdDrawables.CHECK_SQUARE, size = 18.dp, tint = priority ?: muted)
                    PdBar(listColor ?: pd.edge2, height = 26.dp)
                    Column(modifier = GlanceModifier.defaultWeight().clickable(openTasks)) {
                        Text(
                            task.title ?: context.getString(R.string.locked),
                            style = textStyle(
                                size = 13.sp, weight = FontWeight.Bold,
                                color = if (upcoming) pd.faint else if (task.title == null) pd.dim else pd.text,
                            ),
                            maxLines = 1,
                        )
                        DueLine(task, color = if (task.overdue) pd.destructive else muted, dim = muted)
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
        Checkbox(task, LucideIcons.CHECK_OFF, size = 20.dp, tint = priority ?: listColor ?: faded)
        Column(modifier = GlanceModifier.defaultWeight().clickable(openTasks)) {
            Text(
                task.title ?: context.getString(R.string.locked),
                style = textStyle(size = 13.sp, weight = FontWeight.Medium, color = if (task.title == null || upcoming) faded else GlanceTheme.colors.onBackground),
                maxLines = 1,
            )
            DueLine(task, color = if (task.overdue) GlanceTheme.colors.error else faded, dim = faded)
        }
    }
}

/** The completion checkbox (LIFE MAPP tasks only), or a gap of the same width. */
@Composable
private fun Checkbox(task: TaskUi, icon: Int, size: androidx.compose.ui.unit.Dp, tint: ColorProvider) {
    if (task.completable) {
        Box(
            modifier = GlanceModifier.size(30.dp).clickable(
                actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.TaskIdKey to task.id))
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, LocalContext.current.getString(R.string.complete_task), size = size, tint = tint)
        }
    } else {
        Spacer(GlanceModifier.width(30.dp))
    }
}

/** Calendar icon, due date and time, a warning when overdue, and the repeat icon. */
@Composable
private fun DueLine(task: TaskUi, color: ColorProvider, dim: ColorProvider) {
    val due = task.dueLabel
    if (due == null && !task.recurring) return
    val pd = LocalPd.current
    Row(verticalAlignment = Alignment.CenterVertically) {
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
