package com.kalai73.lifemapp.widgets.widget

import android.content.Context
import android.content.Intent
import android.util.Log
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

    /** Today screen sections a widget tap scrolls to ([DeepLink.Section]) */
    const val SECTION_EVENTS = "events"
    const val SECTION_TASKS = "tasks"
    const val SECTION_SHORTCUTS = "shortcuts"
    const val SECTION_INTEGRITY = "integrity"
    const val SECTION_SCORING = "scoring"
}
