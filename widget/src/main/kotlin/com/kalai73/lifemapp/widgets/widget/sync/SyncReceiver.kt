package com.kalai73.lifemapp.widgets.widget.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Midnight rollover, reboot, clock / timezone changes, and (registered at runtime, because Android 8+ doesn't
 * deliver it to manifest receivers) device unlock.
 */
class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_MIDNIGHT, Intent.ACTION_DATE_CHANGED -> {
                SyncScheduler.scheduleMidnight(app)
                rerender(app)
                SyncScheduler.syncNow(app)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // WorkManager restores its own work after a reboot; the alarm has to be set again
                SyncScheduler.schedulePeriodic(app)
                rerender(app)
            }
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                SyncScheduler.scheduleMidnight(app)
                rerender(app)
                SyncScheduler.syncNow(app)
            }
            Intent.ACTION_USER_PRESENT -> onUnlock(app)
        }
    }

    private fun rerender(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                LifeMappWidgets.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun onUnlock(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                val graph = LifeMappGraph.get(context)
                if (graph.auth.currentSession() != null &&
                    System.currentTimeMillis() - graph.cache.current().lastSuccessAt > SyncScheduler.UNLOCK_REFRESH_AFTER_MS
                ) {
                    SyncScheduler.syncNow(context)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.kalai73.lifemapp.widgets.action.MIDNIGHT"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
