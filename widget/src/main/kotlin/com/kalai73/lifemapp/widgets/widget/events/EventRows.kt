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
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.Dot
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.openEvent
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
import android.appwidget.AppWidgetManager
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.text.TextStyle
import androidx.glance.text.TextDecoration

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
 * clock, time and date. Days after today are faded; today's past events are faded with the title struck through
 * (the web's isTodayPastEvent styling).
 */
@Composable
internal fun EventRow(event: EventUi, number: Int, day: EventDayUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    // The event's details in the app's event modal (its provider page is one tap further, from the modal)
    val action = openEvent(context, event.id, event.start)
    val color = LifeMappColors.parseHex(event.color) ?: androidx.compose.ui.graphics.Color(0xFF3B82F6)
    val upcoming = day.kind != DayKind.TODAY
    val past = event.past && !upcoming
    val faded = upcoming || past
    val dimmed = faded || event.secondary
    val strike: (TextStyle) -> TextStyle = { if (past) it.copy(textDecoration = TextDecoration.LineThrough) else it }
    val time = listOfNotNull(event.timeLabel ?: context.getString(R.string.all_day), day.label.ifEmpty { null }).joinToString(" · ")
    val calendar = event.calendarName?.takeIf { it.isNotBlank() }
    val colors = LocalWidgetColors.current

    if (pd != null) {
        val titleColor = if (faded) pd.faint else if (event.secondary) pd.dim else pd.text
        val captionColor = if (faded) pd.faint else pd.dim
        // Faded rows keep a faded color; current ones get the calendar color adjusted to read on the row
        val barColor = if (dimmed) pd.tint(color, 0.45f) else colors.graphic(color)
        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            PdHex(18.dp, edge = if (faded) pd.edge2 else pd.accent, fill = pd.panel) {
                Text("$number", style = numberStyle(9.sp, color = if (faded) pd.faint else pd.ink))
            }
            // Secondary calendars are indented from the left, as on the web's events card
            Spacer(GlanceModifier.width(if (event.secondary) 17.dp else 5.dp))
            // ...and more compact, without the card frame, so primary events stand out
            val vertical = if (event.secondary) 2.dp else 4.dp
            val body: @Composable (GlanceModifier) -> Unit = { box ->
                Row(modifier = box.padding(start = 6.dp, end = 6.dp, top = vertical, bottom = vertical), verticalAlignment = Alignment.CenterVertically) {
                    PdBar(barColor, height = 26.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        // Hex, indent, frame padding, bar and its spacer
                        TitleWithTag(
                            event.title, strike(textStyle(size = 13.sp, weight = FontWeight.Bold, color = titleColor)),
                            calendar, color, dimmed, overhead = if (event.secondary) 58.dp else 46.dp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(LucideIcons.CLOCK, null, size = 10.dp, tint = captionColor)
                            Spacer(GlanceModifier.width(3.dp))
                            Text(time, style = captionStyle(size = 9.sp, color = captionColor), maxLines = 1)
                        }
                    }
                }
            }
            if (event.secondary) {
                body(GlanceModifier.defaultWeight().clickable(action))
            } else {
                PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.row, fill = pd.row, modifier = GlanceModifier.defaultWeight().clickable(action)) { frame ->
                    body(frame.fillMaxWidth())
                }
            }
        }
        return
    }

    // Classic: the same structure, flat
    val muted = GlanceTheme.colors.onSurfaceVariant
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(action).padding(vertical = if (event.secondary) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$number",
            style = textStyle(size = 11.sp, weight = FontWeight.Bold, color = if (faded) muted else GlanceTheme.colors.primary).copy(textAlign = TextAlign.Center),
            modifier = GlanceModifier.width(16.dp),
        )
        Spacer(GlanceModifier.width(if (event.secondary) 16.dp else 4.dp))
        Dot(if (dimmed) color.copy(alpha = 0.45f) else color)
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            // Number, indent, dot and its spacer
            TitleWithTag(
                event.title, strike(textStyle(size = 13.sp, weight = FontWeight.Medium, color = if (dimmed) muted else GlanceTheme.colors.onBackground)),
                calendar, color, dimmed, overhead = if (event.secondary) 48.dp else 36.dp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LucideIcons.CLOCK, null, size = 10.dp, tint = muted)
                Spacer(GlanceModifier.width(3.dp))
                Text(time, style = textStyle(size = 10.sp, color = muted), maxLines = 1)
            }
        }
    }
}

/**
 * A row's title with its tag (calendar or task manager) right after it, as on the app's Today screen. RemoteViews
 * have no "wrap but shrink" weight: a weighted title pushes the tag to the row's end, an unweighted long one
 * squeezes the tag out. So the title is measured: when title and tag fit the row, the title wraps and the tag
 * follows it; otherwise the title takes the row (ellipsized) and the tag ends it, which is then right after it.
 * [overhead] is the row's width taken by everything but the title line (number, checkbox, bar, paddings).
 */
@Composable
internal fun TitleWithTag(
    title: String,
    titleStyle: TextStyle,
    tag: String?,
    tagColor: androidx.compose.ui.graphics.Color,
    dimmed: Boolean,
    overhead: Dp,
) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val fits = tag != null && run {
        val available = widgetWidth() - ROOT_SIDES - overhead
        val tagText = if (LocalPd.current != null) style.label(tag) else tag
        // Bold system font for both: at least as wide as the condensed label font, so the estimate errs safe
        val needed = textWidth(context, title, titleStyle.fontSize?.value ?: 13f) +
            textWidth(context, tagText, 9f) + TAG_EXTRA
        needed <= available
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = titleStyle, maxLines = 1, modifier = if (fits) GlanceModifier else GlanceModifier.defaultWeight())
        if (tag != null) RowTag(tag, tagColor, dimmed)
        if (fits) Spacer(GlanceModifier.defaultWeight())
    }
}

/** The root's padding on both sides (LifeMappWidgetTheme) and the list's scrollbar gutter. */
private val ROOT_SIDES = 32.dp
/** The tag's spacer and horizontal padding, plus slack for font differences. */
private val TAG_EXTRA = 24.dp

/**
 * The widget's real width: with SizeMode.Responsive LocalSize is the matched bucket, not the widget. The portrait
 * width (the options' min width) is the narrower one; LocalSize when the options aren't available.
 */
@Composable
private fun widgetWidth(): Dp {
    val context = LocalContext.current
    val glanceId = LocalGlanceId.current
    val fallback = LocalSize.current.width
    return runCatching {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val min = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        if (min > 0) min.dp else fallback
    }.getOrDefault(fallback)
}

/** The width of [text] in bold at [sp], in dp. */
private fun textWidth(context: android.content.Context, text: String, sp: Float): Dp {
    val metrics = context.resources.displayMetrics
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
    }
    return (paint.measureText(text) / metrics.density).dp
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

/** No calendar connected: icon, message and the "Add calendar" button (opens the app at the events). */
@Composable
internal fun NoCalendarCard(onAdd: Action) {
    val context = LocalContext.current
    ConnectCard(LucideIcons.CALENDAR, context.getString(R.string.no_calendar), context.getString(R.string.add_calendar), onAdd)
}
