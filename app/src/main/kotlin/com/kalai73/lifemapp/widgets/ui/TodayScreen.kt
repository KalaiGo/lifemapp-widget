package com.kalai73.lifemapp.widgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.kalai73.lifemapp.widgets.core.model.DashboardTab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.presentation.EventUi
import com.kalai73.lifemapp.widgets.core.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.presentation.TaskUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.data.repository.TaskText
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import kotlinx.coroutines.launch

/**
 * Native Today screen (docs/todayScreenPlan.md), drawn in the widgets' look and feel: in Predator the framed hero
 * (greeting, date, counters, weather), numbered hex rows with color bars, the score gauge and the amber warnings
 * bar; in Classic the rounded flat variants. It renders the same [WidgetUi] the widgets use.
 *
 * This file holds the screen's layout, widget-tap handling and navigation overlay. The parts live beside it:
 * TodayTheme.kt (style tokens and shared drawing helpers), TodayHero.kt (top bar and hero), TodayEventsTasks.kt,
 * TodayShortcuts.kt and TodayDomains.kt (the domain cards).
 */
@Composable
fun TodayScreen(
    ui: WidgetUi,
    onOpenSettings: () -> Unit,
    onReload: () -> Unit,
    onCompleteTask: (String) -> Unit,
    /** A LIFE MAPP task's whole title and description for the task dialog's editor */
    onLoadTaskText: suspend (String) -> TaskText,
    /** Task id, new title and new HTML description (null keeps either) */
    onSaveTask: (String, String?, String?) -> Unit,
    /** An item's whole text for the edit dialog */
    onLoadItemText: suspend (String) -> ItemText,
    /** Item id, new name and new HTML text (null keeps either) */
    onSaveItem: (String, String?, String?) -> Unit,
    onDeleteItem: (String) -> Unit,
    /** Item id, its topic id and whether up */
    onMoveItem: (String, String, Boolean) -> Unit,
    /** Item id and whether it gets the warning icon */
    onSetItemWarning: (String, Boolean) -> Unit,
    /** An image item's picture */
    onLoadItemImage: suspend (String) -> ItemImage,
    /** Item id and the collapsed state to switch to (image items expand to show their picture) */
    onToggleItem: (String, Boolean) -> Unit,
    /** Topic id and the collapsed state to switch to */
    onToggleTopic: (String, Boolean) -> Unit,
    /** Scoring section: hide or show one domain on Today */
    onToggleDomainHidden: (String) -> Unit,
    /** Scoring section: hide (true) or show every hidable domain */
    onSetAllDomainsHidden: (Boolean) -> Unit,
    /** Scoring section: new ratings, domain id → 1–10 */
    onRateDomains: (Map<String, Int>) -> Unit,
    onUnlock: () -> Unit,
    onOpenWeb: (String) -> Unit,
    /** An item (subsubdomain) a widget tap asked to open in the item modal; null for none */
    openItemId: String? = null,
    /** The [openItemId] request was handled (shown, or not on Today) */
    onItemOpened: () -> Unit = {},
    /** A section key ("events", "tasks", "shortcuts", "integrity") a widget tap asked to scroll to; null for none */
    openSection: String? = null,
    /** The [openSection] request was handled */
    onSectionOpened: () -> Unit = {},
    /** An event or task a widget tap asked to show in its details modal; null for none */
    openEntry: TodayEntry? = null,
    /** The [openEntry] request was handled (shown, or not on Today) */
    onEntryOpened: () -> Unit = {},
    showBottomBar: Boolean = true,
    activeTab: DashboardTab? = null,
    onSelectTab: ((DashboardTab) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    val t = rememberTokens()
    val itemHandlers = ItemHandlers(onLoadItemText, onSaveItem, onDeleteItem, onMoveItem, onSetItemWarning, onLoadItemImage, onToggleItem)

    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        if (url.startsWith("http", ignoreCase = true)) runCatching { uriHandler.openUri(url) } else onOpenWeb(url)
    }
    val locked = ui.encryption == EncryptionState.LOCKED
    // Hidden domains tapped in the navigation overlay: their cards show on this screen while their hidden status
    // stays. One that gets shown (scoring section) leaves the set, so hiding it again hides its card too
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    val hiddenIds = ui.hiddenTodayDomains.map { it.domainId }.toSet()
    LaunchedEffect(hiddenIds) { revealed = revealed intersect hiddenIds }
    val allDomains = (ui.todayDomains + ui.hiddenTodayDomains).sortedBy { it.position }
    val shownDomains = allDomains.filter { it.domainId !in hiddenIds || it.domainId in revealed }
    val keys = buildList {
        add("greeting"); add("date"); add("counters")
        // The weather card carries the average only while there's no scoring section to show it
        if (ui.weather != null || (ui.averageRating != null && ui.scoring == null)) add("weather")
        add("actions")
        // The web's scoring circles, below the hero
        if (ui.scoring != null) add("scoring")
        if (locked) add("locked")
        if (ui.integrity != null) add("integrity")
        add("events"); add("tasks"); add("shortcuts")
        // Serenity, Vitality, Stability, Tenacity follow the Integrity card's widgets, as on the web's Today page
        shownDomains.forEach { add(domainKey(it)) }
        add("end")
    }
    val scrollTo = { key: String -> scope.launch { listState.animateScrollToItem(keys.indexOf(key).coerceAtLeast(0)) }; Unit }
    var navOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = navOpen) { navOpen = false }

    // A revealed card joins the list on the next composition: scroll to it once its key is there
    var pendingScroll by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingScroll, keys) {
        val key = pendingScroll ?: return@LaunchedEffect
        if (key in keys) { pendingScroll = null; listState.animateScrollToItem(keys.indexOf(key)) }
    }
    val openDomain = { domain: IntegrityUi ->
        if (domain.domainId in hiddenIds) revealed = revealed + domain.domainId
        pendingScroll = domainKey(domain)
    }

    // A widget section tap: scroll there once Today has it (Integrity only while the user has one)
    LaunchedEffect(openSection, keys) {
        val section = openSection ?: return@LaunchedEffect
        when {
            section in keys -> {
                navOpen = false
                // In the screen's scope: clearing the request restarts (cancels) this effect
                scrollTo(section)
                onSectionOpened()
            }
            ui.hasData && !ui.syncing -> onSectionOpened()
        }
    }

    // A widget item tap: scroll to the item's domain card and open the item in the modal, once Today has the item
    var widgetItem by remember { mutableStateOf<TopicItemUi?>(null) }
    LaunchedEffect(openItemId, ui) {
        val id = openItemId ?: return@LaunchedEffect
        val domains = listOfNotNull(ui.integrity) + allDomains
        val domain = domains.firstOrNull { d -> d.topics.any { topic -> topic.items.any { it.id == id } } }
        val item = domain?.topics?.firstNotNullOfOrNull { topic -> topic.items.firstOrNull { it.id == id } }
        when {
            item != null -> {
                navOpen = false
                widgetItem = item
                onItemOpened()
                // An item of a hidden domain reveals its card, as the navigation overlay does
                if (domain === ui.integrity) scrollTo("integrity") else openDomain(domain)
            }
            // Loaded and the item isn't there (removed since the widget drew it): Today alone
            ui.hasData && !ui.syncing -> onItemOpened()
        }
    }
    widgetItem?.let { item ->
        val close = { widgetItem = null }
        ItemEditorDialog(
            item,
            focusTitle = false,
            loadText = itemHandlers.loadText,
            onSave = { name, html -> itemHandlers.save(item.id, name, html); close() },
            onDelete = { itemHandlers.delete(item.id); close() },
            onDismiss = close,
            autoFocus = false,
        )
    }

    // The event or task shown in its details modal, as on the web's events card and task list. Kept by id, so
    // the modal follows syncs and closes when the entry goes (a completed task, an event removed since)
    var shownEntry by remember { mutableStateOf<TodayEntry?>(null) }
    val shownEvent = (shownEntry as? TodayEntry.Event)?.let { findEvent(ui, it) }
    val shownTask = (shownEntry as? TodayEntry.Task)?.let { e -> ui.tasks.firstOrNull { it.id == e.id } }
    if (shownEntry != null && shownEvent == null && shownTask == null) SideEffect { shownEntry = null }
    val showEvent: (EventUi) -> Unit = { shownEntry = TodayEntry.Event(it.id, it.start) }
    val showTask: (TaskUi) -> Unit = { shownEntry = TodayEntry.Task(it.id) }

    // A widget event or task tap: scroll to its section and open it in the modal, once Today has it
    LaunchedEffect(openEntry, ui) {
        val entry = openEntry ?: return@LaunchedEffect
        val found = when (entry) {
            is TodayEntry.Event -> findEvent(ui, entry) != null
            is TodayEntry.Task -> ui.tasks.any { it.id == entry.id }
        }
        when {
            found -> {
                navOpen = false
                shownEntry = entry
                onEntryOpened()
                scrollTo(if (entry is TodayEntry.Event) "events" else "tasks")
            }
            // Loaded and the entry isn't there (done or removed since the widget drew it): Today alone
            ui.hasData && !ui.syncing -> onEntryOpened()
        }
    }

    CompositionLocalProvider(LocalTok provides t) {
      shownEvent?.let { EventDetailsDialog(it, openLink) { shownEntry = null } }
      shownTask?.let { task ->
          TaskDetailsDialog(
              task, onCompleteTask,
              loadText = onLoadTaskText,
              onSave = { title, html -> onSaveTask(task.id, title, html) },
          ) { shownEntry = null }
      }
      Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(t.page)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            ) {
                items(keys, key = { it }) { key ->
                    when (key) {
                        "greeting" -> GreetingBlock(ui)
                        "date" -> DateBlock(ui)
                        "counters" -> Counters(
                            ui,
                            onEvents = { scrollTo("events") },
                            onTasks = { scrollTo("tasks") },
                            onMail = { openLink(ui.mailUrl ?: WebRoutes.MAIL) },
                        )
                        "weather" -> WeatherCard(ui) { onOpenWeb(WebRoutes.SETTINGS) }
                        "actions" -> QuickActions(onOpenWeb)
                        "scoring" -> ScoringSection(
                            ui.scoring!!, ui.averageRating,
                            onToggleHidden = onToggleDomainHidden,
                            onSetAllHidden = onSetAllDomainsHidden,
                            onRate = onRateDomains,
                            // The score history panel lives on the web's Today page
                            onHistory = { onOpenWeb(WebRoutes.TODAY) },
                            onUnlock = onUnlock,
                        )
                        "locked" -> LockedBanner(onUnlock)
                        "integrity" -> DomainCard(ui.integrity!!, onOpenWeb, itemHandlers, onToggleTopic)
                        "events" -> EventsSection(ui, showEvent, onOpenWeb)
                        "tasks" -> TasksSection(ui, onCompleteTask, showTask, onOpenWeb)
                        "shortcuts" -> ShortcutsSection(ui, openLink, onOpenWeb, onUnlock)
                        "end" -> Spacer(Modifier.height(28.dp))
                        else -> shownDomains.firstOrNull { domainKey(it) == key }?.let {
                            DomainCard(it, onOpenWeb, itemHandlers, onToggleTopic)
                        }
                    }
                }
            }

            if (showBottomBar) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
                TopBar(
                    ui = ui,
                    error = ui.error,
                    syncing = ui.syncing,
                    onOpenSettings = onOpenSettings,
                    onReload = onReload,
                    navOpen = navOpen,
                    onToggleNav = { navOpen = !navOpen },
                    activeTab = activeTab,
                    onSelectTab = onSelectTab,
                )
            }
        }
        if (navOpen) {
            NavOverlay(
                ui, allDomains, hiddenIds,
                onDismiss = { navOpen = false },
                onTop = { navOpen = false; scope.launch { listState.animateScrollToItem(0) } },
                onSection = { key -> navOpen = false; scrollTo(key) },
                onDomain = { domain -> navOpen = false; openDomain(domain) },
            )
        }
      }
    }
}

