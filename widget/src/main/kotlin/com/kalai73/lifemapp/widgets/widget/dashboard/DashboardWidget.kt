package com.kalai73.lifemapp.widgets.widget.dashboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyListScope
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.lazy.itemsIndexed
import com.kalai73.lifemapp.widgets.widget.events.NoCalendarCard
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.presentation.EventDayUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import com.kalai73.lifemapp.widgets.widget.common.DismissFailureAction
import com.kalai73.lifemapp.widgets.widget.common.Icon
import com.kalai73.lifemapp.widgets.widget.common.LocalPd
import com.kalai73.lifemapp.widgets.widget.common.LocalWidgetStyle
import com.kalai73.lifemapp.widgets.widget.common.PdIconWell
import com.kalai73.lifemapp.widgets.widget.common.PdRule
import com.kalai73.lifemapp.widgets.widget.common.ReloadButton
import com.kalai73.lifemapp.widgets.widget.common.StatusLine
import com.kalai73.lifemapp.widgets.widget.common.OpenAppButton
import com.kalai73.lifemapp.widgets.widget.shortcuts.DriveBody
import com.kalai73.lifemapp.widgets.widget.common.VoiceButton
import com.kalai73.lifemapp.widgets.widget.common.WidgetRoot
import com.kalai73.lifemapp.widgets.widget.common.labelStyle
import com.kalai73.lifemapp.widgets.widget.common.liveWidgetUi
import com.kalai73.lifemapp.widgets.widget.common.widgetUi
import com.kalai73.lifemapp.widgets.widget.common.openApp
import com.kalai73.lifemapp.widgets.widget.common.openSection
import com.kalai73.lifemapp.widgets.widget.common.textStyle
import com.kalai73.lifemapp.widgets.widget.events.DayHeader
import com.kalai73.lifemapp.widgets.widget.events.EventRow
import com.kalai73.lifemapp.widgets.widget.events.EventsConfig
import com.kalai73.lifemapp.widgets.widget.integrity.integritySection
import com.kalai73.lifemapp.widgets.widget.shortcuts.MailBody
import com.kalai73.lifemapp.widgets.widget.shortcuts.ShortcutTile
import com.kalai73.lifemapp.widgets.widget.shortcuts.ShortcutRow
import com.kalai73.lifemapp.widgets.widget.shortcuts.LINK_COLUMNS
import com.kalai73.lifemapp.widgets.widget.shortcuts.ShortcutsSubHeader
import com.kalai73.lifemapp.widgets.widget.shortcuts.SUB_HEADER_GAP
import com.kalai73.lifemapp.widgets.widget.common.openExternal
import com.kalai73.lifemapp.widgets.core.presentation.DayKind
import com.kalai73.lifemapp.widgets.widget.shortcuts.TILE_WIDTH
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler
import com.kalai73.lifemapp.widgets.widget.tasks.TaskActions
import com.kalai73.lifemapp.widgets.widget.tasks.TaskDayHeader
import com.kalai73.lifemapp.widgets.widget.tasks.TaskRow
import com.kalai73.lifemapp.widgets.widget.tasks.SeeUpcomingButton
import com.kalai73.lifemapp.widgets.widget.today.Counters
import com.kalai73.lifemapp.widgets.widget.today.HeroFooter
import com.kalai73.lifemapp.widgets.widget.today.HeroHeader
import com.kalai73.lifemapp.widgets.widget.today.stackedHero

/**
 * Everything in one widget: on tall sizes the hero (greeting, date, counters, weather), stacked as on the web's
 * mobile layout, then the Integrity card, events, tasks and shortcuts, all in a single scrolling list; at 4×2 a compact hero alone. Glance can't nest scrolling lists, so the shortcut
 * grid is laid out as rows of tiles inside that list.
 *
 * Per-instance events settings (days ahead, hidden calendars) come from WidgetConfigActivity, see [EventsConfig].
 */
