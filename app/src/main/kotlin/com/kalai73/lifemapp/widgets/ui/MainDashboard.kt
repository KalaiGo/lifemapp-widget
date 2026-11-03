package com.kalai73.lifemapp.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch

/**
 * Main dashboard hosting Today (tab 0) and Monthly (tab 1) in a HorizontalPager,
 * with synchronized top-bar segmented toggle [ TODAY | MONTHLY ] and cross-tab navigation.
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

    val currentTab = if (pagerState.currentPage == 0) DashboardTab.TODAY else DashboardTab.MONTHLY
    val currentError = if (pagerState.currentPage == 0) todayUi.error else monthlyUi.error

    Column(Modifier.fillMaxSize().background(t.page)) {
        // Sticky Top Bar matching web home-header.tsx with [ TODAY | MONTHLY ] segmented toggle
        DashboardTopBar(
            activeTab = currentTab,
            onSelectTab = { tab ->
                todayViewModel.selectTab(tab)
                scope.launch {
                    pagerState.animateScrollToPage(if (tab == DashboardTab.TODAY) 0 else 1)
                }
            },
            error = currentError,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
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
                    openItemId = openItemId,
                    onItemOpened = todayViewModel::itemOpened,
                    openSection = crossTabSection ?: openSection,
                    onSectionOpened = {
                        crossTabSection = null
                        todayViewModel.sectionOpened()
                    },
                    openEntry = openEntry,
                    onEntryOpened = todayViewModel::entryOpened,
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
                    openItemId = openItemId,
                    onItemOpened = todayViewModel::itemOpened,
                    openSection = openSection,
                    onSectionOpened = todayViewModel::sectionOpened,
                )
            }
        }
    }
}

@Composable
internal fun DashboardTopBar(
    activeTab: DashboardTab,
    onSelectTab: (DashboardTab) -> Unit,
    error: SyncError? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalTok.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "LIFE MAPP",
                style = t.label(16.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            StatusLine(error)
        }
        DashboardTabToggle(
            activeTab = activeTab,
            onSelectTab = onSelectTab,
        )
        Spacer(Modifier.weight(1f))
    }
}

