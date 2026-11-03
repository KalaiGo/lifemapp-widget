package com.kalai73.lifemapp.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import kotlinx.coroutines.launch

/**
 * Main dashboard hosting Today (tab 0) and Monthly (tab 1) in a HorizontalPager, with the web's bottom floating
 * bars over it (navigation overlay, search, settings, voice, reminder; TODAY | MONTHLY | COMPANY) and cross-tab
 * navigation.
 */
@Composable
fun MainDashboard(
    todayUi: WidgetUi,
    monthlyUi: MonthlyUi,
    todayViewModel: TodayViewModel,
    appViewModel: AppViewModel,
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
    val pagerState = rememberPagerState(initialPage = if (activeTab == DashboardTab.MONTHLY) 1 else 0) { 2 }

    // Synchronize activeTab from ViewModel to pager
    LaunchedEffect(activeTab) {
        val targetPage = if (activeTab == DashboardTab.TODAY) 0 else 1
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Synchronize pager swipe to activeTab
    LaunchedEffect(pagerState.currentPage) {
        val tab = if (pagerState.currentPage == 0) DashboardTab.TODAY else DashboardTab.MONTHLY
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
        showPage(if (tab == DashboardTab.TODAY) 0 else 1)
    }
    val itemFor = { page: Int -> searchItem?.takeIf { it.first == page }?.second ?: openItemId }
    val itemOpened = { page: Int -> if (searchItem?.first == page) searchItem = null else todayViewModel.itemOpened() }

    val currentTab = if (pagerState.currentPage == 0) DashboardTab.TODAY else DashboardTab.MONTHLY
    val currentError = if (pagerState.currentPage == 0) todayUi.error else monthlyUi.error
    val syncing = if (pagerState.currentPage == 0) todayUi.syncing else monthlyUi.syncing

    // No header: the pages run to the top, reload sits in the floating actions pill
    Column(Modifier.fillMaxSize().background(t.page)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> TodayScreen(
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
                1 -> MonthlyScreen(
                    ui = monthlyUi,
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
                    openItemId = itemFor(1),
                    onItemOpened = { itemOpened(1) },
                    openSection = openSection,
                    onSectionOpened = todayViewModel::sectionOpened,
                    navOpen = navOpen && page == pagerState.currentPage,
                    onNavOpenChange = { navOpen = it },
                )
            }
        }

        CompositionLocalProvider(LocalTok provides t) {
            DashboardFloatingBars(
                activeTab = currentTab,
                onSelectTab = selectTab,
                showCompany = isAdmin,
                onOpenCompany = { onOpenWeb(WebRoutes.COMPANY) },
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
                    onItem = { tab, id ->
                        val page = if (tab == DashboardTab.TODAY) 0 else 1
                        searchItem = page to id
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
}
