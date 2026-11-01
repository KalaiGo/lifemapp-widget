package com.kalai73.lifemapp.widgets.widget.today

// The hero stacked as on the web's mobile layout (components/layout/hero-section.tsx): Today chip, greeting,
// date with reload, "And you have" counters, weather, Journal / Budget. Used by the Dashboard on tall sizes,
// where it scrolls with the events, tasks and shortcuts below it.

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyListScope
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.core.designsystem.WidgetStyle
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.PdHex
import com.kalai73.lifemapp.widgets.widget.common.PdWrapFrame
import com.kalai73.lifemapp.widgets.widget.common.ReloadButton
import com.kalai73.lifemapp.widgets.widget.common.StatusLine
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.widget.common.numberStyle
import com.kalai73.lifemapp.widgets.widget.common.openRoute
import com.kalai73.lifemapp.widgets.widget.common.textStyle

// Item ids for the hero rows: below the Dashboard's own section ids (-1 … -14)
private const val ID_CHIP = -100L
private const val ID_GREETING = -101L
private const val ID_DATE = -102L
private const val ID_COUNTERS = -103L
private const val ID_WEATHER = -104L
private const val ID_BUTTONS = -105L
private const val ID_STATUS = -106L

/** The stacked hero as lazy items; each item is its own view tree, so the frames don't eat the root's depth. */
internal fun LazyListScope.stackedHero(ui: WidgetUi, showStatus: Boolean) {
    item(itemId = ID_CHIP) { TodayChip(ui) }
    item(itemId = ID_GREETING) { HeroCard(gap = 6.dp) { GreetingBlock(ui, it) } }
    item(itemId = ID_DATE) { DateBlock(ui) }
    item(itemId = ID_COUNTERS) {
        Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
            Caption(LocalContext.current.getString(R.string.and_you_have))
            Spacer(GlanceModifier.height(2.dp))
            Counters(ui)
        }
    }
    if (ui.weather != null || ui.averageRating != null) item(itemId = ID_WEATHER) { WeatherCard(ui) }
    item(itemId = ID_BUTTONS) {
        val context = LocalContext.current
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            QuickButton(LucideIcons.JOURNAL, context.getString(R.string.journal), openRoute(context, WebRoutes.JOURNAL), GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            QuickButton(LucideIcons.BUDGET, context.getString(R.string.budget), openRoute(context, WebRoutes.BUDGET), GlanceModifier.defaultWeight())
        }
    }
    if (showStatus) item(itemId = ID_STATUS) {
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.End) { StatusLine(ui) }
    }
}

/** "TODAY" tag and the three counters in small, centered (the web's sticky Today bar). */
@Composable
private fun TodayChip(ui: WidgetUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val mail = if (ui.mailConnected) ui.mailCount.toString() else "–"
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(openRoute(context, WebRoutes.EVENTS)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = style.label(context.getString(R.string.today))
        val counts: @Composable (GlanceModifier) -> Unit = { modifier ->
            Row(modifier = modifier.padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniCount(LucideIcons.CALENDAR, ui.eventsCount.toString())
                Spacer(GlanceModifier.width(8.dp))
                MiniCount(LucideIcons.TASKS, ui.tasksCount.toString())
                Spacer(GlanceModifier.width(8.dp))
                MiniCount(LucideIcons.MAIL, mail)
            }
        }
        if (pd != null) {
            PdWrapFrame(PdShape.PARA, edge = pd.edge, fill = pd.raised) { frame ->
                Text(label, style = labelStyle(size = 11.sp), modifier = frame.padding(horizontal = 14.dp, vertical = 3.dp))
            }
            Spacer(GlanceModifier.width(4.dp))
            PdWrapFrame(PdShape.PARA, edge = pd.edge, fill = pd.well) { frame -> counts(frame) }
        } else {
            Text(label, style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary), modifier = GlanceModifier.padding(end = 6.dp))
            counts(GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
        }
    }
}

@Composable
private fun MiniCount(icon: Int, value: String) {
    val style = LocalWidgetStyle.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, size = 11.dp)
        Spacer(GlanceModifier.width(3.dp))
        Text(value, style = if (style.isPredator) numberStyle(11.sp) else textStyle(size = 11.sp, weight = FontWeight.Bold))
    }
}

