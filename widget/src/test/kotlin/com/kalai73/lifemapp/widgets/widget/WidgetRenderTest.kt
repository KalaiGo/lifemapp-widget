package com.kalai73.lifemapp.widgets.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import com.kalai73.lifemapp.widgets.core.data.presentation.DayKind
import com.kalai73.lifemapp.widgets.core.data.presentation.EventDayUi
import com.kalai73.lifemapp.widgets.core.data.presentation.EventUi
import com.kalai73.lifemapp.widgets.core.data.presentation.ShortcutUi
import com.kalai73.lifemapp.widgets.core.data.presentation.TaskUi
import com.kalai73.lifemapp.widgets.core.data.presentation.WeatherUi
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import com.kalai73.lifemapp.widgets.widget.common.CompleteTaskAction
import com.kalai73.lifemapp.widgets.widget.common.WidgetRoot
import com.kalai73.lifemapp.widgets.widget.dashboard.DashboardContent
import com.kalai73.lifemapp.widgets.widget.events.EventsConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The Dashboard widget in its sizes and states: populated, empty, locked, signed out, stale/error. */
@RunWith(RobolectricTestRunner::class)
class WidgetRenderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 10, 31)

    private val populated = WidgetUi.SignedOut.copy(
        uiStyle = UiStyle.CLASSIC,
        authState = AuthState.SIGNED_IN,
        hasData = true,
        username = "Ana",
        today = today,
        todayLabel = "Sa. 31/10/2026",
        eventsCount = 2,
        tasksCount = 5,
        mailCount = 12,
        mailConnected = true,
        calendarConnected = true,
        averageRating = 7.4,
        weather = WeatherUi("18°C", "9°C", "Partially cloudy", "partly-cloudy-day", "Madrid, ES"),
        updatedAt = "10:42",
        eventDays = listOf(
            EventDayUi(today, DayKind.TODAY, "Sa. 31/10/2026", listOf(
                EventUi("e1", "Standup", "09:00 – 09:15", false, "#22c55e", "Work", false, null),
            )),
            EventDayUi(today.plusDays(1), DayKind.TOMORROW, "Su. 01/11/2026", listOf(
                EventUi("e2", "Family lunch", null, true, "#f97316", "Home", false, "https://calendar.google.com/e2"),
            )),
            EventDayUi(today.plusDays(5), DayKind.OTHER, "Th. 05/11/2026", listOf(
                EventUi("e3", "Dentist", "16:00", false, "#3b82f6", "Home", false, null),
            )),
        ),
        tasks = listOf(
            TaskUi("t1", "Pay rent", "Th. 29/10/2026", overdue = true, priority = 4, listName = "Today", listColor = "#0d9488", recurring = true, completable = true),
            TaskUi("t2", null, null, overdue = false, priority = 1, listName = null, listColor = null, recurring = false, completable = true),
        ),
        shortcuts = listOf(
            ShortcutUi("q1", "Spotify", "https://open.spotify.com", "Music", "#1db954"),
            ShortcutUi("q2", "Bank", "https://bank.example", "UnknownIcon", null),
            ShortcutUi("q3", "Coffee", "https://cafe.example", "☕", null),
        ),
    )

    private val TALL = DpSize(250.dp, 400.dp)  // hero + list
    private val HERO = DpSize(250.dp, 110.dp)  // 4×2: hero only

    /** Renders the dashboard as the widget does: events filtered by [daysAhead] and [hidden]. */
    private fun GlanceAppWidgetUnitTest.dashboard(
        ui: WidgetUi,
        size: DpSize = TALL,
        daysAhead: Int = EventsConfig.DEFAULT_DAYS,
        hidden: Set<String> = emptySet(),
    ) {
        setContext(context)
        setAppWidgetSize(size)
        val days = EventsConfig.filter(ui.eventDays, ui.today, daysAhead, hidden)
        provideComposable { WidgetRoot(ui) { DashboardContent(ui, days, it) } }
    }

    @Test
    fun `hero shows greeting, date, weather, rating, and counters, without quick buttons`() = runGlanceAppWidgetUnitTest {
        dashboard(populated, HERO)
        onNode(hasText("Ana")).assertExists()
        onNode(hasText("Today is Sa. 31/10/2026")).assertExists()
        onNode(hasText("18°C")).assertExists()
        onNode(hasText("★ 7.4")).assertExists()
        onNode(hasText("12")).assertExists()
        onNode(hasText("Journal")).assertDoesNotExist()
        onNode(hasText("Budget")).assertDoesNotExist()
    }

    @Test
    fun `mic button shows in the compact hero`() = runGlanceAppWidgetUnitTest {
        dashboard(populated, HERO)
        onNode(hasContentDescription("Add tasks or reminders by voice")).assertExists()
    }

    @Test
    fun `mic button shows in the tall stacked hero`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(uiStyle = UiStyle.PREDATOR))
        onNode(hasContentDescription("Add tasks or reminders by voice")).assertExists()
    }

    @Test
    fun `tall dashboard stacks the hero above the list`() = runGlanceAppWidgetUnitTest {
        dashboard(populated)
        onNode(hasTextEqualTo("Hello,")).assertExists()
        onNode(hasTextEqualTo("Ana")).assertExists()
        onNode(hasTextEqualTo("Today is,")).assertExists()
        onNode(hasTextEqualTo("Sa. 31/10/2026")).assertExists()
        onNode(hasTextEqualTo("And you have:")).assertExists()
        onNode(hasTextEqualTo("Weather in Madrid, ES")).assertExists()
        onNode(hasTextEqualTo("18°C")).assertExists()
        onNode(hasTextEqualTo("9°C")).assertExists()
        onNode(hasTextEqualTo("★ 7.4")).assertExists()
        onAllNodes(hasTextEqualTo("12")).assertCountEquals(2) // mail counter and the inbox row
        onNode(hasContentDescription("Reload")).assertExists()
        onNode(hasTextEqualTo("Standup")).assertExists()
    }

    @Test
    fun `predator stacked hero uppercases captions and shows the hex rating`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(uiStyle = UiStyle.PREDATOR))
        onNode(hasTextEqualTo("HELLO,")).assertExists()
        onNode(hasTextEqualTo("AND YOU HAVE:")).assertExists()
        onNode(hasTextEqualTo("WEATHER IN MADRID, ES")).assertExists()
        onNode(hasTextEqualTo("7.4")).assertExists()
        onNode(hasText("JOURNAL")).assertDoesNotExist()
        onNode(hasText("BUDGET")).assertDoesNotExist()
        onNode(hasTextEqualTo("Ana")).assertExists()
    }

    @Test
    fun `a 4x2 dashboard shows only the hero`() = runGlanceAppWidgetUnitTest {
        dashboard(populated, HERO)
        onNode(hasTextEqualTo("Ana")).assertExists()
        onNode(hasTextEqualTo("Standup")).assertDoesNotExist()
        onNode(hasTextEqualTo("Pay rent")).assertDoesNotExist()
        onNode(hasTextEqualTo("Spotify")).assertDoesNotExist()
    }

    @Test
    fun `dashboard shows hero, events, tasks and shortcuts in one widget`() = runGlanceAppWidgetUnitTest {
        dashboard(populated)
        onNode(hasTextEqualTo("Ana")).assertExists()
        onNode(hasTextEqualTo("Standup")).assertExists()
        onNode(hasTextEqualTo("Pay rent")).assertExists()
        onNode(hasTextEqualTo("Spotify")).assertExists()
    }

    @Test
    fun `predator is the default style`() {
        assert(UiStyle.from(null) == UiStyle.PREDATOR)
        assert(UiStyle.from("something-new") == UiStyle.PREDATOR)
        assert(UiStyle.from("classic") == UiStyle.CLASSIC)
        assert(WidgetUi.SignedOut.uiStyle == UiStyle.PREDATOR)
    }

    @Test
    fun `predator hero uppercases the date and shows the hex rating`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(uiStyle = UiStyle.PREDATOR), HERO)
        onNode(hasText("TODAY IS SA. 31/10/2026")).assertExists()
        onNode(hasTextEqualTo("7.4")).assertExists()
        // User content keeps its case
        onNode(hasTextEqualTo("Ana")).assertExists()
    }

    @Test
    fun `predator events, tasks and shortcuts render`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(uiStyle = UiStyle.PREDATOR), DpSize(320.dp, 400.dp))
        onNode(hasTextEqualTo("Pay rent")).assertExists()
        onNode(hasTextEqualTo("ADD TASK")).assertExists()
        onNode(hasTextEqualTo("TASK LISTS")).assertExists()
        onNode(hasTextEqualTo("Th. 29/10/2026")).assertExists() // due date: data, not uppercased
        onAllNodes(hasContentDescription("Complete task")).assertCountEquals(2)
        onNode(hasTextEqualTo("SHORTCUTS")).assertExists()
        onAllNodes(hasTextEqualTo("MAIL")).assertCountEquals(2) // hero counter label and the mail section
        onNode(hasTextEqualTo("Inbox")).assertExists()
        onNode(hasTextEqualTo("LINKS")).assertExists()
        onNode(hasTextEqualTo("MANAGE")).assertExists()
        onNode(hasTextEqualTo("TOMORROW")).assertExists()
        onNode(hasTextEqualTo("Standup")).assertExists()
        onNode(hasTextEqualTo("Coffee")).assertExists()
    }

    @Test
    fun `signed out shows the sign-in prompt and nothing personal`() = runGlanceAppWidgetUnitTest {
        dashboard(WidgetUi.SignedOut)
        onNode(hasText("Sign in to LIFE MAPP", ignoreCase = true)).assertExists()
        onNode(hasText("Journal", ignoreCase = true)).assertDoesNotExist()
    }

    @Test
    fun `before the first sync the widget shows loading`() = runGlanceAppWidgetUnitTest {
        dashboard(WidgetUi.SignedOut.copy(authState = AuthState.SIGNED_IN, hasData = false))
        onNode(hasText("Loading…")).assertExists()
    }

    @Test
    fun `events group by day and respect the days-ahead setting`() = runGlanceAppWidgetUnitTest {
        dashboard(populated, daysAhead = 3)
        onNode(hasText("Standup")).assertExists()
        onNode(hasText("Family lunch")).assertExists()
        onNode(hasText("All day")).assertExists()
        onNode(hasText("Dentist")).assertDoesNotExist() // 5 days out
    }

    @Test
    fun `events list today even when it is empty, and hide filtered calendars`() = runGlanceAppWidgetUnitTest {
        dashboard(populated, daysAhead = 7, hidden = setOf("Work"))
        onNode(hasText("No events today")).assertExists()
        onNode(hasText("Standup")).assertDoesNotExist()
        onNode(hasText("Dentist")).assertExists()
    }

    @Test
    fun `events ask to connect a calendar, and tasks still show`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(calendarConnected = false, eventDays = emptyList()))
        onNode(hasTextEqualTo("No calendar connected yet")).assertExists()
        onNode(hasTextEqualTo("Add calendar")).assertExists()
        onNode(hasTextEqualTo("Pay rent")).assertExists()
    }

    @Test
    fun `events are numbered across days with calendar tag and time line`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(uiStyle = UiStyle.PREDATOR), daysAhead = 7)
        // Events, tasks and links are numbered separately; "2" is also the hero's events counter, "3" the links count
        onAllNodes(hasTextEqualTo("1")).assertCountEquals(3)
        onAllNodes(hasTextEqualTo("2")).assertCountEquals(4)
        onAllNodes(hasTextEqualTo("3")).assertCountEquals(3)
        onNode(hasContentDescriptionEqualTo("Add link")).assertExists()
        onNode(hasTextEqualTo("WORK")).assertExists()
        onAllNodes(hasTextEqualTo("HOME")).assertCountEquals(2)
        onNode(hasTextEqualTo("09:00 – 09:15 · Sa. 31/10/2026")).assertExists()
        onNode(hasTextEqualTo("All day · Su. 01/11/2026")).assertExists()
        onAllNodes(hasTextEqualTo("TODAY")).assertCountEquals(2) // events day divider and the tasks divider
        onNode(hasTextEqualTo("TOMORROW")).assertExists()
    }

    @Test
    fun `tasks have a completion checkbox and encrypted titles are locked`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(encryption = EncryptionState.LOCKED))
        onNode(hasText("Pay rent")).assertExists()
        onNode(hasText("🔒 Locked")).assertExists()
        onNode(hasTextEqualTo("Add task")).assertExists()
        onNode(hasTextEqualTo("Task lists")).assertExists()
        onAllNodes(hasContentDescription("Complete task")).assertCountEquals(2)
        onAllNodes(hasRunCallbackClickAction<CompleteTaskAction>(androidx.glance.action.actionParametersOf(CompleteTaskAction.TaskIdKey to "t1"))).assertCountEquals(1)
    }

    @Test
    fun `tasks empty, stale and failure states`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(tasks = emptyList(), tasksCount = 0, stale = true, completionFailed = true))
        onNode(hasText("Nothing due. Well done!")).assertExists()
        onNode(hasText("Not updated since 10:42")).assertExists()
        onNode(hasText("A task couldn't be completed. Tap to dismiss.")).assertExists()
    }

    @Test
    fun `offline error is shown`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(error = SyncError.NETWORK))
        onNode(hasText("Offline. Showing saved data.")).assertExists()
    }

    @Test
    fun `shortcuts render icons, emoji and letter fallbacks`() = runGlanceAppWidgetUnitTest {
        dashboard(populated)
        onNode(hasContentDescription("Spotify")).assertExists()
        onNode(hasTextEqualTo("B")).assertExists()
        onNode(hasText("☕")).assertExists()
        onNode(hasText("Coffee")).assertExists()
    }

    @Test
    fun `mail shows the connect prompt until mail is connected, then the inbox count`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(mailConnected = false))
        onNode(hasTextEqualTo("Mail not connected")).assertExists()
        onNode(hasTextEqualTo("Connect mail")).assertExists()
        onNode(hasTextEqualTo("Inbox")).assertDoesNotExist()
    }

    @Test
    fun `connected mail shows the inbox with its unread count`() = runGlanceAppWidgetUnitTest {
        dashboard(populated)
        onNode(hasTextEqualTo("Inbox")).assertExists()
        onNode(hasTextEqualTo("Mail not connected")).assertDoesNotExist()
        onNode(hasTextEqualTo("Links")).assertExists()
        onNode(hasTextEqualTo("Manage")).assertExists()
    }

    @Test
    fun `encrypted shortcuts on a locked device ask to unlock`() = runGlanceAppWidgetUnitTest {
        dashboard(populated.copy(shortcuts = emptyList(), shortcutsLocked = true))
        onNode(hasText("Quick links are encrypted. Unlock them in the app.")).assertExists()
    }
}
