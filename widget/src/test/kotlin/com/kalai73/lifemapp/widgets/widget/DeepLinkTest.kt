package com.kalai73.lifemapp.widgets.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `every link reads back from its intent`() {
        listOf(
            DeepLink.Section(LifeMappWidgets.SECTION_EVENTS),
            DeepLink.Section(LifeMappWidgets.SECTION_INTEGRITY, topicId = "topic-1"),
            DeepLink.Section("domain-42"),
            DeepLink.Item("item-1"),
            DeepLink.Event("event-1", "2026-11-03T09:00:00Z"),
            DeepLink.Event("event-2", null),
            DeepLink.Task("task-1"),
        ).forEach { link -> assertEquals(link, DeepLink.from(link.intent(context))) }
    }

    @Test
    fun `different rows get different intent data, so their PendingIntents stay apart`() {
        assertNotEquals(DeepLink.Item("a").intent(context).data, DeepLink.Item("b").intent(context).data)
        assertNotEquals(
            DeepLink.Section(LifeMappWidgets.SECTION_INTEGRITY, "t1").intent(context).data,
            DeepLink.Section(LifeMappWidgets.SECTION_INTEGRITY, "t2").intent(context).data,
        )
    }

    @Test
    fun `a consumed intent carries no link, and a plain launch never did`() {
        val consumed = DeepLink.consumed(DeepLink.Task("task-1").intent(context))
        assertNull(DeepLink.from(consumed))
        assertNull(consumed.data)
        assertNull(DeepLink.from(Intent()))
        assertNull(DeepLink.from(null))
    }
}
