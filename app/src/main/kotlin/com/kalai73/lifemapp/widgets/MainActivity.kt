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
import kotlinx.coroutines.launch

/**
 * The app's only screen host: sign-in, encryption unlock and settings. Everything else happens in the
 * widgets and the web app. Also receives the Google sign-in redirect (lifemappwidgets://auth/callback).
 */
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
        setContent {
            val cache by viewModel.cache.collectAsState()
            val scheme = viewModel.colorScheme.takeIf { cache.snapshot != null } ?: com.kalai73.lifemapp.widgets.core.model.ColorSchemeName.DEFAULT
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
                    onGoogleSignIn = ::startGoogleSignIn,
                    onOpenWeb = { path -> startActivity(WebLauncher.routeIntent(this, path)) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthCallback(intent)
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
}
