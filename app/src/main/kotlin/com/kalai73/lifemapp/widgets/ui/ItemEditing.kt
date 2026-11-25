package com.kalai73.lifemapp.widgets.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import com.kalai73.lifemapp.widgets.core.presentation.panel.ItemTags
import com.kalai73.lifemapp.widgets.core.presentation.panel.PanelReminder
import com.kalai73.lifemapp.widgets.core.presentation.panel.ReminderDates
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** An item's tags and reminder, as the item editor shows them. */
internal data class ItemMeta(
    /** The topic's tags: always on, not removable here */
    val topicTags: List<String>,
    /** Effective tags that still exist: the topic's, then the item's own */
    val tags: List<String>,
    /** The user's own tags, id to name */
    val customTags: Map<String, String>,
    val reminder: PanelReminder?,
) {
    companion object {
        fun of(topic: Topic, item: TopicItem, customTags: Map<String, String>) = ItemMeta(
            topicTags = ItemTags.topicTags(topic.tags),
            tags = ItemTags.effective(topic.tags, item.tags),
            customTags = customTags,
            reminder = ReminderDates.parse(item.reminderDate, ZoneId.systemDefault())?.let { date ->
                PanelReminder(
                    value = item.reminderDate!!, date = date, rule = item.reminderRule,
                    leadDays = ReminderDates.normalizeLeadDays(item.reminderLeadDays),
                    taskCreated = item.reminderTaskCreated,
                    status = ReminderDates.status(date, item.reminderTaskCreated, LocalDate.now()),
                )
            },
        )
    }
}

/** The item editor's edits beside Done and Remove; provided by the dashboard. */
internal interface ItemEditing {
    fun meta(itemId: String): Flow<ItemMeta?>
    suspend fun loadCustomTags()
    /** Saves and waits: false when it failed (the dashboard shows why) */
    suspend fun saveNow(itemId: String, name: String?, html: String?): Boolean
    fun saveTags(itemId: String, tags: List<String>)
    fun saveReminder(itemId: String, date: String?, rule: String?, leadDays: Int)
}

internal val LocalItemEditing = staticCompositionLocalOf<ItemEditing?> { null }
