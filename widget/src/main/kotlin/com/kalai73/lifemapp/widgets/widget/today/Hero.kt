package com.kalai73.lifemapp.widgets.widget.today

// The hero section (components/layout/hero-section.tsx on the web), shown at the top of the Dashboard widget.

import com.kalai73.lifemapp.widgets.widget.common.PdWrapFrame
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
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
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.core.designsystem.WidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.PdDivider
import com.kalai73.lifemapp.widgets.widget.common.PdFrame
import com.kalai73.lifemapp.widgets.widget.common.PdHex
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.widget.common.numberStyle
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.openExternal
import com.kalai73.lifemapp.widgets.widget.common.openRoute
import com.kalai73.lifemapp.widgets.widget.common.textStyle

/**
 * Greeting and date (the arrow banner in Predator), then weather and rating, then [trailing].
 * Emits a single Row. It sits outside the lazy list, so its depth counts from the widget root: keep the
 * banner path (root, column, row, frame ×4, column, greeting column, text) within RemoteViews' 10 levels.
 */
@Composable
internal fun HeroHeader(ui: WidgetUi, showLocation: Boolean, trailing: @Composable () -> Unit = {}) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        val pd = LocalPd.current
        if (pd != null) {
            // Hero banner (styleguide §7): arrow frame, raised fill, pointing at the weather. As tall as its text.
            PdWrapFrame(PdShape.ARROW, edge = pd.edge, fill = pd.raised, modifier = GlanceModifier.defaultWeight()) { frame ->
                Column(modifier = frame.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 4.dp, bottom = 5.dp)) {
                    Greeting(ui, compact = false)
                    DateLine(ui)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
        } else {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Greeting(ui, compact = false)
                DateLine(ui)
            }
        }
        WeatherAndRating(ui, showLocation)
        trailing()
    }
}

@Composable
private fun Greeting(ui: WidgetUi, compact: Boolean) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val name = ui.username ?: context.getString(R.string.welcome)
    Column {
        if (style.isPredator) {
            Text(style.label(context.getString(R.string.hello)), style = captionStyle(size = 9.sp, color = GlanceTheme.colors.primary))
            // The name is user content: display weight, not italic (styleguide §4)
            Text(name, style = textStyle(size = if (compact) 16.sp else 19.sp, weight = FontWeight.Bold).copy(fontFamily = WidgetStyle.Condensed), maxLines = 1)
        } else {
            Text(style.label(context.getString(R.string.hello)).uppercase(), style = textStyle(size = 9.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.onSurfaceVariant))
            Text(name, style = textStyle(size = if (compact) 16.sp else 20.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary), maxLines = 1)
        }
    }
}

@Composable
private fun DateLine(ui: WidgetUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val text = "${context.getString(R.string.today_is)} ${ui.todayLabel}"
    Text(
        style.label(text),
        style = if (style.isPredator) captionStyle(size = 10.sp) else textStyle(size = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
        maxLines = 1,
    )
}

@Composable
private fun WeatherAndRating(ui: WidgetUi, showLocation: Boolean) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    Column(horizontalAlignment = Alignment.End) {
        ui.weather?.let { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LucideIcons.weather(w.icon), w.condition, size = 20.dp)
                Spacer(GlanceModifier.width(4.dp))
                // Weather values: italic ink (styleguide §7 weather tile)
                Text(w.temperature, style = if (pd != null) numberStyle(17.sp, color = pd.ink) else textStyle(size = 16.sp, weight = FontWeight.Bold))
            }
            if (showLocation && w.location.isNotBlank()) {
                Text(
                    style.label(w.location),
                    style = if (pd != null) captionStyle(size = 9.sp) else textStyle(size = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
        }
        ui.averageRating?.let { rating ->
            Spacer(GlanceModifier.height(4.dp))
            if (pd != null) {
                // Full hex score: accent fill, number in pd-on (styleguide §6 PdHexScore)
                PdHex(30.dp, edge = pd.accentHi, fill = pd.accent) {
                    Text("%.1f".format(rating), style = numberStyle(11.sp, color = pd.on))
                }
            } else {
                Box(
                    modifier = GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(style.innerRadius).padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("★ ${"%.1f".format(rating)}", style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary))
                }
            }
        }
    }
}

@Composable
internal fun Counters(ui: WidgetUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val mailAction = ui.mailUrl?.let(::openExternal) ?: openRoute(context, WebRoutes.MAIL)
    val row: @Composable (GlanceModifier) -> Unit = { modifier ->
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Counter(LucideIcons.CALENDAR, ui.eventsCount.toString(), context.getString(R.string.events), openRoute(context, WebRoutes.EVENTS))
            if (pd != null) PdDivider(18.dp)
            Counter(LucideIcons.TASKS, ui.tasksCount.toString(), context.getString(R.string.tasks), openRoute(context, WebRoutes.TASKS))
            if (pd != null) PdDivider(18.dp)
            Counter(LucideIcons.MAIL, if (ui.mailConnected) ui.mailCount.toString() else "–", context.getString(R.string.mail), mailAction)
        }
    }
    if (pd != null) {
        PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge, fill = pd.raised, modifier = GlanceModifier.fillMaxWidth()) { frame ->
            row(frame.padding(vertical = 3.dp))
        }
    } else {
        row(GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius).padding(vertical = 4.dp))
    }
}

@Composable
private fun androidx.glance.layout.RowScope.Counter(icon: Int, value: String, label: String, onClick: Action) {
    val style = LocalWidgetStyle.current
    Column(
        modifier = GlanceModifier.defaultWeight().clickable(onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, size = 14.dp)
            Spacer(GlanceModifier.width(4.dp))
            Text(value, style = if (style.isPredator) numberStyle(16.sp) else textStyle(size = 16.sp, weight = FontWeight.Bold))
        }
        Text(
            style.label(label),
            style = if (style.isPredator) captionStyle(size = 8.sp) else textStyle(size = 9.sp, color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
        )
    }
}

@Composable
internal fun QuickButton(icon: Int, label: String, onClick: Action, modifier: GlanceModifier? = null) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    // [modifier] given: the button stretches to it (stacked hero, two buttons sharing the width), content centered
    val fill = modifier != null
    if (pd != null) {
        // Secondary button: parallelogram, framed raised fill, pd-label (styleguide §6 Button)
        PdWrapFrame(PdShape.PARA, edge = pd.edge, fill = pd.raised, modifier = (modifier ?: GlanceModifier).clickable(onClick)) { frame ->
            Row(
                modifier = (if (fill) frame.fillMaxWidth() else frame).padding(horizontal = 14.dp, vertical = if (fill) 7.dp else 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, size = 13.dp, tint = pd.ink)
                Spacer(GlanceModifier.width(5.dp))
                Text(style.label(label), style = labelStyle(size = 11.sp, color = pd.text))
            }
        }
        return
    }
    Row(
        modifier = (modifier ?: GlanceModifier).background(GlanceTheme.colors.primaryContainer).cornerRadius(style.innerRadius)
            .clickable(onClick).padding(horizontal = 10.dp, vertical = if (fill) 7.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, size = 14.dp, tint = GlanceTheme.colors.onPrimaryContainer)
        Spacer(GlanceModifier.width(4.dp))
        Text(style.label(label), style = textStyle(size = 12.sp, weight = FontWeight.Medium, color = GlanceTheme.colors.onPrimaryContainer))
    }
}
