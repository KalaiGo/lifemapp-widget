package com.kalai73.lifemapp.widgets.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.Uri
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.repository.SyncResult
import com.kalai73.lifemapp.widgets.widget.dashboard.DashboardWidget
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object LifeMappWidgets {
    /** Re-renders every placed LIFE MAPP widget from the cache. */
    suspend fun updateAll(context: Context) {
        LifeMappGraph.get(context).invalidateWidgets() // running sessions re-read secrets + cache
        DashboardWidget().updateAll(context)
    }

    /**
     * Downloads a fresh snapshot right here and redraws every widget (and the app's Today screen) from it, with
     * the spinner on meanwhile. A queued sync, which the system may hold back for a while (expedited quota, Doze,
     * standby buckets), only covers a failed, timed-out or interrupted reload.
     */
    suspend fun reloadNow(context: Context, timeoutMs: Long = INLINE_RELOAD_TIMEOUT_MS) {
        val app = context.applicationContext
        val graph = LifeMappGraph.get(app)
        var reloaded = false
        try {
            graph.snapshots.markSyncing()
            updateAll(app) // spinner
            reloaded = withTimeoutOrNull(timeoutMs) {
                if (graph.encryption.dataKey() != null) graph.encryption.verifyStoredKey()
                graph.snapshots.refresh() == SyncResult.Success
            } == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "reload failed: ${e::class.simpleName}")
        } finally {
            withContext(NonCancellable) {
                graph.snapshots.clearSyncing()
                updateAll(app)
                if (!reloaded) SyncScheduler.syncNow(app)
            }
        }
    }

    /**
     * Within the time a broadcast receiver may run (the reload button's callback runs in one): past it the
     * queued sync takes over.
     */
    private const val INLINE_RELOAD_TIMEOUT_MS = 25_000L
    private const val TAG = "LifeMappWidgets"

    /** Lets the app (sign-out, unlock, settings) re-render widgets without knowing about Glance. */
    fun install(context: Context) {
        LifeMappGraph.get(context).refreshWidgets = { updateAll(context.applicationContext) }
    }

    suspend fun placedCount(context: Context): Int {
        val manager = GlanceAppWidgetManager(context)
        return manager.getGlanceIds(DashboardWidget::class.java).size
    }

    /** Opens the app (sign-in / unlock / settings). */
    fun appIntent(context: Context): Intent =
        (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The Today screen section the app scrolls to (one of the SECTION_ values), from a widget tap */
    const val EXTRA_SECTION = "com.kalai73.lifemapp.widgets.extra.SECTION"
    const val SECTION_EVENTS = "events"
    const val SECTION_TASKS = "tasks"
    const val SECTION_SHORTCUTS = "shortcuts"
    const val SECTION_INTEGRITY = "integrity"
    const val SECTION_SCORING = "scoring"

    /** The Integrity topic (subdomain) id the app expands when it opens its section, from a topic header's tap */
    const val EXTRA_TOPIC_ID = "com.kalai73.lifemapp.widgets.extra.TOPIC_ID"

    /** Opens the app on the Today screen scrolled to [section] (a SECTION_ value), with [topicId] expanded. */
    fun sectionIntent(context: Context, section: String, topicId: String? = null): Intent =
        appIntent(context)
            .putExtra(EXTRA_SECTION, section)
            .apply { topicId?.let { putExtra(EXTRA_TOPIC_ID, it) } }
            // Distinct data so the sections' and topics' PendingIntents don't collapse into one (extras don't count)
            .setData(Uri.fromParts("lifemapp-section", section, topicId))

    /** The item (subsubdomain) id the app opens in its item modal, from an item row's tap */
    const val EXTRA_ITEM_ID = "com.kalai73.lifemapp.widgets.extra.ITEM_ID"

    /** Opens the app on the Today screen with [itemId]'s content in the item modal. */
    fun itemIntent(context: Context, itemId: String): Intent =
        appIntent(context)
            .putExtra(EXTRA_ITEM_ID, itemId)
            // Distinct data so the rows' PendingIntents don't collapse into one (extras don't count)
            .setData(Uri.fromParts("lifemapp-item", itemId, null))

    /** The calendar event id the app shows in its event modal, from an event row's tap */
    const val EXTRA_EVENT_ID = "com.kalai73.lifemapp.widgets.extra.EVENT_ID"
    /** The event's start as the snapshot has it, telling a repeating event's instances apart */
    const val EXTRA_EVENT_START = "com.kalai73.lifemapp.widgets.extra.EVENT_START"

    /** Opens the app on the Today screen with the event's details in the event modal. */
    fun eventIntent(context: Context, eventId: String, start: String): Intent =
        appIntent(context)
            .putExtra(EXTRA_EVENT_ID, eventId)
            .putExtra(EXTRA_EVENT_START, start)
            .setData(Uri.fromParts("lifemapp-event", eventId, start))

    /** The task id the app shows in its task modal, from a task row's tap */
    const val EXTRA_TASK_ID = "com.kalai73.lifemapp.widgets.extra.TASK_ID"

    /** Opens the app on the Today screen with the task's details in the task modal. */
    fun taskIntent(context: Context, taskId: String): Intent =
        appIntent(context)
            .putExtra(EXTRA_TASK_ID, taskId)
            .setData(Uri.fromParts("lifemapp-task", taskId, null))
}
