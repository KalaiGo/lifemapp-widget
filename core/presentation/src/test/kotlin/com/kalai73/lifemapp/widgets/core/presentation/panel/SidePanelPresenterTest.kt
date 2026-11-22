package com.kalai73.lifemapp.widgets.core.presentation.panel

import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.PlanLimits
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SidePanelPresenterTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 20)
    private val master = PlanLimits.forTier("master")

    private val mastery = Integrity(
        domainId = "d-13", position = 13, name = "Mastery",
        topics = listOf(
            Topic(
                "s-1", name = "Reading", tags = listOf("me", "work"),
                items = listOf(
                    TopicItem("ss-2", name = "Design Links", tags = listOf("family"), content = """<p><a href="https://raindrop.io/">Raindrop</a></p>""", updatedAt = "2026-01-03"),
                    TopicItem("ss-1", name = "TypeScript Handbook", content = """<p><a href="https://www.typescriptlang.org/docs/">https://www.typescriptlang.org/docs/</a> https://raindrop.io/</p>""", updatedAt = "2026-01-02"),
                    TopicItem("ss-img", name = "Screenshot", isImage = true, content = "https://example.com/image.png"),
                ),
            ),
        ),
    )
    private val integrity = Integrity(
        domainId = "d-1", position = 1, name = "Integrity",
        topics = listOf(
            Topic(
                "s-r", name = "Reminders", reminders = true,
                items = listOf(
                    TopicItem("r-late", name = "Pay rent", reminderDate = "2026-11-18T09:00:00Z"),
                    TopicItem("r-today", name = "Call", reminderDate = "2026-11-20T09:00:00Z", reminderRule = "FREQ=WEEKLY;INTERVAL=1"),
                    TopicItem("r-done", name = "Renew", reminderDate = "2026-11-25T09:00:00Z", reminderTaskCreated = true),
                    TopicItem("plain", name = "No alert"),
                ),
            ),
        ),
    )
    private val business = Integrity(domainId = "d-20", position = 20, name = "Company", topics = listOf(Topic("b", items = listOf(TopicItem("b1", content = "https://biz.com")))))

    private fun present(limits: PlanLimits = master) = SidePanelPresenter.present(
        Snapshot(monthlyDomains = listOf(mastery, business, integrity)), dataKey = null, limits = limits, today = today, zone = zone,
    )

    @Test
    fun `business domains are left out and domains are in position order`() {
        assertEquals(listOf("d-1", "d-13"), present().domains.map { it.id })
    }

    @Test
    fun `links follow the tree, are listed once per topic and carry effective tags`() {
        val ui = present()
        assertEquals(listOf("https://raindrop.io/", "https://www.typescriptlang.org/docs/"), ui.links.map { it.url })
        assertEquals("Raindrop", ui.links[0].label)
        assertEquals(listOf("me", "work", "family"), ui.links[0].tags)
        assertEquals("typescriptlang.org › docs", ui.links[1].label)
        assertEquals("", ui.links[1].anchorLabel)
        assertEquals(1, ui.item("ss-2")!!.linkCount)
        assertEquals(0, ui.item("ss-img")!!.linkCount)
        assertEquals(listOf("me" to 2, "work" to 2, "family" to 1), ui.tagCounts)
    }

    @Test
    fun `fetched titles name untitled links`() {
        val ui = SidePanelPresenter.present(
            Snapshot(monthlyDomains = listOf(mastery)), null, master,
            titles = mapOf("https://www.typescriptlang.org/docs/" to "The TypeScript Handbook"), today = today, zone = zone,
        )
        assertEquals("The TypeScript Handbook", ui.links[1].label)
    }

    @Test
    fun `the plan caps topics and items, and the Reminders topic by the reminders limit`() {
        val basic = present(PlanLimits.forTier("basic"))
        assertEquals(listOf("ss-2", "ss-1"), basic.topic("s-1")!!.items.map { it.id })
        // basic: 10 reminders, so all four stay although items per topic are capped at 2
        assertEquals(4, basic.topic("s-r")!!.items.size)
    }

    @Test
    fun `alerts sort by date with status from today`() {
        val ui = present()
        val alerts = SidePanelPresenter.filterAlerts(ui, PanelScope(), PanelSort.STRUCTURE) { it.name.orEmpty() }
        assertEquals(listOf("r-late", "r-today", "r-done"), alerts.map { it.item.id })
        assertEquals(listOf(ReminderStatus.OVERDUE, ReminderStatus.DUE, ReminderStatus.CREATED), alerts.map { it.item.reminder!!.status })
        assertNull(ui.item("plain")!!.reminder)
    }

    @Test
    fun `filters narrow links by scope, tag and query`() {
        val ui = present()
        val name: (PanelDomain) -> String = { it.name.orEmpty() }
        assertEquals(1, SidePanelPresenter.filterLinks(ui, PanelScope(tag = "family"), PanelSort.STRUCTURE, name).size)
        assertEquals(listOf("ss-1"), SidePanelPresenter.filterLinks(ui, PanelScope(query = "handbook"), PanelSort.STRUCTURE, name).map { it.itemId })
        assertEquals(2, SidePanelPresenter.filterLinks(ui, PanelScope(query = "mastery"), PanelSort.STRUCTURE, name).size)
        assertTrue(SidePanelPresenter.filterLinks(ui, PanelScope(domainId = "d-1"), PanelSort.STRUCTURE, name).isEmpty())
        assertEquals(listOf("Raindrop", "typescriptlang.org › docs"), SidePanelPresenter.filterLinks(ui, PanelScope(), PanelSort.ALPHA, name).map { it.label })
    }

    @Test
    fun `reminder days parse in the device zone and nine o'clock is local`() {
        assertEquals(LocalDate.of(2026, 11, 21), ReminderDates.parse("2026-11-20T23:30:00Z", ZoneOffset.ofHours(2)))
        assertEquals(LocalDate.of(2026, 3, 4), ReminderDates.parse("2026-03-04", zone))
        assertEquals("2026-11-20T07:00:00Z", ReminderDates.atNine(today, ZoneOffset.ofHours(2)))
    }

    @Test
    fun `effective tags put the topic's first and default it to me`() {
        assertEquals(listOf("me", "family"), ItemTags.effective(emptyList(), listOf("family", "bogus")))
        assertEquals(listOf("work", "c-abcd1"), ItemTags.effective(listOf("work"), listOf("c-abcd1", "work")))
    }

    private fun link(id: String, item: String, vararg headings: String) = PanelLink(
        id = id, url = "https://$id.com", hostname = "$id.com", label = id, anchorLabel = id, domainId = "d", domainPosition = 1,
        topicId = "t", topicName = "Reading", itemId = item, itemName = "Item $item", headings = headings.toList(),
        headingLevels = headings.indices.map { it + 1 }, tags = emptyList(), updatedAt = "",
    )

    @Test
    fun `link sections split runs by item and heading path`() {
        val links = listOf(link("a", "1"), link("b", "1", "Intro"), link("c", "1", "Intro"), link("d", "1", "Intro", "Deep"), link("e", "2"))
        val sections = SidePanelPresenter.linkSections(links, PanelSort.STRUCTURE, PanelScope(), "Untitled")!!
        assertEquals(listOf(listOf("a"), listOf("b", "c"), listOf("d"), listOf("e")), sections.map { s -> s.links.map { it.id } })
        assertEquals(listOf("Item 1", "Intro", "Intro › Deep", "Item 2"), sections.map { it.title })
        assertEquals(listOf("Reading", "Item 1"), sections[1].context.map { it.label })
        assertEquals(listOf("Reading"), sections[0].context.map { it.label })
    }

    @Test
    fun `inside an item, links above the first heading get no title and headings no item prefix`() {
        val links = listOf(link("a", "1"), link("b", "1", "Intro"))
        val sections = SidePanelPresenter.linkSections(links, PanelSort.STRUCTURE, PanelScope(domainId = "d", topicId = "t", itemId = "1"), "Untitled")!!
        assertEquals(listOf("", "Intro"), sections.map { it.title })
        assertTrue(sections.all { it.context.isEmpty() })
    }

    @Test
    fun `structure order groups per item even without headings, except inside a single item or outside structure order`() {
        val sections = SidePanelPresenter.linkSections(listOf(link("a", "1")), PanelSort.STRUCTURE, PanelScope(), "Untitled")!!
        assertEquals(listOf("Item 1"), sections.map { it.title })
        assertNull(SidePanelPresenter.linkSections(listOf(link("a", "1")), PanelSort.STRUCTURE, PanelScope(itemId = "1"), "Untitled"))
        assertNull(SidePanelPresenter.linkSections(listOf(link("a", "1", "H")), PanelSort.ALPHA, PanelScope(), "Untitled"))
    }
}
