package com.kalai73.lifemapp.widgets.core.presentation

import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.Counts
import com.kalai73.lifemapp.widgets.core.model.CustomTag
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.Task
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ContextTagsPresenterTest {
    private val now = LocalDate.of(2026, 11, 25).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val customSport = CustomTag("c-sport123", "Sport", "amber")

    private fun sampleState(activeTag: String): CachedState {
        val integrity = Integrity(
            domainId = "d1",
            position = 1,
            name = "Integrity",
            topics = listOf(
                Topic(
                    id = "t-rem",
                    name = "Reminders",
                    reminders = true,
                    tags = listOf("me"),
                    items = listOf(
                        TopicItem(id = "r1", name = "Pay bill", tags = emptyList()),
                        TopicItem(id = "r2", name = "Email boss", tags = listOf("work")),
                    ),
                ),
                Topic(
                    id = "t-work",
                    name = "Office",
                    tags = listOf("work"),
                    items = listOf(TopicItem(id = "w1", name = "Slides", tags = emptyList())),
                ),
            ),
        )
        val serenity = Integrity(
            domainId = "d2",
            position = 2,
            name = "Serenity",
            topics = listOf(
                Topic(
                    id = "t-ser",
                    name = "Health",
                    tags = listOf("me"),
                    items = listOf(
                        TopicItem(id = "s1", name = "Run", tags = listOf("c-sport123")),
                        TopicItem(id = "s2", name = "Read", tags = emptyList()),
                    ),
                ),
            ),
        )
        val trayectory = Integrity(
            domainId = "d10",
            position = 10,
            name = "Trayectory",
            topics = listOf(
                Topic(
                    id = "t-traj",
                    name = "Career",
                    tags = listOf("work"),
                    items = listOf(TopicItem(id = "tr1", name = "Promo", tags = emptyList())),
                ),
            ),
        )
        val business = Integrity(
            domainId = "d20",
            position = 20,
            name = "Delivery",
            topics = listOf(
                Topic(
                    id = "t-biz",
                    name = "Ops",
                    tags = listOf("me"),
                    items = listOf(TopicItem(id = "b1", name = "Ship", tags = emptyList())),
                ),
            ),
        )
        val snapshot = Snapshot(
            today = "2026-11-25",
            counts = Counts(tasks = 3),
            tasks = listOf(
                Task(id = "tk1", title = "Personal", segment = Task.SEGMENT_TODAY, labels = emptyList()),
                Task(id = "tk2", title = "Client call", segment = Task.SEGMENT_TODAY, labels = listOf("Work")),
                Task(id = "tk3", title = "5k run", segment = Task.SEGMENT_TODAY, labels = listOf("#Sport")),
            ),
            integrity = integrity,
            todayDomains = listOf(serenity),
            monthlyDomains = listOf(integrity, serenity, trayectory, business),
        )
        return CachedState(
            snapshot = snapshot,
            activeTag = activeTag,
            customTags = listOf(customSport),
        )
    }

    @Test
    fun `Today filters tasks, domains, topics and items while home screen widgets stay unfiltered`() {
        val state = sampleState(activeTag = "work")

        // Home screen widgets (applyTagFilter = false) keep everything
        val widgetUi = WidgetPresenter.present(state, AuthState.SIGNED_IN, null, now, ZoneOffset.UTC, applyTagFilter = false)
        assertEquals(3, widgetUi.tasks.size)
        assertEquals(3, widgetUi.tasksCount)
        assertEquals(1, widgetUi.todayDomains.size)

        // Today screen (applyTagFilter = true) filters by "work"
        val todayUi = WidgetPresenter.present(state, AuthState.SIGNED_IN, null, now, ZoneOffset.UTC, applyTagFilter = true)
        assertEquals(listOf("tk2"), todayUi.tasks.map { it.id })
        assertEquals(1, todayUi.tasksCount)
        assertEquals(emptyList<String>(), todayUi.todayDomains.map { it.domainId }) // Serenity has no work items
        // Integrity always stays; Reminders header stays with only work-tagged items; Office topic stays
        val intTopics = todayUi.integrity!!.topics
        assertEquals(listOf("t-rem", "t-work"), intTopics.map { it.id })
        assertEquals(listOf("r2"), intTopics[0].items.map { it.id })
        assertEquals(listOf("w1"), intTopics[1].items.map { it.id })
    }

    @Test
    fun `Today filters by custom tag across tasks and items`() {
        val todayUi = WidgetPresenter.present(
            sampleState(activeTag = "c-sport123"),
            AuthState.SIGNED_IN,
            null,
            now,
            ZoneOffset.UTC,
            applyTagFilter = true,
        )
        assertEquals(listOf("tk3"), todayUi.tasks.map { it.id })
        assertEquals(listOf("d2"), todayUi.todayDomains.map { it.domainId })
        assertEquals(listOf("s1"), todayUi.todayDomains[0].topics[0].items.map { it.id })
    }

    @Test
    fun `Monthly filters domains and task count while Business stays unfiltered`() {
        val state = sampleState(activeTag = "work")
        val monthly = MonthlyPresenter.present(state, AuthState.SIGNED_IN, null, now, ZoneOffset.UTC)
        assertEquals(1, monthly.tasksCount)
        assertEquals("d10", monthly.trayectory?.domain?.domainId)

        val business = BusinessPresenter.present(state, AuthState.SIGNED_IN, null, now, ZoneOffset.UTC)
        assertEquals(listOf("d20"), business.monthlyDomains.map { it.domainId })
    }
}
