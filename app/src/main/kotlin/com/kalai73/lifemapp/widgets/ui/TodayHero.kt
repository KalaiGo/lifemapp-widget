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
import com.kalai73.lifemapp.widgets.core.model.DashboardTab
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.presentation.MonthlyUi
import com.kalai73.lifemapp.widgets.core.presentation.WeatherUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import androidx.compose.ui.platform.LocalContext
import com.kalai73.lifemapp.widgets.widget.voice.VoiceInputActivity

// ── Top bar and hero ────────────────────────────────────────────────────────

@Composable
private fun HeaderButton(onClick: () -> Unit, desc: String, primary: Boolean = false, width: Dp = 36.dp, content: @Composable () -> Unit) {
    val t = LocalTok.current
    val mod = Modifier.size(width, 36.dp).let {
        if (t.predator) it.framed(t, fill = if (primary) t.accent else t.pd.panel, edge = if (primary) t.pd.accentHi else t.pd.edge)
        else it.clip(CircleShape).let { m -> if (primary) m.background(t.accent) else m }
    }
    Box(mod.clickable(onClickLabel = desc, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun DashboardTabToggle(
    activeTab: DashboardTab,
    onSelectTab: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdParallelogramShape(6.dp) else RoundedCornerShape(16.dp)
    val containerShape: Shape = if (t.predator) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .clip(containerShape)
            .background(if (t.predator) t.card else t.card.copy(alpha = 0.6f))
            .border(1.dp, if (t.predator) t.pd.edge else t.line, containerShape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardTabItem(
            label = stringResource(R.string.tab_today),
            selected = activeTab == DashboardTab.TODAY,
            shape = shape,
            onClick = { onSelectTab(DashboardTab.TODAY) },
        )
        Spacer(Modifier.width(4.dp))
        DashboardTabItem(
            label = stringResource(R.string.tab_monthly),
            selected = activeTab == DashboardTab.MONTHLY,
            shape = shape,
            onClick = { onSelectTab(DashboardTab.MONTHLY) },
        )
    }
}

@Composable
private fun DashboardTabItem(
    label: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val t = LocalTok.current
    val bg = if (selected) t.accent else Color.Transparent
    val textColor = if (selected) t.onAccent else t.dim
    val textStyle = if (t.predator) {
        t.label(12.sp, textColor)
    } else {
        t.body(12.sp, textColor, if (selected) FontWeight.Bold else FontWeight.Medium)
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = t.s(label),
            style = textStyle,
            maxLines = 1,
        )
    }
}

@Composable
internal fun TopBar(
    ui: WidgetUi? = null,
    error: SyncError? = ui?.error,
    syncing: Boolean = ui?.syncing ?: false,
    onOpenSettings: () -> Unit,
    onReload: () -> Unit,
    navOpen: Boolean,
    onToggleNav: () -> Unit,
    activeTab: DashboardTab? = null,
    onSelectTab: ((DashboardTab) -> Unit)? = null,
) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderButton(onOpenSettings, stringResource(R.string.settings_open)) {
            Ic(LucideIcons.PANEL, 18.dp, t.ink, stringResource(R.string.settings_open))
        }
        if (activeTab != null && onSelectTab != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DashboardTabToggle(activeTab = activeTab, onSelectTab = onSelectTab)
            }
        } else {
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("LIFE MAPP", style = t.label(16.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                StatusLine(error)
            }
        }
        // Navigation overlay (the web's DomainFloatingNav), highlighted while open
        HeaderButton(onToggleNav, stringResource(R.string.today_nav), primary = navOpen, width = 48.dp) {
            val tint = if (navOpen) t.onAccent else t.ink
            Ic(if (navOpen) LucideIcons.CLOSE else LucideIcons.NAVIGATION, 18.dp, tint, stringResource(R.string.today_nav))
        }
        Spacer(Modifier.width(8.dp))
        // Mic: add tasks and reminders by voice (the widgets' VoiceInputActivity)
        val context = LocalContext.current
        HeaderButton({ context.startActivity(VoiceInputActivity.intent(context)) }, stringResource(R.string.today_voice), width = 48.dp) {
            Ic(LucideIcons.MIC, 18.dp, t.ink, stringResource(R.string.today_voice))
        }
        Spacer(Modifier.width(8.dp))
        ReloadButton(syncing, onReload)
    }
}

@Composable
internal fun TopBar(
    ui: WidgetUi,
    onOpenSettings: () -> Unit,
    onReload: () -> Unit,
    navOpen: Boolean,
    onToggleNav: () -> Unit,
) = TopBar(
    ui = ui,
    error = ui.error,
    syncing = ui.syncing,
    onOpenSettings = onOpenSettings,
    onReload = onReload,
    navOpen = navOpen,
    onToggleNav = onToggleNav,
    activeTab = null,
    onSelectTab = null,
)

@Composable
private fun ReloadButton(syncing: Boolean, onReload: () -> Unit) {
    val tint = LocalTok.current.ink
    HeaderButton(onReload, stringResource(R.string.today_reload), primary = false, width = 48.dp) {
        if (syncing) CircularProgressIndicator(Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
        else Ic(LucideIcons.RELOAD, 16.dp, tint, stringResource(R.string.today_reload))
    }
}

/** The last sync error, if any (no "Updated" time). */
@Composable
internal fun StatusLine(error: SyncError?) {
    val t = LocalTok.current
    val text = when (error) {
        SyncError.UNAUTHORIZED -> stringResource(R.string.sync_error_unauthorized)
        SyncError.NETWORK -> stringResource(R.string.error_network)
        SyncError.SERVER -> stringResource(R.string.sync_error_server)
        else -> return
    }
    Text(text, style = t.caption(10.sp, t.destructive), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun StatusLine(ui: WidgetUi) = StatusLine(ui.error)

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
