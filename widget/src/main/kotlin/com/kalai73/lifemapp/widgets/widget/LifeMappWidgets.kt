package com.kalai73.lifemapp.widgets.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.widget.dashboard.DashboardWidget

object LifeMappWidgets {
    /** Re-renders every placed LIFE MAPP widget from the cache. */
    suspend fun updateAll(context: Context) {
        LifeMappGraph.get(context).invalidateWidgets() // running sessions re-read secrets + cache
        DashboardWidget().updateAll(context)
    }

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
}
