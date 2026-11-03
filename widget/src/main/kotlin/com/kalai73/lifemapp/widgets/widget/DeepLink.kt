package com.kalai73.lifemapp.widgets.widget

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * A widget tap that opens the app's Today screen at something. The widget encodes it into the launch intent with
 * [intent]; the app reads it back with [from]. Widget taps never open the web app.
 */
sealed interface DeepLink {
    /** Scroll Today to [section] (a LifeMappWidgets.SECTION_ value, or "domain-<id>"), expanding [topicId] first */
    data class Section(val section: String, val topicId: String? = null) : DeepLink

    /** Show an item (subsubdomain) in the item modal */
    data class Item(val itemId: String) : DeepLink

    /** Show an event in the details modal; [start] tells a repeating event's instances apart */
    data class Event(val eventId: String, val start: String?) : DeepLink

    /** Show a task in the details modal */
    data class Task(val taskId: String) : DeepLink

    /** Opens the app on the Today screen at this link. */
    fun intent(context: Context): Intent {
        val base = LifeMappWidgets.appIntent(context)
        // Distinct data per link so PendingIntents of different rows don't collapse into one (extras don't count)
        return when (this) {
            is Section -> base.putExtra(EXTRA_SECTION, section)
                .apply { topicId?.let { putExtra(EXTRA_TOPIC_ID, it) } }
                .setData(Uri.fromParts("lifemapp-section", section, topicId))
            is Item -> base.putExtra(EXTRA_ITEM_ID, itemId)
                .setData(Uri.fromParts("lifemapp-item", itemId, null))
            is Event -> base.putExtra(EXTRA_EVENT_ID, eventId)
                .apply { start?.let { putExtra(EXTRA_EVENT_START, it) } }
                .setData(Uri.fromParts("lifemapp-event", eventId, start))
            is Task -> base.putExtra(EXTRA_TASK_ID, taskId)
                .setData(Uri.fromParts("lifemapp-task", taskId, null))
        }
    }

    companion object {
        private const val EXTRA_SECTION = "com.kalai73.lifemapp.widgets.extra.SECTION"
        private const val EXTRA_TOPIC_ID = "com.kalai73.lifemapp.widgets.extra.TOPIC_ID"
        private const val EXTRA_ITEM_ID = "com.kalai73.lifemapp.widgets.extra.ITEM_ID"
        private const val EXTRA_EVENT_ID = "com.kalai73.lifemapp.widgets.extra.EVENT_ID"
        private const val EXTRA_EVENT_START = "com.kalai73.lifemapp.widgets.extra.EVENT_START"
        private const val EXTRA_TASK_ID = "com.kalai73.lifemapp.widgets.extra.TASK_ID"
        private val EXTRAS = listOf(EXTRA_SECTION, EXTRA_TOPIC_ID, EXTRA_ITEM_ID, EXTRA_EVENT_ID, EXTRA_EVENT_START, EXTRA_TASK_ID)

        /** The link a launch intent carries, if any. */
        fun from(intent: Intent?): DeepLink? {
            intent ?: return null
            fun extra(name: String) = intent.getStringExtra(name)?.takeIf { it.isNotBlank() }
            extra(EXTRA_ITEM_ID)?.let { return Item(it) }
            extra(EXTRA_EVENT_ID)?.let { return Event(it, extra(EXTRA_EVENT_START)) }
            extra(EXTRA_TASK_ID)?.let { return Task(it) }
            extra(EXTRA_SECTION)?.let { return Section(it, extra(EXTRA_TOPIC_ID)) }
            return null
        }

        /** [intent] without its link, so a recreated activity doesn't handle the same tap again. */
        fun consumed(intent: Intent): Intent = Intent(intent).apply {
            EXTRAS.forEach(::removeExtra)
            data = null
        }
    }
}
