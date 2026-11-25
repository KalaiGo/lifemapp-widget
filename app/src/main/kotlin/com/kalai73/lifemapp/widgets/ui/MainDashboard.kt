package com.kalai73.lifemapp.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.core.model.DashboardTab
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.presentation.MonthlyUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.ui.sidepanel.LocalPanel
import com.kalai73.lifemapp.widgets.ui.sidepanel.SidePanelHost
import com.kalai73.lifemapp.widgets.ui.sidepanel.SidePanelViewModel
import com.kalai73.lifemapp.widgets.ui.sidepanel.opener
import com.kalai73.lifemapp.widgets.ui.tags.LocalTags
import com.kalai73.lifemapp.widgets.ui.tags.TagEnvironment
import com.kalai73.lifemapp.widgets.ui.tags.TagFilterDropdown
import com.kalai73.lifemapp.widgets.ui.tags.TagManagerDialog
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch

/**
 * Main dashboard hosting Today, Monthly and, when the user has it, Business (BUSINESS MAPP) in a HorizontalPager,
 * with the web's bottom floating bars over it (navigation overlay, search, settings, voice, reminder;
 * TODAY | MONTHLY | BUSINESS) and cross-tab navigation.
 */
@Composable
fun MainDashboard(
    todayUi: WidgetUi,
    monthlyUi: MonthlyUi,
    businessUi: MonthlyUi,
    todayViewModel: TodayViewModel,
    appViewModel: AppViewModel,
    panelViewModel: SidePanelViewModel,
    onOpenSettings: () -> Unit,
    onOpenWeb: (String) -> Unit,
    openItemId: String? = null,
    openSection: String? = null,
    openEntry: TodayEntry? = null,
) {
    val t = rememberTokens()
    val scope = rememberCoroutineScope()
    val activeTab by todayViewModel.activeTab.collectAsState()
    val isAdmin by todayViewModel.isAdmin.collectAsState()
    // BUSINESS MAPP: a page once the user has business domains (20–29); admins see it before, to set them up
    val hasBusiness = businessUi.hasData && (businessUi.monthlyDomains.isNotEmpty() || businessUi.hiddenMonthlyDomains.isNotEmpty())
    val showBusiness = hasBusiness || isAdmin
    val tabs = if (showBusiness) DASHBOARD_TABS else DASHBOARD_TABS.dropLast(1)
    val pageOf = { tab: DashboardTab -> tabs.indexOf(tab).coerceAtLeast(0) }
    val tabOf = { page: Int -> tabs.getOrElse(page) { DashboardTab.TODAY } }
    val pagerState = rememberPagerState(initialPage = pageOf(activeTab)) { tabs.size }

    // Synchronize activeTab from ViewModel to pager
    LaunchedEffect(activeTab, tabs.size) {
        val targetPage = pageOf(activeTab)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Synchronize pager swipe to activeTab
    LaunchedEffect(pagerState.currentPage) {
        val tab = tabOf(pagerState.currentPage)
        if (activeTab != tab) {
            todayViewModel.selectTab(tab)
        }
    }

    // Cross-tab counter jumps (Monthly -> Today)
    var crossTabSection by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        todayViewModel.crossTabScroll.collect { section ->
            pagerState.animateScrollToPage(0)
            crossTabSection = section
        }
    }

    // The floating Nav button's overlay, on the page shown; a swipe to the other page closes it
    var navOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { navOpen = false }

    // A search pick: the item, for the page it lives on, or the event or task (Today), opened as a widget tap is
    var searchOpen by remember { mutableStateOf(false) }
    var searchItem by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var searchEntry by remember { mutableStateOf<TodayEntry?>(null) }
    val showPage = { page: Int -> scope.launch { pagerState.animateScrollToPage(page) }; Unit }
    val selectTab = { tab: DashboardTab ->
        todayViewModel.selectTab(tab)
        showPage(pageOf(tab))
    }
    val itemFor = { page: Int -> searchItem?.takeIf { it.first == page }?.second ?: openItemId }
    val itemOpened = { page: Int -> if (searchItem?.first == page) searchItem = null else todayViewModel.itemOpened() }

    val currentTab = tabOf(pagerState.currentPage)
    val currentError = if (currentTab == DashboardTab.TODAY) todayUi.error else monthlyUi.error
    val syncing = if (currentTab == DashboardTab.TODAY) todayUi.syncing else monthlyUi.syncing

    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        if (url.startsWith("http", ignoreCase = true)) runCatching { uriHandler.openUri(url) } else onOpenWeb(url)
    }
    val panelOpener = remember(panelViewModel) { panelViewModel.opener() }
    var tagManagerOpen by remember { mutableStateOf(false) }
    var tagManagerOnCreated by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val tagEnv = remember(todayUi.activeTag, todayUi.customTags, todayViewModel) {
        TagEnvironment(
            activeTag = todayUi.activeTag,
            customTags = todayUi.customTags,
            onSelectFilter = todayViewModel::setActiveTag,
            onOpenManager = { onCreated ->
                tagManagerOnCreated = onCreated
                tagManagerOpen = true
            },
            onSaveCustomTags = todayViewModel::saveCustomTags,
            onDeleteCustomTag = todayViewModel::deleteCustomTag,
        )
    }

    CompositionLocalProvider(LocalPanel provides panelOpener, LocalItemEditing provides todayViewModel.itemEditing, LocalTags provides tagEnv) {
    Box(Modifier.fillMaxSize()) {
    // No header: the pages run to the top, reload sits in the floating actions pill
    Column(Modifier.fillMaxSize().background(t.page)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (tabOf(page)) {
                DashboardTab.TODAY -> TodayScreen(
                    ui = todayUi,
                    onOpenSettings = onOpenSettings,
                    onReload = todayViewModel::reload,
                    onCompleteTask = todayViewModel::completeTask,
                    onLoadTaskText = todayViewModel::loadTaskText,
                    onSaveTask = todayViewModel::saveTask,
                    onLoadItemText = todayViewModel::loadItemText,
                    onSaveItem = todayViewModel::saveItem,
                    onDeleteItem = todayViewModel::deleteItem,
                    onMoveItem = todayViewModel::moveItem,
                    onSetItemWarning = todayViewModel::setItemWarning,
                    onLoadItemImage = todayViewModel::loadItemImage,
                    onToggleItem = todayViewModel::setItemCollapsed,
                    onToggleTopic = todayViewModel::setTopicCollapsed,
                    onSaveItemTags = todayViewModel::saveItemTags,
                    onSaveTopicTags = todayViewModel::saveTopicTags,
                    onToggleDomainHidden = todayViewModel::toggleDomainHidden,
                    onSetAllDomainsHidden = todayViewModel::setAllDomainsHidden,
                    onRateDomains = todayViewModel::rateDomains,
                    onUnlock = appViewModel::showUnlock,
                    onOpenWeb = onOpenWeb,
                    openItemId = itemFor(0),
                    onItemOpened = { itemOpened(0) },
                    openSection = crossTabSection ?: openSection,
                    onSectionOpened = {
                        crossTabSection = null
                        todayViewModel.sectionOpened()
                    },
                    openEntry = searchEntry ?: openEntry,
                    onEntryOpened = { if (searchEntry != null) searchEntry = null else todayViewModel.entryOpened() },
                    navOpen = navOpen && page == pagerState.currentPage,
                    onNavOpenChange = { navOpen = it },
                )
                DashboardTab.MONTHLY -> MonthlyScreen(
                    ui = monthlyUi,
                    quickLinks = if (todayUi.shortcutsLocked) emptyList() else todayUi.shortcuts,
                    onOpenSettings = onOpenSettings,
                    onReload = todayViewModel::reload,
                    onLoadItemText = todayViewModel::loadItemText,
                    onSaveItem = todayViewModel::saveItem,
                    onDeleteItem = todayViewModel::deleteItem,
                    onMoveItem = todayViewModel::moveItem,
                    onSetItemWarning = todayViewModel::setItemWarning,
                    onLoadItemImage = todayViewModel::loadItemImage,
                    onToggleItem = todayViewModel::setItemCollapsed,
                    onToggleTopic = todayViewModel::setTopicCollapsed,
                    onSaveItemTags = todayViewModel::saveItemTags,
                    onSaveTopicTags = todayViewModel::saveTopicTags,
                    onToggleDomainHidden = todayViewModel::toggleDomainHidden,
                    onSetAllDomainsHidden = todayViewModel::setAllMonthlyDomainsHidden,
                    onRateDomains = todayViewModel::rateDomains,
                    onUnlock = appViewModel::showUnlock,
                    onOpenWeb = onOpenWeb,
                    onJumpToTodayEvents = {
                        todayViewModel.requestTodaySection("events")
                    },
                    onJumpToTodayTasks = {
                        todayViewModel.requestTodaySection("tasks")
                    },
                    openItemId = itemFor(page),
                    onItemOpened = { itemOpened(page) },
                    openSection = openSection,
                    onSectionOpened = todayViewModel::sectionOpened,
                    navOpen = navOpen && page == pagerState.currentPage,
                    onNavOpenChange = { navOpen = it },
                )
                DashboardTab.BUSINESS -> MonthlyScreen(
                    ui = businessUi,
                    quickLinks = if (todayUi.shortcutsLocked) emptyList() else todayUi.shortcuts,
                    onOpenSettings = onOpenSettings,
                    onReload = todayViewModel::reload,
                    onLoadItemText = todayViewModel::loadItemText,
                    onSaveItem = todayViewModel::saveItem,
                    onDeleteItem = todayViewModel::deleteItem,
                    onMoveItem = todayViewModel::moveItem,
                    onSetItemWarning = todayViewModel::setItemWarning,
                    onLoadItemImage = todayViewModel::loadItemImage,
                    onToggleItem = todayViewModel::setItemCollapsed,
                    onToggleTopic = todayViewModel::setTopicCollapsed,
                    onSaveItemTags = todayViewModel::saveItemTags,
                    onSaveTopicTags = todayViewModel::saveTopicTags,
                    onToggleDomainHidden = todayViewModel::toggleDomainHidden,
                    onSetAllDomainsHidden = todayViewModel::setAllBusinessDomainsHidden,
                    onRateDomains = todayViewModel::rateBusinessDomains,
                    onUnlock = appViewModel::showUnlock,
                    onOpenWeb = onOpenWeb,
                    onJumpToTodayEvents = { todayViewModel.requestTodaySection("events") },
                    onJumpToTodayTasks = { todayViewModel.requestTodaySection("tasks") },
                    openItemId = itemFor(page),
                    onItemOpened = { itemOpened(page) },
                    navOpen = navOpen && page == pagerState.currentPage,
                    onNavOpenChange = { navOpen = it },
                )
            }
        }

        CompositionLocalProvider(LocalTok provides t) {
            if (todayUi.hasData && (currentTab == DashboardTab.TODAY || currentTab == DashboardTab.MONTHLY)) {
                TagFilterDropdown(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 8.dp, start = 12.dp),
                )
            }
            DashboardFloatingBars(
                activeTab = currentTab,
                onSelectTab = selectTab,
                showBusiness = showBusiness,
                navOpen = navOpen,
                onToggleNav = { navOpen = !navOpen },
                searchOpen = searchOpen,
                onSearch = { navOpen = false; searchOpen = !searchOpen },
                onOpenSettings = onOpenSettings,
                syncing = syncing,
                syncError = currentError != null,
                onReload = todayViewModel::reload,
            )
            if (searchOpen) {
                SearchDialog(
                    today = todayUi,
                    monthly = monthlyUi,
                    business = businessUi.takeIf { showBusiness },
                    onItem = { tab, id ->
                        searchItem = pageOf(tab) to id
                        selectTab(tab)
                    },
                    onEvent = { event -> searchEntry = TodayEntry.Event(event.id, event.start); selectTab(DashboardTab.TODAY) },
                    onTask = { task -> searchEntry = TodayEntry.Task(task.id); selectTab(DashboardTab.TODAY) },
                    onDismiss = { searchOpen = false },
                )
            }
        }
        }
    }
    // The side panel (Editor | Links | Alerts | Feeds) over both pages and the floating bars
    CompositionLocalProvider(LocalTok provides t) {
        SidePanelHost(
            vm = panelViewModel,
            shortcuts = todayUi.shortcuts,
            openLink = openLink,
            onOpenWeb = onOpenWeb,
            onUnlock = appViewModel::showUnlock,
        )
        if (tagManagerOpen) {
            TagManagerDialog(
                customTags = todayUi.customTags,
                onCreated = tagManagerOnCreated,
                onSaveCustomTags = todayViewModel::saveCustomTags,
                onDeleteCustomTag = todayViewModel::deleteCustomTag,
                onDismiss = {
                    tagManagerOpen = false
                    tagManagerOnCreated = null
                },
            )
        }
    }
    }
    }
}

/** The dashboard's pages in order; Business is left out when the user has none */
private val DASHBOARD_TABS = listOf(DashboardTab.TODAY, DashboardTab.MONTHLY, DashboardTab.BUSINESS)
