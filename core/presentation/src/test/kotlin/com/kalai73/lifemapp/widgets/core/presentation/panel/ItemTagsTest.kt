package com.kalai73.lifemapp.widgets.core.presentation.panel

import com.kalai73.lifemapp.widgets.core.model.CustomTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors `lifeMappWeb/frontend/src/lib/domains/__tests__/tags.test.ts`. */
class ItemTagsTest {
    private data class Item(val id: String, val tags: List<String>)
    private fun items(vararg tagLists: List<String>): List<Item> =
        tagLists.mapIndexed { i, tags -> Item("i$i", tags) }

    @Test
    fun `topicTags gives a topic at least me and reads legacy tag`() {
        assertEquals(listOf("me"), ItemTags.topicTags(null))
        assertEquals(listOf("me"), ItemTags.topicTags(emptyList()))
        assertEquals(listOf("me"), ItemTags.topicTags(listOf("boss")))
        assertEquals(listOf("me", "work"), ItemTags.topicTags(listOf("me", "work", "work")))
        assertEquals(listOf("work"), ItemTags.topicTags(null, "work"))
        assertEquals(listOf("me"), ItemTags.topicTags(listOf("me"), "work"))
    }

    @Test
    fun `itemTags keeps items without own tags untagged`() {
        assertEquals(emptyList<String>(), ItemTags.itemTags(null))
        assertEquals(listOf("family"), ItemTags.itemTags(listOf("boss", "family")))
        assertEquals(listOf("family"), ItemTags.itemTags(null, "family"))
        assertEquals(emptyList<String>(), ItemTags.itemTags(null, null))
        assertEquals("me", ItemTags.normalizeTag(null))
        assertEquals("work", ItemTags.normalizeTag("work"))
    }

    @Test
    fun `effective combines topic tags with item own ones`() {
        assertEquals(listOf("me", "work"), ItemTags.effective(listOf("me", "work"), emptyList()))
        assertEquals(listOf("work", "me"), ItemTags.effective(listOf("work"), listOf("me", "work")))
        assertEquals(listOf("me"), ItemTags.effective(null, null))
    }

    @Test
    fun `tag filter shows and hides topics and items`() {
        val list = items(emptyList(), listOf("family"))
        assertEquals(list, ItemTags.filterItemsByTag(listOf("me", "work"), list, Item::tags, "work"))
        assertEquals(list, ItemTags.filterItemsByTag(listOf("me", "work"), list, Item::tags, "me"))
        assertTrue(ItemTags.isTopicVisibleForTag(listOf("me", "work"), list, Item::tags, "work"))

        assertFalse(ItemTags.isTopicVisibleForTag(listOf("work"), items(emptyList(), emptyList()), Item::tags, "me"))
        assertFalse(ItemTags.isTopicVisibleForTag(listOf("me"), items(emptyList()), Item::tags, "family"))

        val mixed = items(emptyList(), listOf("me"), listOf("family"))
        assertEquals(listOf("i1"), ItemTags.filterItemsByTag(listOf("work"), mixed, Item::tags, "me").map { it.id })
        assertTrue(ItemTags.isTopicVisibleForTag(listOf("work"), mixed, Item::tags, "me"))
        assertTrue(ItemTags.isTopicVisibleForTag(null, emptyList<Item>(), Item::tags, "me"))

        assertTrue(ItemTags.isItemVisibleForTag(listOf("work"), emptyList(), "work"))
        assertTrue(ItemTags.isItemVisibleForTag(listOf("me"), listOf("work"), "work"))
        assertTrue(ItemTags.isItemVisibleForTag(listOf("me"), listOf("work"), "me"))
        assertFalse(ItemTags.isItemVisibleForTag(listOf("me"), emptyList(), "work"))
        assertTrue(ItemTags.isItemVisibleForTag(null, emptyList(), "me"))
    }

    @Test
    fun `tags of new entries and itemTagsToStore`() {
        assertEquals(listOf("me"), ItemTags.tagsForNewTopic())
        assertEquals(emptyList<String>(), ItemTags.tagsForNewItem(listOf("me", "work")))
        assertEquals(emptyList<String>(), ItemTags.tagsForNewItem(null))
        assertEquals(listOf("me"), ItemTags.tagsForNewItem(listOf("work")))

        assertEquals(listOf("family"), ItemTags.itemTagsToStore(listOf("me", "work"), listOf("me", "work", "family")))
        assertEquals(listOf("me"), ItemTags.itemTagsToStore(listOf("work"), listOf("work", "me")))
        assertEquals(emptyList<String>(), ItemTags.itemTagsToStore(listOf("me"), listOf("me")))
    }

