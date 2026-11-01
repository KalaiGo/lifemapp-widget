package com.kalai73.lifemapp.widgets.widget.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.repository.SyncResult
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import com.kalai73.lifemapp.widgets.widget.R

/** Expedited work runs as a short foreground service below Android 12; this is its notification. */
internal fun syncForegroundInfo(context: Context, id: Int): ForegroundInfo {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(SYNC_CHANNEL) == null) {
        manager.createNotificationChannel(
            NotificationChannel(SYNC_CHANNEL, context.getString(R.string.sync_channel_name), NotificationManager.IMPORTANCE_MIN)
        )
    }
    val notification = NotificationCompat.Builder(context, SYNC_CHANNEL)
        .setSmallIcon(com.kalai73.lifemapp.widgets.core.designsystem.R.drawable.lm_ic_refresh_cw)
        .setContentTitle(context.getString(R.string.sync_notification))
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(id, notification)
    }
}

private const val SYNC_CHANNEL = "widget-sync"

/** Downloads a fresh snapshot into the cache and re-renders every widget. */
class SnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo() = syncForegroundInfo(applicationContext, 0x5A11)

    override suspend fun doWork(): Result {
        val graph = LifeMappGraph.get(applicationContext)
        // Before the first unlock after a reboot neither the Keystore key nor the cache can be read
        if (!graph.secrets.isDeviceUnlocked()) return Result.retry()
        if (graph.auth.currentSession() == null) {
            LifeMappWidgets.updateAll(applicationContext)
            return Result.success()
        }

        val result = try {
            graph.snapshots.markSyncing()
            LifeMappWidgets.updateAll(applicationContext) // shows the spinner
            if (graph.encryption.dataKey() != null) graph.encryption.verifyStoredKey()
            graph.snapshots.refresh()
        } finally {
            // Replaced or stopped by the system mid-sync: never leave the spinner on
            graph.snapshots.clearSyncing()
            withContext(NonCancellable) { LifeMappWidgets.updateAll(applicationContext) }
        }
        return when (result) {
            SyncResult.Success, SyncResult.SignedOut -> Result.success()
            is SyncResult.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** Confirms a task checked in the widget; after the last retry the task is put back. */
class CompleteTaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo() = syncForegroundInfo(applicationContext, 0x5A12)

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val graph = LifeMappGraph.get(applicationContext)
        if (!graph.secrets.isDeviceUnlocked()) return Result.retry()

        val ok = runCatching { graph.tasks.complete(taskId) }.getOrDefault(false)
        if (ok) {
            LifeMappWidgets.updateAll(applicationContext)
            // Recurring tasks get their next instance on the server: pick it up
            SyncScheduler.syncNow(applicationContext)
            return Result.success()
        }
        if (runAttemptCount + 1 < MAX_ATTEMPTS) return Result.retry()
        graph.tasks.revert(taskId)
        LifeMappWidgets.updateAll(applicationContext)
        return Result.failure()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val MAX_ATTEMPTS = 4
    }
}
