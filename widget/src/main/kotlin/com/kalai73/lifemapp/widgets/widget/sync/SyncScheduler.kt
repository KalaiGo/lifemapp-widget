package com.kalai73.lifemapp.widgets.widget.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * When widgets refresh (see docs/androidWidgetsApp.md §5):
 * - every 30 min on any network (periodic, unique)
 * - right away when a widget is added or resized, settings change, or the user taps reload (expedited)
 * - just after local midnight, so "today" rolls over (inexact alarm, no exact-alarm permission needed)
 * - on unlock when the cache is older than 15 min (registered at runtime by the app)
 */
object SyncScheduler {
    const val PERIODIC_WORK = "snapshot-sync"
    const val ONE_OFF_WORK = "snapshot-sync-now"
    const val PERIOD_MINUTES = 30L
    const val MANUAL_DEBOUNCE_MS = 10_000L
    const val UNLOCK_REFRESH_AFTER_MS = 15 * 60 * 1000L
    private const val MIDNIGHT_WINDOW_MS = 10 * 60 * 1000L

    @Volatile private var lastManualSyncAt = 0L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SnapshotWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(PERIODIC_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        scheduleMidnight(context)
    }

    /** False within 10 s of the last manual reload. */
    fun canSyncManually(now: Long = System.currentTimeMillis()): Boolean = now - lastManualSyncAt >= MANUAL_DEBOUNCE_MS

    /**
     * Sync now. Manual reloads are debounced: taps within 10 s of the last one are ignored.
     * @return false when debounced
     */
    fun syncNow(context: Context, manual: Boolean = false, now: Long = System.currentTimeMillis()): Boolean {
        if (manual) {
            if (now - lastManualSyncAt < MANUAL_DEBOUNCE_MS) return false
            lastManualSyncAt = now
        }
        val request = OneTimeWorkRequestBuilder<SnapshotWorker>()
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.REPLACE, request)
        return true
    }

    fun completeTask(context: Context, taskId: String) {
        val request = OneTimeWorkRequestBuilder<CompleteTaskWorker>()
            .setInputData(workDataOf(CompleteTaskWorker.KEY_TASK_ID to taskId))
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("complete-task-$taskId", ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(ONE_OFF_WORK)
        context.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(context))
    }

    /** Next local midnight, a minute late so the server's "today" has rolled over too. */
    fun nextMidnight(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(java.time.Clock.fixed(java.time.Instant.ofEpochMilli(now), zone))
            .plusDays(1).atStartOfDay(zone).plusMinutes(1).toInstant().toEpochMilli()

    fun scheduleMidnight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setWindow(AlarmManager.RTC, nextMidnight(), MIDNIGHT_WINDOW_MS, midnightIntent(context))
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, SyncReceiver::class.java).setAction(SyncReceiver.ACTION_MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