class DashboardWidget : GlanceAppWidget() {
    companion object {
        val HERO_ONLY = DpSize(250.dp, 110.dp) // 4×2: the hero alone
        val MEDIUM = DpSize(250.dp, 250.dp)    // 4×4 (default) and taller, any width
    }

    /**
     * One tall layout only. Android 12+ receives every responsive layout in each update, and the tall one carries
     * the whole list (~300 KB with real data): a second tall layout (e.g. a wider one) pushed updates past the
     * ~500 KB binder limit, so the launcher dropped them (TransactionTooLargeException) and the widget froze,
     * reload spinner included.
     */
    override val sizeMode = SizeMode.Responsive(setOf(HERO_ONLY, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = LifeMappGraph.get(context)
        val initial = graph.widgetUi()
        provideContent {
            val ui = liveWidgetUi(graph, initial)
            val prefs = currentState<Preferences>()
            val daysAhead = prefs[EventsConfig.DaysAhead] ?: EventsConfig.DEFAULT_DAYS
            val hidden = prefs[EventsConfig.HiddenCalendars] ?: emptySet()
            WidgetRoot(ui) { DashboardContent(ui, EventsConfig.filter(ui.eventDays, ui.today, daysAhead, hidden), it) }
        }
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DashboardWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SyncScheduler.schedulePeriodic(context)
        SyncScheduler.syncNow(context)
    }
}

/** Tenacity's place among the life domains (1 Integrity … 5 Tenacity) */
private const val TENACITY_POSITION = 5

/** Below this height there's no room for the list: the hero only. */
private val LIST_MIN_HEIGHT = 200.dp
/** From this width the compact hero shows the weather location and "Updated 10:42" (the tall one always does). */
private val LOCATION_MIN_WIDTH = 310.dp

/** Root padding on each side (LifeMappWidgetTheme), subtracted when fitting shortcut tiles. */
private val ROOT_PADDING = 12.dp

@Composable
internal fun DashboardContent(ui: WidgetUi, days: List<EventDayUi>, modifier: GlanceModifier = GlanceModifier) {
    val size = LocalSize.current
    val showStatus = size.width >= LOCATION_MIN_WIDTH || ui.stale || ui.error != null
    val predator = LocalPd.current != null
    if (size.height < LIST_MIN_HEIGHT) {
        CompactHero(ui, showStatus, modifier)
        return
    }
    // Tall: the hero stacked as on the web's mobile layout, scrolling with the list. Lazy items are separate
    // view trees, so their frames don't count against the root's 10 nesting levels.
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            // "Updated 10:42" always: the date block has room for it at any width
            stackedHero(ui, showStatus = true)
            ui.integrity?.let { integritySection(it) }
            // Today only: no upcoming days of events, no Tomorrow / Tomorrow ++ 1 tasks (the app shows those)
            eventsSection(ui, days.filter { it.kind == DayKind.TODAY })
            tasksSection(ui.copy(tasks = ui.tasks.filterNot { it.upcoming }))
            shortcutsSection(ui, columns = shortcutColumns(size.width, rows = predator))
            // Tenacity (life domain 5) closes the list, unless it's hidden on the app's Today screen
            ui.todayDomains.firstOrNull { it.position == TENACITY_POSITION }?.let { integritySection(it) }
        }
        HeroFooter(ui)
    }
}

/** 4×2: greeting, date, weather and rating in one row with reload and the mic top right, then the counters. */
@Composable
private fun CompactHero(ui: WidgetUi, showStatus: Boolean, modifier: GlanceModifier) {
    val size = LocalSize.current
    // The hero is composed straight into this Column (no extra wrapper) and [modifier] pads this Column rather
    // than a Box around it: RemoteViews allow 10 nested levels and the hero frames use them all.
    Column(modifier = modifier.fillMaxSize()) {
        HeroHeader(ui, showLocation = size.width >= LOCATION_MIN_WIDTH) {
            Spacer(GlanceModifier.width(4.dp))
            ReloadButton(ui)
            Spacer(GlanceModifier.width(4.dp))
            VoiceButton()
            Spacer(GlanceModifier.width(4.dp))
            OpenAppButton()
        }
        Spacer(GlanceModifier.height(6.dp))
        Counters(ui)
        Spacer(GlanceModifier.defaultWeight())
        if (showStatus) {
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End) { StatusLine(ui) }
        }
    }
}

