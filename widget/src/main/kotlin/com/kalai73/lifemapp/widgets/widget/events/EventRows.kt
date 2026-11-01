package com.kalai73.lifemapp.widgets.widget.events

// Event rows as on the web's events card (components/calendar/calendar-events-card.tsx).

import com.kalai73.lifemapp.widgets.widget.common.PdWrapFrame
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.kalai73.lifemapp.widgets.core.data.presentation.DayKind
import com.kalai73.lifemapp.widgets.core.data.presentation.EventDayUi
import com.kalai73.lifemapp.widgets.core.data.presentation.EventUi
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetColors
import com.kalai73.lifemapp.widgets.widget.common.PdBar
import com.kalai73.lifemapp.widgets.widget.common.PdFrame
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.height
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.Dot
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.openExternal
import com.kalai73.lifemapp.widgets.widget.common.openRoute
import com.kalai73.lifemapp.widgets.widget.common.textStyle
import androidx.glance.action.Action
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Column
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.PdHex
import com.kalai73.lifemapp.widgets.widget.common.ConnectCard
import com.kalai73.lifemapp.widgets.widget.common.numberStyle

/** Per-widget events settings, kept in the widget's Glance state (written by WidgetConfigActivity in the app). */
object EventsConfig {
    val DaysAhead = intPreferencesKey("events.daysAhead")
    val HiddenCalendars = stringSetPreferencesKey("events.hiddenCalendars")
    val DAY_OPTIONS = listOf(1, 3, 7)
    /** Short, so tasks and shortcuts below the events stay within a short scroll. */
    const val DEFAULT_DAYS = 3

    /** The days and calendars this widget instance shows. */
    fun filter(days: List<EventDayUi>, today: java.time.LocalDate, daysAhead: Int, hidden: Set<String>): List<EventDayUi> {
        val last = today.plusDays((daysAhead - 1).toLong())
        val kept = days.filter { !it.date.isAfter(last) }
            .map { day -> day.copy(events = day.events.filter { it.calendarName == null || it.calendarName !in hidden }) }
            .filter { it.events.isNotEmpty() }
        // Today is always listed, with "No events today" when it's empty
        return if (kept.firstOrNull()?.kind == DayKind.TODAY) kept
        else listOf(EventDayUi(today, DayKind.TODAY, days.firstOrNull { it.kind == DayKind.TODAY }?.label ?: "", emptyList())) + kept
    }
}

/** Centered day divider: a line, "TODAY" / "TOMORROW" / the date, a line. */
@Composable
internal fun DayHeader(day: EventDayUi, emphasized: Boolean = false) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val name = when (day.kind) {
        DayKind.TODAY -> context.getString(R.string.today)
        DayKind.TOMORROW -> context.getString(R.string.tomorrow)
        DayKind.OTHER -> day.label
    }
    val line = pd?.edge ?: GlanceTheme.colors.outline
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.defaultWeight().height(1.dp).background(line)) {}
        Text(
            style.label(name),
            style = if (pd != null) labelStyle(size = 10.sp, color = if (emphasized) pd.ink else pd.dim) else textStyle(size = 11.sp, weight = FontWeight.Bold, color = if (emphasized) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
            modifier = GlanceModifier.padding(horizontal = 10.dp),
        )
        Box(GlanceModifier.defaultWeight().height(1.dp).background(line)) {}
    }
}

/**
 * An event as on the web's events card: numbered hex, calendar color bar, title with the calendar tag, then
 * clock, time and date. Days after today are faded.
 */
