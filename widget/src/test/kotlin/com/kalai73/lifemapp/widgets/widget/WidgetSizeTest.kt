package com.kalai73.lifemapp.widgets.widget

import android.content.Context
import android.os.Parcel
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import com.kalai73.lifemapp.widgets.core.presentation.DayKind
import com.kalai73.lifemapp.widgets.core.presentation.EventDayUi
import com.kalai73.lifemapp.widgets.core.presentation.EventUi
import com.kalai73.lifemapp.widgets.core.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.presentation.ShortcutUi
import com.kalai73.lifemapp.widgets.core.presentation.TaskUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicUi
import com.kalai73.lifemapp.widgets.core.presentation.WeatherUi
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import com.kalai73.lifemapp.widgets.widget.common.WidgetRoot
import com.kalai73.lifemapp.widgets.widget.dashboard.DashboardContent
import com.kalai73.lifemapp.widgets.widget.dashboard.DashboardWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Keeps a widget update under the ~500 KB binder limit. Android 12+ receives every responsive layout in each
 * update; past the limit the launcher drops it (TransactionTooLargeException) and the widget silently freezes.
 * The data here is a busy but realistic account, in the heavier Predator style.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSizeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 11, 2)

    private fun topic(n: Int, reminders: Boolean, items: Int) = TopicUi(
        "s$n", n, "Topic $n", reminders = reminders, count = items,
        items = (1..items).map { TopicItemUi("i$n-$it", "Item $n.$it", warning = false, isImage = false) },
    )

    private val ui = WidgetUi.SignedOut.copy(
        uiStyle = UiStyle.PREDATOR,
        authState = AuthState.SIGNED_IN,
        hasData = true,
        username = "Kalai",
        today = today,
        todayLabel = "Mo. 02.11.2026",
        eventsCount = 6, tasksCount = 2, mailCount = 4, mailConnected = true, calendarConnected = true,
        averageRating = 7.2,
        weather = WeatherUi("7°C", "3°C", "Partially cloudy", "partly-cloudy-day", "Zurich, CH"),
        updatedAt = "10:59",
        eventDays = (0L..3L).map { d ->
            EventDayUi(today.plusDays(d), if (d == 0L) DayKind.TODAY else DayKind.OTHER, "Day $d", (1..2).map {
                EventUi("e$d-$it", "Event $d.$it", "08:15 – 08:30", false, "#3b82f6", "GJO WRK", false, null)
            })
        },
        tasks = (1..4).map {
            TaskUi("t$it", "Task $it", "Mo. 02.11.2026", overdue = false, priority = 2, listName = "Today", listColor = "#0d9488", recurring = false, completable = true, tagLabel = "LIFE MAPP", tagColor = "#10B981")
        },
        shortcuts = (1..12).map { ShortcutUi("q$it", "Link $it", "https://x$it.example", "Music", "#1db954") },
        integrity = IntegrityUi(name = "Integrity", infoBox = "Capture & Organize", rating = 8,
            topics = listOf(topic(1, true, 2), topic(2, false, 5), topic(3, false, 2)), warnings = 7),
        todayDomains = listOf(IntegrityUi(domainId = "d5", position = 5, name = "Tenacity", infoBox = "Keep going", rating = 6,
            topics = listOf(topic(1, false, 3), topic(2, false, 3)), warnings = 0)),
    )

    private class Probe(val ui: WidgetUi) : GlanceAppWidget() {
        override val sizeMode = SizeMode.Single
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent { WidgetRoot(ui) { DashboardContent(ui, ui.eventDays, it) } }
        }
    }

    private fun bytes(ui: WidgetUi, size: DpSize = DpSize(250.dp, 450.dp)): Int = runBlocking {
        val rv = Probe(ui).compose(context, size = size)
        val p = Parcel.obtain()
        rv.writeToParcel(p, 0)
        p.dataSize().also { p.recycle() }
    }

    @Test
    fun `one update of every responsive layout stays well under the binder limit`() {
        val sizes = DashboardWidget().sizeMode.let { it as SizeMode.Responsive }.sizes
        // A tall layout (with the list) per size taller than the list threshold; measured at a tall height
        val total = sizes.sumOf { size -> bytes(ui, if (size.height < 200.dp) size else DpSize(size.width, 450.dp)) }
        assertTrue("widget update is $total bytes, budget $BUDGET", total < BUDGET)
    }

    private companion object {
        /** Headroom under the ~500 KB limit for accounts busier than this one */
        const val BUDGET = 420_000
    }
}