    @Test
    fun `toggleTag and sameTags`() {
        assertEquals(listOf("me", "work"), ItemTags.toggleTag(listOf("me"), "work"))
        assertEquals(listOf("work"), ItemTags.toggleTag(listOf("me", "work"), "me"))
        assertTrue(ItemTags.sameTags(listOf("me", "work"), listOf("work", "me")))
        assertFalse(ItemTags.sameTags(listOf("me"), null))
        assertTrue(ItemTags.sameTags(emptyList(), null))
    }

    @Test
    fun `taskTag and withTaskTag`() {
        assertEquals("work", ItemTags.taskTag(listOf("Work")))
        assertEquals("family", ItemTags.taskTag(listOf("@familia", "x")))
        assertEquals("me", ItemTags.taskTag(listOf("urgent")))
        assertEquals("me", ItemTags.taskTag(null))
        assertEquals("me", ItemTags.taskTag(emptyList()))

        assertEquals(listOf("work", "urgent"), ItemTags.withTaskTag(listOf("urgent", "Familia"), "work"))
        assertEquals(listOf("urgent"), ItemTags.withTaskTag(listOf("work", "urgent"), "me"))
        assertEquals(listOf("family"), ItemTags.withTaskTag(emptyList(), "family"))
        assertEquals(listOf("work", "a", "b"), ItemTags.withTaskTag(listOf("a", "b", "c"), "work"))
    }

    @Test
    fun `custom tags normalization, validation, filtering, and task labels`() {
        val clients = CustomTag("c-clients1", "Clients", "teal")
        val sport = CustomTag("c-sport123", "Sport", "amber")
        val custom = listOf(clients, sport)

        assertEquals(listOf("c-clients1"), ItemTags.topicTags(listOf("c-clients1")))
        assertEquals(listOf("c-sport123"), ItemTags.itemTags(listOf("c-sport123")))
        assertEquals(listOf("me"), ItemTags.topicTags(listOf("c-")))
        assertEquals(emptyList<String>(), ItemTags.itemTags(listOf("c-UPPER")))

        val a = ItemTags.newCustomTagId()
        assertTrue(ItemTags.isCustomTagId(a))
        assertNotEquals(a, ItemTags.newCustomTagId())

        assertTrue(ItemTags.isKnownTag("work", custom))
        assertTrue(ItemTags.isKnownTag("c-clients1", custom))
        assertFalse(ItemTags.isKnownTag("c-deleted1", custom))
        assertNull(ItemTags.resolveFilter("c-deleted1", custom))
        assertEquals("c-clients1", ItemTags.resolveFilter("c-clients1", custom))

        val list = items(emptyList(), listOf("c-clients1"))
        assertEquals(list, ItemTags.filterItemsByTag(listOf("c-clients1"), list, Item::tags, "c-clients1"))
        assertEquals(listOf(list[1]), ItemTags.filterItemsByTag(listOf("me"), list, Item::tags, "c-clients1"))

        assertEquals(emptyList<CustomTag>(), ItemTags.normalizeCustomTags(null))
        assertEquals(
            listOf(clients, CustomTag("c-color123", "Gym", "emerald")),
            ItemTags.normalizeCustomTags(
                listOf(
                    clients,
                    clients.copy(name = "Other"),
                    CustomTag("c-dupname1", " clients ", "lime"),
                    CustomTag("bad", "X", "lime"),
                    CustomTag("c-color123", "  Gym  ", "neon"),
                ),
            ),
        )

        assertEquals(TagNameError.EMPTY, ItemTags.tagNameError("  ", custom))
        assertEquals(TagNameError.TOO_LONG, ItemTags.tagNameError("x".repeat(21), custom))
        assertEquals(TagNameError.DUPLICATE, ItemTags.tagNameError("clients", custom))
        assertEquals(TagNameError.DUPLICATE, ItemTags.tagNameError("Familie", custom))
        assertNull(ItemTags.tagNameError("Clients", custom, "c-clients1"))
        assertNull(ItemTags.tagNameError("Hobby", custom))

        assertEquals("c-clients1", ItemTags.taskTag(listOf("clients"), custom))
        assertEquals("work", ItemTags.taskTag(listOf("Sport", "work"), custom))
        assertEquals("me", ItemTags.taskTag(listOf("clients")))
        assertTrue(ItemTags.isTaskTagLabel("#Sport", custom))
        assertEquals(listOf("Sport", "urgent"), ItemTags.withTaskTag(listOf("work", "urgent"), "c-sport123", custom))
        assertEquals(listOf("urgent"), ItemTags.withTaskTag(listOf("Sport", "urgent"), "me", custom))
        assertEquals(emptyList<String>(), ItemTags.withTaskTag(listOf("Sport"), "c-deleted1", custom))
    }
}