/** Tiles (Classic) fit up to 8 per line; numbered link rows (Predator) always two, like the Mail tags. */
private fun shortcutColumns(width: Dp, rows: Boolean): Int {
    if (rows) return LINK_COLUMNS
    val available = width.value - 2 * ROOT_PADDING.value
    return (available / TILE_WIDTH.value).toInt().coerceIn(1, 8)
}

// Item ids: one range per section, so ids never collide across sections.
private const val ID_EVENTS = -1L
private const val ID_TASKS = -2L
private const val ID_SHORTCUTS = -3L
private const val ID_EVENTS_EMPTY = -10L
private const val ID_TASKS_FAILED = -11L
private const val ID_TASKS_LOCKED = -12L
private const val ID_TASKS_EMPTY = -13L
private const val ID_SHORTCUTS_EMPTY = -14L
private const val ID_TASKS_ACTIONS = -15L
private const val ID_TASKS_DAY = -16L
// The Tomorrow and Tomorrow ++ 1 dividers are ID_TASKS_DAY - 100 and - 200
private const val ID_TASKS_TODAY_EMPTY = -20L
private const val ID_EVENTS_MORE = -21L
private const val ID_TASKS_MORE = -22L
private const val ID_MAIL = -17L
private const val ID_MAIL_BODY = -18L
private const val ID_LINKS = -19L
private const val ID_DRIVE = -40L
private const val ID_DRIVE_BODY = -41L
private const val ID_DAY_BASE = 1_000_000_000L
private const val ID_SHORTCUT_ROW_BASE = 2_000_000_000L

private fun LazyListScope.eventsSection(ui: WidgetUi, days: List<EventDayUi>) {
    item(itemId = ID_EVENTS) {
        val context = LocalContext.current
        SectionHeader(context.getString(R.string.events), LucideIcons.CALENDAR, openSection(context, LifeMappWidgets.SECTION_EVENTS), centered = true)
    }
    when {
        !ui.calendarConnected -> item(itemId = ID_EVENTS_EMPTY) {
            NoCalendarCard(openSection(LocalContext.current, LifeMappWidgets.SECTION_EVENTS))
        }
        // Only today is listed, so an empty list means nothing today
        days.none { it.events.isNotEmpty() } -> item(itemId = ID_EVENTS_EMPTY) {
            val context = LocalContext.current
            EmptyLine(context.getString(R.string.no_events_today), openSection(context, LifeMappWidgets.SECTION_EVENTS))
        }
        // Events are numbered across days (1, 2, 3 today, 4, 5 tomorrow), as on the web
        else -> days.runningFold(0) { count, day -> count + day.events.size }.zip(days).forEach { (offset, day) ->
            val dayId = ID_DAY_BASE + day.date.toEpochDay() * 2
            item(itemId = dayId) { DayHeader(day) }
            if (day.events.isEmpty()) {
                item(itemId = dayId + 1) {
                    val context = LocalContext.current
                    EmptyLine(context.getString(R.string.no_events_today), openSection(context, LifeMappWidgets.SECTION_EVENTS))
                }
            }
            // Same ids as the Events widget: epochDay × 1_000_003 puts them around 2·10¹⁰, clear of the day
            // header (~10⁹), shortcut row (~2·10⁹) and task (~2⁴⁰) ranges
            itemsIndexed(day.events, itemId = { _, event -> (day.date.toEpochDay() * 1_000_003L) xor event.id.hashCode().toLong() }) { index, event ->
                EventRow(event, number = offset + index + 1, day = day)
            }
        }
    }
    if (ui.calendarConnected) {
        item(itemId = ID_EVENTS_MORE) {
            SeeUpcomingButton(LocalContext.current.getString(R.string.see_upcoming_events), LifeMappWidgets.SECTION_EVENTS)
        }
    }
}

