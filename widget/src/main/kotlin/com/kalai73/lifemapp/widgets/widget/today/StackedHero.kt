package com.kalai73.lifemapp.widgets.widget.today

// The hero stacked as on the web's mobile layout (components/layout/hero-section.tsx): Today chip, greeting,
// date with reload, "And you have" counters, weather; the mic button top right. Used by the Dashboard on tall sizes,
// where it scrolls with the events, tasks and shortcuts below it.

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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
import com.kalai73.lifemapp.widgets.widget.common.OpenAppButton
import com.kalai73.lifemapp.widgets.widget.common.VoiceButton
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.widget.common.numberStyle
import com.kalai73.lifemapp.widgets.widget.common.openRoute
import com.kalai73.lifemapp.widgets.widget.common.textStyle

/** The date and counter frames sit slightly inside the panel edges, as on the web. */
private val SIDE_INSET = 8.dp

/** The date frame is 20% narrower than the counter frame below it. */
private const val DATE_WIDTH_FRACTION = 0.8f

// Item ids for the hero rows: below the Dashboard's own section ids (-1 … -14)
private const val ID_GREETING = -101L
private const val ID_DATE = -102L
private const val ID_COUNTERS = -103L
private const val ID_WEATHER = -104L
private const val ID_WEB_APP = -105L
private const val ID_STATUS = -106L

/** The stacked hero as lazy items; each item is its own view tree, so the frames don't eat the root's depth. */
internal fun LazyListScope.stackedHero(ui: WidgetUi, showStatus: Boolean) {
    item(itemId = ID_GREETING) { GreetingBlock(ui) }
    item(itemId = ID_DATE) { DateBlock(ui) }
    item(itemId = ID_COUNTERS) {
        Box(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp, start = SIDE_INSET, end = SIDE_INSET)) {
            Counters(ui, caption = LocalContext.current.getString(R.string.and_you_have))
        }
    }
    if (ui.weather != null || ui.averageRating != null) item(itemId = ID_WEATHER) { WeatherCard(ui) }
    item(itemId = ID_WEB_APP) { WebAppButton() }
    if (showStatus) item(itemId = ID_STATUS) {
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.End) { StatusLine(ui) }
    }
}

/** A full-width card: chamfered raised fill without border (edge drawn in the fill) in Predator, surface with rounded corners in Classic. */
@Composable
private fun HeroCard(gap: androidx.compose.ui.unit.Dp, modifier: GlanceModifier = GlanceModifier.fillMaxWidth(), content: @Composable (GlanceModifier) -> Unit) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    Box(modifier = GlanceModifier.fillMaxWidth().padding(top = gap)) {
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.raised, fill = pd.raised, modifier = modifier) { frame -> content(frame.fillMaxWidth()) }
        } else {
            content(modifier.background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
        }
    }
}

/** "HELLO," (left-aligned) over the name, in a frame as wide as its text, centered;the mic button in the top right corner. */
@Composable
private fun GreetingBlock(ui: WidgetUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val name = ui.username ?: context.getString(R.string.welcome)
    val body: @Composable (GlanceModifier) -> Unit = { modifier ->
        Column(modifier = modifier.padding(horizontal = 18.dp, vertical = 5.dp), horizontalAlignment = Alignment.Start) {
            Caption(context.getString(R.string.hello))
            // Display face as on the web hero: condensed bold italic (Predator), bold (Classic)
            Text(
                name,
                style = if (style.isPredator) numberStyle(26.sp, color = GlanceTheme.colors.primary).copy(textAlign = TextAlign.Center)
                else textStyle(size = 24.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary).copy(textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        // Equal-width ends keep the greeting centered while the open-app button sits in the top right corner
        Spacer(GlanceModifier.width(32.dp))
        Row(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pd != null) {
                PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.raised, fill = pd.raised) { frame -> body(frame) }
            } else {
                body(GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
            }
        }
        OpenAppButton()
    }
}

/** Footer of the tall Dashboard: the mic and reload buttons, pinned below the scrolling list. */
@Composable
internal fun HeroFooter(ui: WidgetUi) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val context = LocalContext.current
        val style = LocalWidgetStyle.current
        // Two equal halves, each with its icon and label centered; a label wider than its half's room beside the
        // icon is cut with an ellipsis (one line) instead of pushing the other half out of the widget
        val half = @Composable { modifier: GlanceModifier, text: String, button: @Composable () -> Unit ->
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                button()
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    style.label(text),
                    style = captionStyle(size = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
        }
        half(GlanceModifier.defaultWeight(), context.getString(R.string.record)) { VoiceButton() }
        half(GlanceModifier.defaultWeight(), context.getString(R.string.reload)) { ReloadButton(ui, primary = true) }
    }
}

/** "TODAY IS," over the date, the reload button inside the same frame on the right. */
@Composable
private fun DateBlock(ui: WidgetUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    // DATE_WIDTH_FRACTION of the inset width, centered. Glance has no fractional widths, so it's computed from the widget size
    // minus the panel padding (12dp a side) and the side inset.
    val frameWidth = (LocalSize.current.width - 24.dp - SIDE_INSET * 2) * DATE_WIDTH_FRACTION
    // Shrink the date to fit the frame: text space is the frame minus padding (16dp);
    // a condensed bold glyph averages about 0.55em.
    val textWidth = (frameWidth - 16.dp).value
    val dateSize = (textWidth / (ui.todayLabel.length.coerceAtLeast(1) * 0.55f)).coerceIn(13f, if (style.isPredator) 24f else 20f).sp
    val body: @Composable (GlanceModifier) -> Unit = { modifier ->
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Caption(context.getString(R.string.today_is) + ",")
                Text(
                    ui.todayLabel,
                    style = if (style.isPredator) numberStyle(dateSize, color = GlanceTheme.colors.primary) else textStyle(size = dateSize, weight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                    maxLines = 1,
                )
            }
        }
    }
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.raised, fill = pd.raised, modifier = GlanceModifier.width(frameWidth)) { frame -> body(frame) }
        } else {
            body(GlanceModifier.width(frameWidth).background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
        }
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
                ui.averageRating?.let { rating ->
                    if (w != null) {
                        // Thin vertical divider between the weather and the score
                        Spacer(GlanceModifier.width(8.dp))
                        Box(
                            modifier = (GlanceModifier.width(1.dp).height(36.dp))
                                .let { if (pd != null) it.background(pd.edge) else it.background(GlanceTheme.colors.outline) },
                        ) {}
                        Spacer(GlanceModifier.width(10.dp))
                    }
                    RatingBadge(rating)
                }
            }
        }
    }
}

/** Full-width button under the weather card that opens the LIFE MAPP web app (Today page), signed in. */
@Composable
private fun WebAppButton() {
    val context = LocalContext.current
    HeroCard(gap = 8.dp, modifier = GlanceModifier.fillMaxWidth().clickable(openRoute(context, WebRoutes.TODAY))) { modifier ->
        Row(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                LocalWidgetStyle.current.label(context.getString(R.string.open_web_app)),
                style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RatingBadge(rating: Double) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    if (pd != null) {
        // Full hex score: accent fill, number in pd-on (styleguide §6 PdHexScore)
        PdHex(36.dp, edge = pd.accentHi, fill = pd.accent) {
            Text("%.1f".format(rating), style = numberStyle(13.sp, color = pd.on))
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
