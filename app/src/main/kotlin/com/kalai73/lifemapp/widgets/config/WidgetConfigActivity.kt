package com.kalai73.lifemapp.widgets.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappAppTheme
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.widget.dashboard.DashboardWidget
import com.kalai73.lifemapp.widgets.widget.events.EventsConfig
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler
import kotlinx.coroutines.launch

/** Per-instance settings of the Dashboard widget's events: how many days ahead, and which calendars. */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget with its defaults (configuration is optional)
        setResult(RESULT_OK, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch {
            val graph = LifeMappGraph.get(applicationContext)
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            val prefs = getAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) as Preferences
            val snapshot = graph.cache.current().snapshot
            val calendars = snapshot?.events?.mapNotNull { it.calendarName }?.distinct()?.sorted().orEmpty()
            val scheme = ColorSchemeName.from(snapshot?.settings?.colorScheme)

            setContent {
                LifeMappAppTheme(scheme) {
                    ConfigScreen(
                        initialDays = prefs[EventsConfig.DaysAhead] ?: EventsConfig.DEFAULT_DAYS,
                        initialHidden = prefs[EventsConfig.HiddenCalendars] ?: emptySet(),
                        calendars = calendars,
                    ) { days, hidden ->
                        lifecycleScope.launch {
                            updateAppWidgetState(applicationContext, glanceId) { state ->
                                state[EventsConfig.DaysAhead] = days
                                state[EventsConfig.HiddenCalendars] = hidden
                            }
                            DashboardWidget().update(applicationContext, glanceId)
                            SyncScheduler.syncNow(applicationContext)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun ConfigScreen(
    initialDays: Int,
    initialHidden: Set<String>,
    calendars: List<String>,
    onSave: (Int, Set<String>) -> Unit,
) {
    var days by remember { mutableStateOf(initialDays) }
    var hidden by remember { mutableStateOf(initialHidden) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.config_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.config_days), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventsConfig.DAY_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = days == option,
                        onClick = { days = option },
                        label = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.config_days_option, option, option)) },
                    )
                }
            }
            Text(stringResource(R.string.config_calendars), style = MaterialTheme.typography.titleMedium)
            if (calendars.isEmpty()) {
                Text(stringResource(R.string.config_no_calendars), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            calendars.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { hidden = if (name in hidden) hidden - name else hidden + name },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = name !in hidden, onCheckedChange = { hidden = if (it) hidden - name else hidden + name })
                    Text(name)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onSave(days, hidden) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
    }
}
