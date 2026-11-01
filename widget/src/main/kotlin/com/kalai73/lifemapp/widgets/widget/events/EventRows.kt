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

@Composable
internal fun DayHeader(day: EventDayUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val name = when (day.kind) {
        DayKind.TODAY -> context.getString(R.string.today)
        DayKind.TOMORROW -> context.getString(R.string.tomorrow)
        DayKind.OTHER -> null
    }
    val pd = LocalPd.current
    if (pd != null) {
        // Day chip (parallelogram, accent tint, pd-label ink), mono date caption, then an edge line
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (name != null) {
                PdWrapFrame(PdShape.PARA, edge = pd.accent, fill = pd.accentTint) { frame ->
                    Text(
                        style.label(name), style = labelStyle(size = 10.sp, color = pd.ink), maxLines = 1,
                        modifier = frame.padding(horizontal = 10.dp, vertical = 1.dp),
                    )
                }
                Spacer(GlanceModifier.width(6.dp))
            }
            if (day.label.isNotEmpty()) {
                Text(style.label(day.label), style = captionStyle(size = 9.sp), maxLines = 1)
                Spacer(GlanceModifier.width(6.dp))
            }
            Box(GlanceModifier.defaultWeight().height(1.dp).background(pd.edge)) {}
        }
        return
    }
    Text(
        style.label(listOfNotNull(name, day.label.ifEmpty { null }).joinToString(" · ")),
        style = textStyle(size = 11.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary),
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
internal fun EventRow(event: EventUi) {
    val context = LocalContext.current
    val action = event.link?.let(::openExternal) ?: openRoute(context, WebRoutes.EVENTS)
    val color = LifeMappColors.parseHex(event.color) ?: androidx.compose.ui.graphics.Color(0xFF3B82F6)
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(action).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pd = LocalPd.current
        if (pd != null) {
            PdBar(if (event.secondary) pd.tint(color, 0.5f) else androidx.glance.unit.ColorProvider(color))
            Spacer(GlanceModifier.width(6.dp))
        } else {
            Dot(if (event.secondary) color.copy(alpha = 0.5f) else color)
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            event.timeLabel ?: context.getString(R.string.all_day),
            style = if (pd != null) captionStyle(size = 10.sp) else textStyle(size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
            modifier = GlanceModifier.width(78.dp),
        )
        Text(
            event.title,
            style = textStyle(
                size = 13.sp,
                weight = if (event.secondary) FontWeight.Normal else FontWeight.Medium,
                color = if (event.secondary) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onBackground,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}
