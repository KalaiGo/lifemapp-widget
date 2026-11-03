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
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.layout.size
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.kalai73.lifemapp.widgets.core.presentation.ScoringUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import com.kalai73.lifemapp.widgets.widget.common.PdHex
import com.kalai73.lifemapp.widgets.widget.common.openSection
import com.kalai73.lifemapp.widgets.widget.integrity.RatingBadge
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.core.designsystem.WidgetStyle
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.PdWrapFrame
import com.kalai73.lifemapp.widgets.widget.common.ReloadAction
import com.kalai73.lifemapp.widgets.widget.voice.VoiceInputActivity
import com.kalai73.lifemapp.widgets.widget.common.ReloadButton
import com.kalai73.lifemapp.widgets.widget.common.StatusLine
import com.kalai73.lifemapp.widgets.widget.common.openApp
import com.kalai73.lifemapp.widgets.widget.common.VoiceButton
import com.kalai73.lifemapp.widgets.widget.common.captionStyle
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.widget.common.numberStyle
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
private const val ID_SCORING = -105L

/** The stacked hero as lazy items; each item is its own view tree, so the frames don't eat the root's depth. */
internal fun LazyListScope.stackedHero(ui: WidgetUi, showStatus: Boolean) {
    item(itemId = ID_GREETING) { GreetingBlock(ui) }
    item(itemId = ID_DATE) { DateBlock(ui, showStatus) }
    item(itemId = ID_COUNTERS) {
        Box(modifier = GlanceModifier.fillMaxWidth().padding(start = SIDE_INSET, end = SIDE_INSET)) {
            Counters(ui, caption = LocalContext.current.getString(R.string.and_you_have))
        }
    }
    if (ui.weather != null) item(itemId = ID_WEATHER) { WeatherCard(ui) }
    ui.scoring?.takeIf { it.domains.isNotEmpty() }?.let { scoring -> item(itemId = ID_SCORING) { ScoringBanner(scoring, ui.averageRating) } }
}

/** Default names by position, as the app's scoring section, for a domain without one (or encrypted and locked). */
private val DEFAULT_DOMAIN_NAMES = mapOf(2 to "Serenity", 3 to "Vitality", 4 to "Stability", 5 to "Tenacity")

/** Short domain names under the badges: three letters fit every cell, where full names were cut with an ellipsis. */
private const val SHORT_NAME_LENGTH = 3

/**
 * The scoring values in one small row of equal cells: each domain's rating badge (the Integrity card's ramp colors)
 * over its short name, then, after a divider, the average. A tap opens the app at its scoring section.
 */
@Composable
private fun ScoringBanner(scoring: ScoringUi, average: Double?) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val dim = pd?.dim ?: GlanceTheme.colors.onSurfaceVariant
    val cell = @Composable { cellModifier: GlanceModifier, label: String, labelColor: androidx.glance.unit.ColorProvider, badge: @Composable () -> Unit ->
        Column(modifier = cellModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            badge()
            Spacer(GlanceModifier.height(2.dp))
            Text(style.label(label), style = captionStyle(size = 9.sp, color = labelColor).copy(textAlign = TextAlign.Center), maxLines = 1)
        }
    }
    HeroCard(gap = 8.dp, modifier = GlanceModifier.fillMaxWidth().clickable(openSection(context, LifeMappWidgets.SECTION_SCORING))) { modifier ->
        Row(modifier = modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            scoring.domains.take(5).forEach { d ->
                val name = d.name?.takeIf { it.isNotBlank() }
                    ?: if (d.position == 1) context.getString(R.string.integrity) else DEFAULT_DOMAIN_NAMES[d.position].orEmpty()
                cell(GlanceModifier.defaultWeight(), name.take(SHORT_NAME_LENGTH), if (d.hidden) pd?.faint ?: GlanceTheme.colors.outline else dim) {
                    // Locked (null): an empty badge with a dash; unrated (0): the empty badge with 0, as the domain card
                    RatingBadge(d.rating ?: 0, label = d.rating?.toString() ?: "–")
                }
            }
            average?.let { avg ->
                Box(modifier = (if (pd != null) GlanceModifier.background(pd.edge) else GlanceModifier.background(GlanceTheme.colors.outline)).width(1.dp).height(30.dp)) {}
                cell(GlanceModifier.defaultWeight(), context.getString(R.string.scoring_banner_avg), pd?.ink ?: GlanceTheme.colors.primary) {
                    val text = "%.1f".format(avg)
                    if (pd != null) {
                        PdHex(24.dp, edge = pd.accentHi, fill = pd.accent) { Text(text, style = numberStyle(10.sp, color = pd.on), maxLines = 1) }
                    } else {
                        Box(
                            modifier = GlanceModifier.height(24.dp).background(GlanceTheme.colors.primary).cornerRadius(12.dp).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text, style = textStyle(size = 11.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary), maxLines = 1)
                        }
                    }
                }
            }
        }
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

