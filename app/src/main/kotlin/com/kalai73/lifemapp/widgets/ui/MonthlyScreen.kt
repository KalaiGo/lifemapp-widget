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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.repository.ItemText
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.model.DashboardTab
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.presentation.MonthlyUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import kotlinx.coroutines.launch

/**
 * Native Monthly screen (docs/migrationPlan.md), matching the web app's monthly page.
 * Displays positions 1 (Integrity), 10 (Trayectory with embedded Tenacity), and 11–19 (Monthly domains).
 * Calendar events, task lists, and workspace shortcuts are omitted on Monthly.
 */
@Composable
fun MonthlyScreen(
    ui: MonthlyUi,
    onOpenSettings: () -> Unit,
    onReload: () -> Unit,
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
    /** Item id and the collapsed state to switch to */
    onToggleItem: (String, Boolean) -> Unit,
    /** Topic id and the collapsed state to switch to */
    onToggleTopic: (String, Boolean) -> Unit,
    /** Scoring section: hide or show one domain on Monthly */
    onToggleDomainHidden: (String) -> Unit,
    /** Scoring section: hide (true) or show every hidable domain (11–19) */
    onSetAllDomainsHidden: (Boolean) -> Unit,
    /** Scoring section: new ratings, domain id → 1–10 */
    onRateDomains: (Map<String, Int>) -> Unit,
    onUnlock: () -> Unit,
    onOpenWeb: (String) -> Unit,
    /** Tapping Events counter jumps to Today tab and scrolls to events */
    onJumpToTodayEvents: () -> Unit,
    /** Tapping Tasks counter jumps to Today tab and scrolls to tasks */
    onJumpToTodayTasks: () -> Unit,
    /** An item a widget/deep tap asked to open in the modal; null for none */
    openItemId: String? = null,
    onItemOpened: () -> Unit = {},
    /** A section key ("integrity", "trayectory", or domain key) to scroll to; null for none */
    openSection: String? = null,
    onSectionOpened: () -> Unit = {},
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
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    val hiddenIds = ui.hiddenMonthlyDomains.map { it.domainId }.toSet()
    LaunchedEffect(hiddenIds) { revealed = revealed intersect hiddenIds }

    val allDomains = (ui.monthlyDomains + ui.hiddenMonthlyDomains).sortedBy { it.position }
    val shownDomains = allDomains.filter { it.domainId !in hiddenIds || it.domainId in revealed }

    val keys = buildList {
        add("greeting"); add("date"); add("counters")
        if (ui.weather != null || (ui.averageRating != null && ui.scoring == null)) add("weather")
        add("actions")
        if (ui.scoring != null) add("scoring")
        if (locked) add("locked")
        if (ui.integrity != null) add("integrity")
        if (ui.trayectory != null) add("trayectory")
        shownDomains.forEach { add(domainKey(it)) }
        add("end")
    }

    val scrollTo = { key: String -> scope.launch { listState.animateScrollToItem(keys.indexOf(key).coerceAtLeast(0)) }; Unit }
    var navOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = navOpen) { navOpen = false }

    var pendingScroll by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingScroll, keys) {
        val key = pendingScroll ?: return@LaunchedEffect
        if (key in keys) { pendingScroll = null; listState.animateScrollToItem(keys.indexOf(key)) }
    }
    val openDomain = { domain: IntegrityUi ->
        if (domain.domainId in hiddenIds) revealed = revealed + domain.domainId
        pendingScroll = domainKey(domain)
    }

    LaunchedEffect(openSection, keys) {
        val section = openSection ?: return@LaunchedEffect
        when {
            section in keys -> {
                navOpen = false
                scrollTo(section)
                onSectionOpened()
            }
            ui.hasData && !ui.syncing -> onSectionOpened()
        }
    }

    var widgetItem by remember { mutableStateOf<TopicItemUi?>(null) }
    LaunchedEffect(openItemId, ui) {
        val id = openItemId ?: return@LaunchedEffect
        val allInspectDomains = listOfNotNull(
            ui.integrity,
            ui.trayectory?.domain,
            ui.trayectory?.embeddedTenacity,
        ) + allDomains
        val domain = allInspectDomains.firstOrNull { d -> d.topics.any { topic -> topic.items.any { it.id == id } } }
        val item = domain?.topics?.firstNotNullOfOrNull { topic -> topic.items.firstOrNull { it.id == id } }
        when {
            item != null -> {
                navOpen = false
                widgetItem = item
                onItemOpened()
                when (domain.domainId) {
                    ui.integrity?.domainId -> scrollTo("integrity")
                    ui.trayectory?.domain?.domainId, ui.trayectory?.embeddedTenacity?.domainId -> scrollTo("trayectory")
                    else -> openDomain(domain)
                }
            }
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

    CompositionLocalProvider(LocalTok provides t) {
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
                                onEvents = onJumpToTodayEvents,
                                onTasks = onJumpToTodayTasks,
                                onMail = { openLink(ui.mailUrl ?: WebRoutes.MAIL) },
                            )
                            "weather" -> WeatherCard(ui) { onOpenWeb(WebRoutes.SETTINGS) }
                            "actions" -> QuickActions(onOpenWeb)
                            "scoring" -> MonthlyScoringSection(
                                scoring = ui.scoring!!,
                                average = ui.averageRating,
                                onToggleHidden = onToggleDomainHidden,
                                onSetAllHidden = onSetAllDomainsHidden,
                                onRate = onRateDomains,
                                onHistory = { onOpenWeb(WebRoutes.MONTHLY) },
                                onUnlock = onUnlock,
                            )
                            "locked" -> LockedBanner(onUnlock)
                            "integrity" -> DomainCard(ui.integrity!!, onOpenWeb, itemHandlers, onToggleTopic)
                            "trayectory" -> TrayectoryDomainCard(ui.trayectory!!, onOpenWeb, itemHandlers, onToggleTopic)
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
                        ui = null,
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
                MonthlyNavOverlay(
                    ui = ui,
                    domains = allDomains,
                    hiddenIds = hiddenIds,
                    onDismiss = { navOpen = false },
                    onTop = { navOpen = false; scope.launch { listState.animateScrollToItem(0) } },
                    onSection = { key -> navOpen = false; scrollTo(key) },
                    onDomain = { domain -> navOpen = false; openDomain(domain) },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.MonthlyNavOverlay(
    ui: MonthlyUi,
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
        ui.trayectory?.domain?.let { trayectory ->
            NavEntry(LucideIcons.domain(trayectory.position), domainName(trayectory), t.ink) { onSection("trayectory") }
        }
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

