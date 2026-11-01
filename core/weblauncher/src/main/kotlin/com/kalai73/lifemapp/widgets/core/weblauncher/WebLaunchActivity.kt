package com.kalai73.lifemapp.widgets.core.weblauncher

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible trampoline for widget taps. Widgets can only start activities, and opening the web app
 * signed in needs a network call first (the session handoff), so this activity does that and opens the
 * Custom Tab, then finishes.
 */
class WebLaunchActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(WebLauncher.EXTRA_PATH)
        val url = intent.getStringExtra(WebLauncher.EXTRA_URL)
        val fragment = intent.getStringExtra(WebLauncher.EXTRA_FRAGMENT)
        if (path == null && url == null) {
            finish()
            return
        }
        scope.launch {
            val graph = LifeMappGraph.get(applicationContext)
            val (uri, scheme) = withContext(Dispatchers.IO) {
                val resolved = if (path != null) WebLauncher.resolve(applicationContext, path) else Uri.parse(url)
                val target = if (fragment != null) resolved.buildUpon().encodedFragment(fragment).build() else resolved
                val scheme = runCatching { graph.cache.current().snapshot?.settings?.colorScheme }.getOrNull()
                target to ColorSchemeName.from(scheme)
            }
            WebLauncher.openCustomTab(this@WebLaunchActivity, uri, scheme)
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
