package com.kalai73.lifemapp.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.pdChamferShape
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.model.DashboardTab
import com.kalai73.lifemapp.widgets.core.presentation.EventUi
import com.kalai73.lifemapp.widgets.core.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.presentation.MonthlyUi
import com.kalai73.lifemapp.widgets.core.presentation.TaskUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.widget.voice.VoiceInputActivity

/** Room the floating bars take at the bottom: the screens' last spacer and the navigation overlay sit above it. */
internal val FLOATING_BARS_CLEARANCE: Dp = 112.dp

/**
 * The web's bottom floating bars (main-layout.tsx), stacked and centered: the actions pill (navigation overlay,
 * search, settings | voice, reminder) over the page pill (TODAY, MONTHLY, and COMPANY for admins).
 */
@Composable
internal fun BoxScope.DashboardFloatingBars(
    activeTab: DashboardTab,
    onSelectTab: (DashboardTab) -> Unit,
    /** COMPANY (the web's /company; no native screen): admins only */
    showCompany: Boolean,
    onOpenCompany: () -> Unit,
    navOpen: Boolean,
    onToggleNav: () -> Unit,
    searchOpen: Boolean,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Reload, right of the reminder button: a spinner while syncing, red after a failed sync */
    syncing: Boolean,
    syncError: Boolean,
    onReload: () -> Unit,
) {
    val t = LocalTok.current
    val context = LocalContext.current
    Column(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 1. Actions: the predator pill carries the web's pd-target corner brackets
        Row(
            Modifier.let { if (t.predator) it.bracketCorners(t.accent, arm = 7.dp, stroke = 1.5.dp, tick = 0.dp).padding(3.dp) else it }
                .pill(t).padding(horizontal = if (t.predator) 12.dp else 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val nav = stringResource(R.string.today_nav)
            BarButton(if (navOpen) LucideIcons.CLOSE else LucideIcons.NAVIGATION, nav, active = navOpen, onClick = onToggleNav)
            val search = stringResource(R.string.search_open)
            BarButton(if (searchOpen) LucideIcons.CLOSE else LucideIcons.SEARCH, search, active = searchOpen, onClick = onSearch)
            BarButton(LucideIcons.SETTINGS, stringResource(R.string.settings_open), onClick = onOpenSettings)
            Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 20.dp).background(t.line))
            // Voice and reminder: the widgets' VoiceInputActivity, as the Integrity card's Reception does
            BarButton(LucideIcons.MIC, stringResource(R.string.today_voice), tint = t.ink) {
                context.startActivity(VoiceInputActivity.intent(context))
            }
            BarButton(LucideIcons.STICKY_NOTE, stringResource(R.string.today_create_reminder), tint = if (t.dark) Amber400 else Amber600) {
                context.startActivity(VoiceInputActivity.intent(context, reminders = true, typed = true))
            }
            val reload = stringResource(R.string.today_reload)
            BarButton(LucideIcons.RELOAD, reload, tint = if (syncError) t.destructive else t.dim, busy = syncing, onClick = onReload)
        }

        // 2. Pages: across the available width, the tabs sharing it equally
        Row(
            Modifier.fillMaxWidth().pill(t).padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PageTab(LucideIcons.CALENDAR, stringResource(R.string.tab_today), activeTab == DashboardTab.TODAY, Modifier.weight(1f)) {
                onSelectTab(DashboardTab.TODAY)
            }
            PageTab(LucideIcons.CALENDAR_DAYS, stringResource(R.string.tab_monthly), activeTab == DashboardTab.MONTHLY, Modifier.weight(1f)) {
                onSelectTab(DashboardTab.MONTHLY)
            }
            if (showCompany) {
                PageTab(LucideIcons.BUILDING, stringResource(R.string.tab_company), selected = false, Modifier.weight(1f), onClick = onOpenCompany)
            }
        }
    }
}

private val Amber600 = Color(0xFFD97706)
private val Amber400 = Color(0xFFFBBF24)

/** The bars' glassy card (Classic) or pd-frame panel (Predator). */
private fun Modifier.pill(t: Tok): Modifier {
    if (t.predator) return framed(t, t.pd.panel, edge = t.pd.edge, cut = 6.dp)
    val shape = RoundedCornerShape(16.dp)
    return shadow(10.dp, shape, clip = false)
        .clip(shape)
        .background(Brush.linearGradient(listOf(t.card, t.well)))
        .border(1.dp, t.line.copy(alpha = 0.6f), shape)
}

@Composable
private fun BarButton(
    icon: Int,
    desc: String,
    active: Boolean = false,
    tint: Color = LocalTok.current.dim,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdChamferShape(4.dp) else RoundedCornerShape(12.dp)
    val bg = when {
        active -> t.accent
        t.predator -> t.pd.raised
        else -> Color.Transparent
    }
    Box(
        Modifier.size(34.dp).clip(shape).background(bg, shape).clickable(onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = t.ink, strokeWidth = 2.dp)
        else Ic(icon, 17.dp, if (active) t.onAccent else tint, desc)
    }
}

