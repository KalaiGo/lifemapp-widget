package com.kalai73.lifemapp.widgets.widget.tasks

// Task rows as on the web's tasks panel (components/tasks/tasks-panel.tsx).

import com.kalai73.lifemapp.widgets.widget.common.PdWrapFrame
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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
import com.kalai73.lifemapp.widgets.core.data.presentation.TaskUi
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdDrawables
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.PdBar
import com.kalai73.lifemapp.widgets.widget.common.PdFrame
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.CompleteTaskAction
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.color
import com.kalai73.lifemapp.widgets.widget.common.openRoute
import com.kalai73.lifemapp.widgets.widget.common.textStyle

@Composable
internal fun TaskRow(task: TaskUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    if (style.isPredator) {
        PredatorTaskRow(task)
        return
    }
    val openTasks = openRoute(context, WebRoutes.TASKS)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        val priority = LifeMappColors.priority(task.priority)
        if (task.completable) {
            Box(
                modifier = GlanceModifier.size(30.dp).clickable(
                    actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.TaskIdKey to task.id))
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    LucideIcons.CHECK_OFF,
                    context.getString(R.string.complete_task),
                    size = 20.dp,
                    tint = priority?.let(::color) ?: GlanceTheme.colors.onSurfaceVariant,
                )
            }
        } else {
            Spacer(GlanceModifier.width(30.dp))
        }
        Spacer(GlanceModifier.width(4.dp))
        Column(modifier = GlanceModifier.defaultWeight().clickable(openTasks)) {
            Text(
                task.title ?: context.getString(R.string.locked),
                style = textStyle(
                    size = 13.sp,
                    weight = FontWeight.Medium,
                    color = if (task.title == null) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onBackground,
                ),
                maxLines = 2,
            )
            if (task.dueLabel != null || task.listName != null || task.recurring) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    task.dueLabel?.let {
                        Text(it, style = textStyle(size = 10.sp, color = if (task.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    if (task.recurring) {
                        Icon(LucideIcons.REPEAT, null, size = 10.dp, tint = GlanceTheme.colors.onSurfaceVariant)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    task.listName?.let { name ->
                        val pill = LifeMappColors.parseHex(task.listColor)
                        Box(
                            modifier = GlanceModifier
                                .background(pill?.copy(alpha = 0.18f)?.let(::color) ?: GlanceTheme.colors.surfaceVariant)
                                .cornerRadius(style.innerRadius)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(style.label(name), style = textStyle(size = 9.sp, color = pill?.let(::color) ?: GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Task row (styleguide §7): chamfer-sm at raised 40%, a list color bar leaning −12°, tags as parallelograms. */
@Composable
private fun PredatorTaskRow(task: TaskUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current ?: return
    val openTasks = openRoute(context, WebRoutes.TASKS)
    val priority = LifeMappColors.priority(task.priority)
    val listColor = LifeMappColors.parseHex(task.listColor)
    // The outer Box keeps the gap below the row outside the frame
    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge, fill = pd.row, modifier = GlanceModifier.fillMaxWidth()) { frame ->
            Row(
                modifier = frame.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PdBar(listColor?.let(::color) ?: pd.accent, height = 18.dp)
                if (task.completable) {
                    Box(
                        modifier = GlanceModifier.size(30.dp).clickable(
                            actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.TaskIdKey to task.id))
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            PdDrawables.CHECK_SQUARE,
                            context.getString(R.string.complete_task),
                            size = 18.dp,
                            tint = priority?.let(::color) ?: pd.dim,
                        )
                    }
                } else {
                    Spacer(GlanceModifier.width(30.dp))
                }
                Spacer(GlanceModifier.width(2.dp))
                Column(modifier = GlanceModifier.defaultWeight().clickable(openTasks)) {
                    Text(
                        task.title ?: context.getString(R.string.locked),
                        style = textStyle(size = 13.sp, weight = FontWeight.Medium, color = if (task.title == null) pd.dim else pd.text),
                        maxLines = 2,
                    )
                    if (task.dueLabel != null || task.listName != null || task.recurring) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            task.dueLabel?.let {
                                Text(style.label(it), style = captionStyle(size = 9.sp, color = if (task.overdue) pd.destructive else pd.dim), maxLines = 1)
                                Spacer(GlanceModifier.width(6.dp))
                            }
                            if (task.recurring) {
                                Icon(LucideIcons.REPEAT, null, size = 10.dp, tint = pd.dim)
                                Spacer(GlanceModifier.width(6.dp))
                            }
                            task.listName?.let { name ->
                                // List names are user content: display italic, no uppercase (styleguide §4)
                                PdWrapFrame(
                                    PdShape.PARA,
                                    edge = listColor?.let { pd.tint(it, 0.55f) } ?: pd.edge,
                                    fill = listColor?.let { pd.tint(it, 0.16f) } ?: pd.raised,
                                ) { frame ->
                                    Text(
                                        name, style = labelStyle(size = 9.sp, color = listColor?.let(::color) ?: pd.dim, weight = FontWeight.Medium), maxLines = 1,
                                        modifier = frame.padding(horizontal = 9.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