/** "HELLO," (left-aligned) over the name, in a frame as wide as its text, centered;the open-app button now lives in the footer. */
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
        Row(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pd != null) {
                PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.raised, fill = pd.raised) { frame -> body(frame) }
            } else {
                body(GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
            }
        }
    }
}

/** Footer of the tall Dashboard: the open-app (icon only), mic and reload buttons, pinned below the scrolling list. */
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
        // The border wraps the icon and the label together; the icon itself has none.
        val pd = LocalPd.current
        val half = @Composable { modifier: GlanceModifier, text: String?, action: Action, icon: @Composable () -> Unit ->
            val body: @Composable (GlanceModifier) -> Unit = { frame ->
                Row(
                    modifier = frame.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    icon()
                    if (text != null) {
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            style.label(text),
                            style = captionStyle(size = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            maxLines = 1,
                        )
                    }
                }
            }
            Row(modifier = modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pd != null) {
                    PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge, fill = pd.panel, modifier = GlanceModifier.fillMaxWidth().clickable(action)) { frame -> body(frame) }
                } else {
                    body(GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius).clickable(action))
                }
            }
        }
        val record = context.getString(R.string.record)
        val reload = context.getString(R.string.reload)
        val openAppDesc = context.getString(R.string.open_app)
        half(GlanceModifier.width(52.dp), null, openApp(context)) {
            Icon(LucideIcons.SMARTPHONE, openAppDesc, size = 16.dp, tint = pd?.ink ?: GlanceTheme.colors.primary)
        }
        half(GlanceModifier.defaultWeight(), record, actionStartActivity(VoiceInputActivity.intent(context))) {
            Icon(LucideIcons.MIC, record, size = 16.dp, tint = pd?.ink ?: GlanceTheme.colors.primary)
        }
        half(GlanceModifier.defaultWeight(), reload, actionRunCallback<ReloadAction>()) {
            val tint = pd?.ink ?: GlanceTheme.colors.primary
            if (ui.syncing) CircularProgressIndicator(modifier = GlanceModifier.size(14.dp), color = tint)
            else Icon(LucideIcons.RELOAD, reload, size = 16.dp, tint = tint)
        }
    }
}

/** "TODAY IS," over the date, the reload button inside the same frame on the right. */
@Composable
private fun DateBlock(ui: WidgetUi, showStatus: Boolean) {
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
            Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Caption(context.getString(R.string.today_is) + ",")
                }
                Text(
                    ui.todayLabel,
                    style = if (style.isPredator) numberStyle(dateSize, color = GlanceTheme.colors.primary) else textStyle(size = dateSize, weight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                    maxLines = 1,
                )
                if (showStatus) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End) { StatusLine(ui) }
                }
            }
        }
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.raised, fill = pd.raised, modifier = GlanceModifier.width(frameWidth)) { frame -> body(frame) }
        } else {
            body(GlanceModifier.width(frameWidth).background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
        }
    }
}

/**
 * "WEATHER IN ZURICH, CH", then today's icon, high / low and condition, centered. Same layout as the top of the app's
 * Today screen weather card, without the rating or the following days.
 */
@Composable
private fun WeatherCard(ui: WidgetUi) {
    val context = LocalContext.current
    val pd = LocalPd.current
    val w = ui.weather ?: return
    val captionText = @Composable { text: String, size: androidx.compose.ui.unit.TextUnit, maxLines: Int ->
        Text(
            text,
            style = if (pd != null) captionStyle(size = size) else textStyle(size = size, color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = maxLines,
        )
    }
    HeroCard(gap = 16.dp) { modifier ->
        Column(modifier = modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Caption(if (w.location.isNotBlank()) context.getString(R.string.weather_in, w.location) else context.getString(R.string.weather))
            Spacer(GlanceModifier.height(6.dp))
            // Today's forecast, centered
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LucideIcons.weather(w.icon), w.condition, size = 34.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    // Weather values: italic ink (styleguide §7 weather tile)
                    Text(w.temperature, style = if (pd != null) numberStyle(24.sp, color = pd.ink) else textStyle(size = 22.sp, weight = FontWeight.Bold))
                    captionText(w.low, 11.sp, 1)
                }
                Spacer(GlanceModifier.width(12.dp))
                captionText(w.condition, 10.sp, 2)
            }
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