/** A full-width card: chamfered raised frame in Predator, surface with rounded corners in Classic. */
@Composable
private fun HeroCard(gap: androidx.compose.ui.unit.Dp, modifier: GlanceModifier = GlanceModifier.fillMaxWidth(), content: @Composable (GlanceModifier) -> Unit) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    Box(modifier = GlanceModifier.fillMaxWidth().padding(top = gap)) {
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge, fill = pd.raised, modifier = modifier) { frame -> content(frame.fillMaxWidth()) }
        } else {
            content(modifier.background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
        }
    }
}

@Composable
private fun GreetingBlock(ui: WidgetUi, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val name = ui.username ?: context.getString(R.string.welcome)
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Caption(context.getString(R.string.hello))
        // The name is user content: display weight, not italic (styleguide §4)
        Text(
            name,
            style = if (style.isPredator) {
                textStyle(size = 24.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary).copy(fontFamily = WidgetStyle.Condensed, textAlign = TextAlign.Center)
            } else {
                textStyle(size = 24.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary).copy(textAlign = TextAlign.Center)
            },
            maxLines = 1,
        )
    }
}

/** "TODAY IS," over the date, with the reload button beside it. */
@Composable
private fun DateBlock(ui: WidgetUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val body: @Composable (GlanceModifier) -> Unit = { modifier ->
            Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Caption(context.getString(R.string.today_is) + ",")
                Text(
                    ui.todayLabel,
                    style = if (style.isPredator) numberStyle(20.sp, color = GlanceTheme.colors.primary) else textStyle(size = 18.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                    maxLines = 1,
                )
            }
        }
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge, fill = pd.raised, modifier = GlanceModifier.defaultWeight()) { frame -> body(frame.fillMaxWidth()) }
        } else {
            body(GlanceModifier.defaultWeight().background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
        }
        Spacer(GlanceModifier.width(6.dp))
        ReloadButton(ui)
    }
}

/** "WEATHER IN ZURICH, CH", today's icon, high / low and condition, and the average rating hex. */
@Composable
private fun WeatherCard(ui: WidgetUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val w = ui.weather
    HeroCard(gap = 8.dp) { modifier ->
        Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (w != null) {
                Caption(if (w.location.isNotBlank()) context.getString(R.string.weather_in, w.location) else context.getString(R.string.weather))
                Spacer(GlanceModifier.height(4.dp))
            }
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (w != null) {
                    Icon(LucideIcons.weather(w.icon), w.condition, size = 30.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        // Weather values: italic ink (styleguide §7 weather tile)
                        Text(w.temperature, style = if (pd != null) numberStyle(22.sp, color = pd.ink) else textStyle(size = 20.sp, weight = FontWeight.Bold))
                        Text(w.low, style = if (pd != null) captionStyle(size = 11.sp) else textStyle(size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    Text(
                        w.condition,
                        style = if (pd != null) captionStyle(size = 10.sp) else textStyle(size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 2,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                ui.averageRating?.let { rating -> RatingBadge(rating) }
            }
        }
    }
}

@Composable
private fun RatingBadge(rating: Double) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val description = LocalContext.current.getString(R.string.rating)
    if (pd != null) {
        // Full hex score: accent fill, number in pd-on (styleguide §6 PdHexScore)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PdHex(36.dp, edge = pd.accentHi, fill = pd.accent) {
                Text("%.1f".format(rating), style = numberStyle(13.sp, color = pd.on))
            }
            Text(style.label(description), style = captionStyle(size = 8.sp))
        }
    } else {
        Box(
            modifier = GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(style.innerRadius).padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text("★ ${"%.1f".format(rating)}", style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary))
        }
    }
}

/** Small spaced caption above a block ("HELLO,", "AND YOU HAVE:"). */
@Composable
private fun Caption(text: String) {
    val style = LocalWidgetStyle.current
    Text(
        style.label(text),
        style = if (style.isPredator) captionStyle(size = 9.sp, color = GlanceTheme.colors.primary)
        else textStyle(size = 9.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.onSurfaceVariant),
        maxLines = 1,
    )
}