@Composable
internal fun EventRow(event: EventUi, number: Int, day: EventDayUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val action = event.link?.let(::openExternal) ?: openRoute(context, WebRoutes.EVENTS)
    val color = LifeMappColors.parseHex(event.color) ?: androidx.compose.ui.graphics.Color(0xFF3B82F6)
    val upcoming = day.kind != DayKind.TODAY
    val dimmed = upcoming || event.secondary
    val time = listOfNotNull(event.timeLabel ?: context.getString(R.string.all_day), day.label.ifEmpty { null }).joinToString(" · ")
    val calendar = event.calendarName?.takeIf { it.isNotBlank() }
    val colors = LocalWidgetColors.current

    if (pd != null) {
        val titleColor = if (upcoming) pd.faint else if (event.secondary) pd.dim else pd.text
        val captionColor = if (upcoming) pd.faint else pd.dim
        // Faded rows keep a faded color; current ones get the calendar color adjusted to read on the row
        val barColor = if (dimmed) pd.tint(color, 0.45f) else colors.graphic(color)
        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            PdHex(18.dp, edge = if (upcoming) pd.edge2 else pd.accent, fill = pd.panel) {
                Text("$number", style = numberStyle(9.sp, color = if (upcoming) pd.faint else pd.ink))
            }
            Spacer(GlanceModifier.width(5.dp))
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.row, fill = pd.row, modifier = GlanceModifier.defaultWeight().clickable(action)) { frame ->
                Row(modifier = frame.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PdBar(barColor, height = 26.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                event.title,
                                style = textStyle(size = 13.sp, weight = FontWeight.Bold, color = titleColor),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            if (calendar != null) RowTag(calendar, color, dimmed)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(LucideIcons.CLOCK, null, size = 10.dp, tint = captionColor)
                            Spacer(GlanceModifier.width(3.dp))
                            Text(time, style = captionStyle(size = 9.sp, color = captionColor), maxLines = 1)
                        }
                    }
                }
            }
        }
        return
    }

    // Classic: the same structure, flat
    val faded = GlanceTheme.colors.onSurfaceVariant
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(action).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$number",
            style = textStyle(size = 11.sp, weight = FontWeight.Bold, color = if (upcoming) faded else GlanceTheme.colors.primary).copy(textAlign = TextAlign.Center),
            modifier = GlanceModifier.width(16.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Dot(if (dimmed) color.copy(alpha = 0.45f) else color)
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.title,
                    style = textStyle(size = 13.sp, weight = FontWeight.Medium, color = if (dimmed) faded else GlanceTheme.colors.onBackground),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (calendar != null) RowTag(calendar, color, dimmed)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LucideIcons.CLOCK, null, size = 10.dp, tint = faded)
                Spacer(GlanceModifier.width(3.dp))
                Text(time, style = textStyle(size = 10.sp, color = faded), maxLines = 1)
            }
        }
    }
}

/**
 * The tag after a row title, shared by event rows (calendar) and task rows (list): the name in its color over a
 * light tint of it, faded when [dimmed]. Predator: uppercase label, square; Classic: rounded.
 */
@Composable
internal fun RowTag(name: String, color: androidx.compose.ui.graphics.Color, dimmed: Boolean) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val colors = LocalWidgetColors.current
    Spacer(GlanceModifier.width(4.dp))
    if (pd != null) {
        Text(
            style.label(name),
            style = labelStyle(size = 8.sp, color = if (dimmed) pd.tint(color, 0.45f) else colors.text(color, over = 0.14f)),
            maxLines = 1,
            modifier = GlanceModifier.background(pd.tint(color, if (dimmed) 0.06f else 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp),
        )
    } else {
        Text(
            name,
            style = textStyle(size = 9.sp, weight = FontWeight.Bold, color = if (dimmed) ColorProvider(color.copy(alpha = 0.5f)) else colors.text(color, over = 0.14f)),
            maxLines = 1,
            modifier = GlanceModifier.background(colors.tint(color, 0.14f)).cornerRadius(6.dp).padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** No calendar connected: icon, message and the "Add calendar" button (opens the web settings). */
@Composable
internal fun NoCalendarCard(onAdd: Action) {
    val context = LocalContext.current
    ConnectCard(LucideIcons.CALENDAR, context.getString(R.string.no_calendar), context.getString(R.string.add_calendar), onAdd)
}
