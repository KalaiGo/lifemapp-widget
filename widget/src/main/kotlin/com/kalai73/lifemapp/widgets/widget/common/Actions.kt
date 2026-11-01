package com.kalai73.lifemapp.widgets.widget.common

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler

/** Reload button: expedited sync, debounced by 10 s; the spinner shows until it finishes. */
class ReloadAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Mark first: a sync that fails fast must not finish before the spinner is switched on
        if (!SyncScheduler.canSyncManually()) return
        LifeMappGraph.get(context).snapshots.markSyncing()
        LifeMappWidgets.updateAll(context)
        SyncScheduler.syncNow(context, manual = true)
    }
}

/** Task checkbox: hides the task at once (optimistic), then a worker confirms it with the server. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        LifeMappGraph.get(context).tasks.markCompletedLocally(taskId)
        LifeMappWidgets.updateAll(context)
        SyncScheduler.completeTask(context, taskId)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<String>("taskId")
    }
}

class DismissFailureAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        LifeMappGraph.get(context).tasks.dismissFailure()
        LifeMappWidgets.updateAll(context)
    }
}