/** The event [entry] names: the instance with its start, else any with its id (the start may have moved) */
private fun findEvent(ui: WidgetUi, entry: TodayEntry.Event): EventUi? {
    val events = ui.eventDays.flatMap { it.events }
    return events.firstOrNull { it.id == entry.id && (entry.start == null || it.start == entry.start) }
        ?: events.firstOrNull { it.id == entry.id }
}

// ── Navigation overlay ──────────────────────────────────────────────────────

/**
 * The web's DomainFloatingNav on Today: a scrim and a small centered list above the footer with "Top", the
 * Integrity domain and its sections (events, tasks, shortcuts), then every other Today domain, hidden ones too
 * (dimmed). Tapping an entry scrolls the list to it; a hidden domain's card is revealed first.
 */
@Composable
private fun BoxScope.NavOverlay(
    ui: WidgetUi,
    domains: List<IntegrityUi>,
    hiddenIds: Set<String>,
    onDismiss: () -> Unit,
    onTop: () -> Unit,
    onSection: (String) -> Unit,
    onDomain: (IntegrityUi) -> Unit,
) {
    val t = LocalTok.current
    Box(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    Column(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).width(200.dp)
            .framed(t, t.card, edge = t.line, cut = 8.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        NavEntry(LucideIcons.ARROW_UP, stringResource(R.string.today_nav_top), t.dim, onTop)
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(t.line))
        ui.integrity?.let { integrity ->
            NavEntry(LucideIcons.domain(integrity.position), domainName(integrity), t.ink) { onSection("integrity") }
        }
        NavEntry(LucideIcons.CALENDAR, stringResource(R.string.today_events), t.dim) { onSection("events") }
        NavEntry(LucideIcons.TASKS, stringResource(R.string.today_tasks), t.dim) { onSection("tasks") }
        NavEntry(LucideIcons.ZAP, stringResource(R.string.today_shortcuts), t.dim) { onSection("shortcuts") }
        domains.forEach { domain ->
            val hidden = domain.domainId in hiddenIds
            NavEntry(LucideIcons.domain(domain.position), domainName(domain), if (hidden) t.dim else t.ink) { onDomain(domain) }
        }
    }
}

@Composable
private fun NavEntry(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, 16.dp, color)
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            style = if (t.predator) t.label(13.sp, color) else t.body(13.sp, color, FontWeight.Medium),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