@Composable
private fun PageTab(icon: Int, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdParallelogramShape(6.dp) else RoundedCornerShape(12.dp)
    val bg = when {
        selected -> t.accent
        t.predator -> t.pd.raised
        else -> Color.Transparent
    }
    val color = if (selected) t.onAccent else t.dim
    Row(
        modifier.clip(shape).background(bg, shape).clickable(onClick = onClick)
            .padding(horizontal = if (t.predator) 16.dp else 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Ic(icon, 15.dp, color)
        Spacer(Modifier.width(6.dp))
        Text(
            label.uppercase(),
            style = if (t.predator) t.label(13.sp, color) else t.body(12.sp, color, if (selected) FontWeight.Bold else FontWeight.Medium),
            maxLines = 1,
        )
    }
}

// ── Search ──────────────────────────────────────────────────────────────────

private class SearchHit(val icon: Int, val title: String, val detail: String, val open: () -> Unit)

/**
 * The web's search, over what this device has synced: items of the Today and Monthly domains (by name and text),
 * events and tasks. Picking one opens it where it lives, as a widget tap does.
 */
@Composable
internal fun SearchDialog(
    today: WidgetUi,
    monthly: MonthlyUi,
    onItem: (DashboardTab, String) -> Unit,
    onEvent: (EventUi) -> Unit,
    onTask: (TaskUi) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTok.current
    var query by remember { mutableStateOf("") }
    // Domain names come from string resources when the user kept the default
    val sources = searchSources(today, monthly)
    val names = sources.associate { (_, domain) -> domain.domainId to domainName(domain) }
    val hits = remember(query, today, monthly, names) { searchHits(query.trim(), sources, names, today, onItem, onEvent, onTask) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 560.dp)
                .framed(t, t.card, edge = t.line, cut = 8.dp).padding(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().framed(t, t.well, edge = t.line, cut = 4.dp).padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(LucideIcons.SEARCH, 16.dp, t.dim)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = t.body(14.sp, t.faint), maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = t.body(14.sp),
                        cursorBrush = SolidColor(t.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clickable { query = "" }) { Ic(LucideIcons.CLOSE, 16.dp, t.dim) }
                }
            }
            if (query.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                if (hits.isEmpty()) {
                    Text(
                        stringResource(R.string.search_empty),
                        style = t.body(13.sp, t.dim),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn {
                        items(hits) { hit -> SearchRow(hit) { onDismiss(); hit.open() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(hit.icon, 16.dp, t.ink)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = t.body(14.sp, t.text, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hit.detail.isNotBlank()) {
                Text(hit.detail, style = t.caption(10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val MAX_HITS = 50
private val TAGS = Regex("<[^>]+>")

/** Domains whose items are searched: Today first (Integrity and Tenacity show on both pages), then Monthly */
private fun searchSources(today: WidgetUi, monthly: MonthlyUi): List<Pair<DashboardTab, IntegrityUi>> =
    (listOfNotNull(today.integrity) + today.todayDomains + today.hiddenTodayDomains).map { DashboardTab.TODAY to it } +
        (listOfNotNull(monthly.integrity, monthly.trayectory?.domain, monthly.trayectory?.embeddedTenacity) +
            monthly.monthlyDomains + monthly.hiddenMonthlyDomains).map { DashboardTab.MONTHLY to it }

private fun searchHits(
    query: String,
    sources: List<Pair<DashboardTab, IntegrityUi>>,
    names: Map<String, String>,
    today: WidgetUi,
    onItem: (DashboardTab, String) -> Unit,
    onEvent: (EventUi) -> Unit,
    onTask: (TaskUi) -> Unit,
): List<SearchHit> {
    if (query.isEmpty()) return emptyList()
    fun String?.has() = this != null && contains(query, ignoreCase = true)
    val hits = mutableListOf<SearchHit>()
    val seen = mutableSetOf<String>()

    for ((tab, domain) in sources) {
        for (topic in domain.topics) for (item in topic.items) {
            val name = item.name ?: continue
            if (item.id in seen) continue
            if (!name.has() && !item.content?.replace(TAGS, " ").has()) continue
            seen += item.id
            hits += SearchHit(LucideIcons.domain(domain.position), name, detail(names[domain.domainId], topic.name)) { onItem(tab, item.id) }
        }
    }
    for (day in today.eventDays) for (event in day.events) {
        if (!event.title.has()) continue
        hits += SearchHit(LucideIcons.CALENDAR, event.title, listOfNotNull(day.label, event.timeLabel).joinToString(" · ")) { onEvent(event) }
    }
    for (task in today.tasks) {
        val title = task.title ?: continue
        if (!title.has()) continue
        hits += SearchHit(LucideIcons.TASKS, title, listOfNotNull(task.listName, task.dueLabel).joinToString(" · ")) { onTask(task) }
    }
    return hits.take(MAX_HITS)
}

private fun detail(domain: String?, topic: String?) =
    listOfNotNull(domain, topic?.takeIf { it.isNotBlank() }).joinToString(" · ").uppercase()