private fun LazyListScope.tasksSection(ui: WidgetUi) {
    item(itemId = ID_TASKS) {
        val context = LocalContext.current
        SectionHeader(context.getString(R.string.tasks), LucideIcons.TASKS, openSection(context, LifeMappWidgets.SECTION_TASKS), centered = true)
    }
    item(itemId = ID_TASKS_ACTIONS) { TaskActions() }
    if (ui.completionFailed) {
        item(itemId = ID_TASKS_FAILED) {
            val context = LocalContext.current
            Text(
                context.getString(R.string.completion_failed),
                style = textStyle(size = 11.sp, color = GlanceTheme.colors.error),
                modifier = GlanceModifier.fillMaxWidth().clickable(actionRunCallback<DismissFailureAction>()).padding(bottom = 4.dp),
            )
        }
    }
    if (ui.encryption == EncryptionState.LOCKED && ui.tasks.any { it.title == null }) {
        item(itemId = ID_TASKS_LOCKED) {
            val context = LocalContext.current
            Text(
                context.getString(R.string.locked_hint),
                style = textStyle(size = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 2,
                modifier = GlanceModifier.fillMaxWidth().clickable(openApp(context)).padding(bottom = 4.dp),
            )
        }
    }
    if (ui.tasks.isEmpty()) {
        item(itemId = ID_TASKS_EMPTY) {
            val context = LocalContext.current
            EmptyLine(context.getString(R.string.no_tasks), openSection(context, LifeMappWidgets.SECTION_TASKS))
        }
    } else {
        // Segmented as on the web's Task List Widget: TODAY, TOMORROW, TOMORROW ++ 1, numbered across them;
        // Today always gets its divider, saying so when it has no tasks
        if (ui.tasks.first().day != DayKind.TODAY) {
            item(itemId = ID_TASKS_DAY) { TaskDayHeader(DayKind.TODAY) }
            item(itemId = ID_TASKS_TODAY_EMPTY) {
                val context = LocalContext.current
                EmptyLine(context.getString(R.string.no_tasks), openSection(context, LifeMappWidgets.SECTION_TASKS))
            }
        }
        ui.tasks.forEachIndexed { index, task ->
            if (index == 0 || ui.tasks[index - 1].day != task.day) {
                item(itemId = ID_TASKS_DAY - task.day.ordinal * 100L) { TaskDayHeader(task.day) }
            }
            // Offset so a task can't share an id with an event (both are string hashes)
            item(itemId = task.id.hashCode().toLong() + (1L shl 40)) { TaskRow(task, number = index + 1) }
        }
    }
    item(itemId = ID_TASKS_MORE) {
        SeeUpcomingButton(LocalContext.current.getString(R.string.see_upcoming_tasks), LifeMappWidgets.SECTION_TASKS)
    }
}

private fun LazyListScope.shortcutsSection(ui: WidgetUi, columns: Int) {
    item(itemId = ID_SHORTCUTS) {
        val context = LocalContext.current
        SectionHeader(context.getString(R.string.shortcuts), LucideIcons.ZAP, openSection(context, LifeMappWidgets.SECTION_SHORTCUTS), centered = true)
    }
    item(itemId = ID_MAIL) {
        val context = LocalContext.current
        ShortcutsSubHeader(LucideIcons.MAIL, context.getString(R.string.mail), ui.mailUrl?.let(::openExternal) ?: openSection(context, LifeMappWidgets.SECTION_SHORTCUTS))
    }
    item(itemId = ID_MAIL_BODY) { MailBody(ui) }
    if (ui.driveLinks.isNotEmpty()) {
        item(itemId = ID_DRIVE) {
            val context = LocalContext.current
            ShortcutsSubHeader(LucideIcons.FOLDER, context.getString(R.string.drive), openExternal(ui.driveLinks.first().url), top = SUB_HEADER_GAP)
        }
        item(itemId = ID_DRIVE_BODY) { DriveBody(ui) }
    }
    item(itemId = ID_LINKS) {
        val context = LocalContext.current
        ShortcutsSubHeader(
            LucideIcons.LINK, context.getString(R.string.links), openSection(context, LifeMappWidgets.SECTION_SHORTCUTS),
            count = ui.shortcuts.size.takeUnless { ui.shortcutsLocked },
            onAdd = openSection(context, LifeMappWidgets.SECTION_SHORTCUTS).takeUnless { ui.shortcutsLocked },
            top = SUB_HEADER_GAP,
            chevron = true,
        )
    }
    when {
        ui.shortcutsLocked -> item(itemId = ID_SHORTCUTS_EMPTY) {
            val context = LocalContext.current
            EmptyLine(context.getString(R.string.shortcuts_locked), openApp(context))
        }
        ui.shortcuts.isEmpty() -> item(itemId = ID_SHORTCUTS_EMPTY) {
            val context = LocalContext.current
            EmptyLine(context.getString(R.string.no_shortcuts), openSection(context, LifeMappWidgets.SECTION_SHORTCUTS))
        }
        else -> ui.shortcuts.chunked(columns).forEachIndexed { index, row ->
            item(itemId = ID_SHORTCUT_ROW_BASE + index) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    row.forEachIndexed { i, shortcut ->
                        // Predator: numbered rows; Classic: icon tiles
                        if (LocalPd.current != null) ShortcutRow(shortcut, number = index * columns + i + 1, modifier = GlanceModifier.defaultWeight())
                        else ShortcutTile(shortcut, GlanceModifier.defaultWeight())
                    }
                    // Keep the last row's tiles the same width as the full rows above
                    repeat(columns - row.size) { Spacer(GlanceModifier.defaultWeight()) }
                }
            }
        }
    }
}

