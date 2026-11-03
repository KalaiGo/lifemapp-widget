package com.kalai73.lifemapp.widgets.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.presentation.MonthlyUi
import com.kalai73.lifemapp.widgets.core.presentation.WeatherUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes

// ── Top bar and hero ────────────────────────────────────────────────────────

/**
 * "HELLO," (left-aligned) over the name, in a frame as wide as its text, centered: the same chamfered card as the
 * date and counters below, as on the widget's stacked hero (StackedHero.GreetingBlock).
 */
@Composable
internal fun GreetingBlock(username: String?) {
    val t = LocalTok.current
    val name = username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.today_welcome)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        Column(
            Modifier.framed(t, t.card, cut = 8.dp).padding(horizontal = 22.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(t.s(stringResource(R.string.today_hello)), style = t.heroCaption(10.sp))
            // Display face as on the widget: condensed bold italic (Predator), bold (Classic), in the accent ink
            Text(
                name,
                style = (if (t.predator) t.label(28.sp) else t.body(26.sp, t.ink, FontWeight.Bold)).copy(textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun GreetingBlock(ui: WidgetUi) = GreetingBlock(ui.username)

@Composable
internal fun GreetingBlock(ui: MonthlyUi) = GreetingBlock(ui.username)

/** "TODAY IS," over the date, in a chamfered frame. */
@Composable
internal fun DateBlock(todayLabel: String) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Column(
            Modifier.fillMaxWidth(0.72f).framed(t, t.card, cut = 8.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t.s(stringResource(R.string.today_is_caption)), style = t.heroCaption(10.sp),
                modifier = Modifier.align(Alignment.Start),
            )
            Text(
                todayLabel,
                style = if (t.predator) t.label(22.sp) else t.body(20.sp, t.ink, FontWeight.Bold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DateBlock(ui: WidgetUi) = DateBlock(ui.todayLabel)

@Composable
internal fun DateBlock(ui: MonthlyUi) = DateBlock(ui.todayLabel)

@Composable
internal fun Counters(
    eventsCount: Int,
    tasksCount: Int,
    mailCount: Int,
    mailConnected: Boolean,
    onEvents: () -> Unit,
    onTasks: () -> Unit,
    onMail: () -> Unit,
) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().framed(t, t.card, cut = 8.dp).padding(vertical = 6.dp)) {
        Text(t.s(stringResource(R.string.today_and_you_have)), style = t.heroCaption(10.sp), modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Counter(Modifier.weight(1f), LucideIcons.CALENDAR, eventsCount.toString(), stringResource(R.string.today_events), onEvents)
            if (t.predator) Box(Modifier.width(1.dp).height(28.dp).background(t.line))
            Counter(Modifier.weight(1f), LucideIcons.TASKS, tasksCount.toString(), stringResource(R.string.today_tasks), onTasks)
            if (t.predator) Box(Modifier.width(1.dp).height(28.dp).background(t.line))
            Counter(Modifier.weight(1f), LucideIcons.MAIL, if (mailConnected) mailCount.toString() else "–", stringResource(R.string.today_mail), onMail)
        }
    }
}

@Composable
internal fun Counters(ui: WidgetUi, onEvents: () -> Unit, onTasks: () -> Unit, onMail: () -> Unit) =
    Counters(ui.eventsCount, ui.tasksCount, ui.mailCount, ui.mailConnected, onEvents, onTasks, onMail)

@Composable
internal fun Counters(ui: MonthlyUi, onEvents: () -> Unit, onTasks: () -> Unit, onMail: () -> Unit) =
    Counters(ui.eventsCount, ui.tasksCount, ui.mailCount, ui.mailConnected, onEvents, onTasks, onMail)

@Composable
private fun Counter(modifier: Modifier, icon: Int, value: String, label: String, onClick: () -> Unit) {
    val t = LocalTok.current
    // Icon, number and label on one line, the label right after its number (as on the widget)
    Row(
        modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Ic(icon, 16.dp, t.ink)
        Spacer(Modifier.width(5.dp))
        Text(value, style = if (t.predator) t.label(20.sp, t.text) else t.body(20.sp, t.text, FontWeight.Bold), maxLines = 1)
        Spacer(Modifier.width(5.dp))
        Text(t.s(label), style = if (t.predator) t.caption(10.sp) else t.body(11.sp, t.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * "WEATHER IN ZURICH", today's icon, high / low and condition, and the average rating hex; below, a second row with
 * the next five days (weekday, icon, high / low).
 */
@Composable
internal fun WeatherCard(
    weather: WeatherUi?,
    averageRating: Double?,
    hasScoring: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalTok.current
    val w = weather
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).framed(t, t.card, cut = 8.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (w != null) {
            Text(
                t.s(if (w.location.isNotBlank()) stringResource(R.string.today_weather_in, w.location) else stringResource(R.string.today_weather)),
                style = t.heroCaption(10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (w != null) {
                // Today's forecast, centered in the space left of the rating
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Ic(LucideIcons.weather(w.icon), 34.dp, t.ink, w.condition)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(w.temperature, style = if (t.predator) t.label(24.sp) else t.body(22.sp, t.text, FontWeight.Bold))
                        Text(w.low, style = t.caption(11.sp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(t.s(w.condition), style = t.caption(10.sp), maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            averageRating?.takeIf { !hasScoring }?.let { rating ->
                if (w != null) {
                    Box(Modifier.width(1.dp).height(38.dp).background(t.line))
                    Spacer(Modifier.width(12.dp))
                }
                if (t.predator) {
                    Hex(40.dp, edge = t.pd.accentHi, fill = t.accent) { Text("%.1f".format(rating), style = t.label(13.sp, t.onAccent)) }
                } else {
                    Text(
                        "★ ${"%.1f".format(rating)}",
                        style = t.body(13.sp, t.onAccent, FontWeight.Bold),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(t.accent).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (w != null && w.forecast.isNotEmpty()) {
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(t.line))
            Row(Modifier.fillMaxWidth()) {
                w.forecast.forEach { d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.s(d.day), style = t.caption(10.sp), maxLines = 1)
                            Spacer(Modifier.width(4.dp))
                            Ic(LucideIcons.weather(d.icon), 16.dp, t.ink, d.condition)
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(d.high, style = t.body(12.sp, t.text, FontWeight.Bold), maxLines = 1)
                            Spacer(Modifier.width(4.dp))
                            Text(d.low, style = t.caption(10.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeatherCard(ui: WidgetUi, onClick: () -> Unit) =
    WeatherCard(ui.weather, ui.averageRating, hasScoring = ui.scoring != null, onClick = onClick)

@Composable
internal fun WeatherCard(ui: MonthlyUi, onClick: () -> Unit) =
    WeatherCard(ui.weather, ui.averageRating, hasScoring = ui.scoring != null, onClick = onClick)

/** Journal and Budget: low-key outlined buttons (parallelograms in Predator, pills in Classic) in the muted text color. */
@Composable
internal fun QuickActions(onOpenWeb: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(Modifier.weight(1f), LucideIcons.JOURNAL, stringResource(R.string.today_journal)) { onOpenWeb(WebRoutes.JOURNAL) }
        ActionButton(Modifier.weight(1f), LucideIcons.BUDGET, stringResource(R.string.today_budget)) { onOpenWeb(WebRoutes.BUDGET) }
    }
}

@Composable
internal fun ActionButton(modifier: Modifier, icon: Int, label: String, onClick: () -> Unit) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdParallelogramShape(8.dp) else RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).border(1.dp, t.line, shape)
            .clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, 13.dp, t.dim)
        Spacer(Modifier.width(6.dp))
        Text(t.s(label), style = if (t.predator) t.label(11.sp, t.dim) else t.body(12.sp, t.dim, FontWeight.Medium), maxLines = 1)
    }
}

@Composable
internal fun LockedBanner(onUnlock: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).framed(t, t.tint, edge = if (t.predator) t.pd.edge2 else t.tint, cut = 8.dp)
            .clickable(onClick = onUnlock).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(LucideIcons.LOCK, 18.dp, t.onTint)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.today_locked_banner), style = t.body(12.sp, t.text), modifier = Modifier.weight(1f))
    }
}
