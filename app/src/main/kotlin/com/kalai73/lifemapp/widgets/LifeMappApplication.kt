package com.kalai73.lifemapp.widgets

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import com.kalai73.lifemapp.widgets.widget.sync.SyncReceiver
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler

class LifeMappApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LifeMappWidgets.install(this)
        LifeMappGraph.get(this)
        SyncScheduler.schedulePeriodic(this)
        // ACTION_USER_PRESENT isn't delivered to manifest receivers on Android 8+; this catches unlocks while
        // the process is alive (widget renders and syncs keep it warm much of the time)
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        androidx.core.content.ContextCompat.registerReceiver(
            this, SyncReceiver(), filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