/** A section title inside the list: lighter than WidgetHeader (no reload button, which sits in the hero). */
@Composable
private fun SectionHeader(
    title: String,
    icon: Int,
    onClick: Action,
    subtitle: String? = null,
    centered: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    if (centered) {
        // Events: calendar icon and title centered, no rule (the day dividers below carry the lines)
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(onClick).padding(top = 32.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, size = 14.dp, tint = pd?.ink ?: GlanceTheme.colors.primary)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                style.label(title),
                style = if (pd != null) labelStyle(size = 15.sp, color = pd.ink)
                else textStyle(size = 14.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                maxLines = 1,
            )
        }
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = GlanceModifier.defaultWeight().clickable(onClick), verticalAlignment = Alignment.CenterVertically) {
                if (pd != null) {
                    PdIconWell(icon, null, width = 22.dp, iconSize = 11.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(style.label(title), style = labelStyle(size = 13.sp), maxLines = 1)
                } else {
                    Icon(icon, null, size = 16.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(style.label(title), style = textStyle(size = 13.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary), maxLines = 1)
                }
                if (subtitle != null) {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        if (pd != null) style.label(subtitle) else subtitle,
                        style = textStyle(size = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                    )
                }
            }
            trailing()
        }
        if (pd != null) {
            Spacer(GlanceModifier.height(3.dp))
            PdRule()
        }
    }
}

@Composable
private fun EmptyLine(text: String, action: Action) {
    Text(
        text,
        style = textStyle(size = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
        maxLines = 2,
        modifier = GlanceModifier.fillMaxWidth().clickable(action).padding(vertical = 4.dp),
    )
}
