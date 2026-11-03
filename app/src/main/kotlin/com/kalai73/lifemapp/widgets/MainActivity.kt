package com.kalai73.lifemapp.widgets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappAppTheme
import com.kalai73.lifemapp.widgets.core.weblauncher.WebLauncher
import com.kalai73.lifemapp.widgets.ui.AppViewModel
import com.kalai73.lifemapp.widgets.ui.LifeMappAppContent
import com.kalai73.lifemapp.widgets.ui.TodayViewModel
import com.kalai73.lifemapp.widgets.widget.DeepLink
import kotlinx.coroutines.launch

/**
 * The app's only screen host: sign-in, encryption unlock, the native Today screen and settings. Also receives the
 * Google sign-in redirect (lifemappwidgets://auth/callback) and the widget's taps ([DeepLink]): an item opens in
 * the Today screen's item modal, an event or task in its details modal, a section scrolls Today to it. Widget taps
 * never open the web app.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val todayViewModel: TodayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
        // A recreated activity keeps its intent: the item was opened already
        if (savedInstanceState == null) handleWidgetTap(intent)
        setContent {
            val cache by viewModel.cache.collectAsState()
            // This device's choice, else the web's (signed out: the default unless the device chose one)
            val scheme = cache.colorScheme
            val style = viewModel.uiStyle.takeIf { cache.snapshot != null } ?: com.kalai73.lifemapp.widgets.core.model.UiStyle.PREDATOR
            val dark = cache.themeMode.isDark(isSystemInDarkTheme())
            // Status and navigation bar icons follow the chosen theme, not only the system one
            LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            LifeMappAppTheme(scheme = scheme, style = style, dark = dark) {
                LifeMappAppContent(
                    viewModel = viewModel,
                    todayViewModel = todayViewModel,
                    onGoogleSignIn = ::startGoogleSignIn,
                    onOpenWeb = { path -> startActivity(WebLauncher.routeIntent(this, path)) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.syncIfStale()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthCallback(intent)
        handleWidgetTap(intent)
    }

    private fun startGoogleSignIn() {
        lifecycleScope.launch {
            val url = viewModel.googleSignInUrl()
            WebLauncher.openCustomTab(this@MainActivity, Uri.parse(url), viewModel.colorScheme)
        }
    }

    private fun handleAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "lifemappwidgets" || data.host != "auth") return
        val error = data.getQueryParameter("error_description") ?: data.getQueryParameter("error")
        val code = data.getQueryParameter("code")
        when {
            code != null -> viewModel.completeGoogleSignIn(code)
            error != null -> viewModel.reportError(error)
        }
        // Don't handle the same redirect again after a configuration change
        setIntent(Intent(intent).setData(null))
    }

    /** A widget tap ([DeepLink]): shown on Today, then dropped from the intent so it isn't handled twice */
    private fun handleWidgetTap(intent: Intent?) {
        val link = DeepLink.from(intent) ?: return
        todayViewModel.open(link)
        setIntent(DeepLink.consumed(intent!!))
    }
}
