package com.kalai73.lifemapp.widgets.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.dashboard.DashboardWidgetReceiver
import java.text.DateFormat
import java.util.Date

@Composable
fun LifeMappAppContent(viewModel: AppViewModel, onGoogleSignIn: () -> Unit, onOpenWeb: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val cache by viewModel.cache.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Brand()
            Spacer(Modifier.height(24.dp))
            when (state.screen) {
                Screen.LOADING -> Centered { CircularProgressIndicator() }
                Screen.SIGN_IN -> SignInScreen(state, viewModel::signIn, onGoogleSignIn)
                Screen.UNLOCK -> UnlockScreen(state, viewModel::unlock, viewModel::skipUnlock)
                Screen.HOME -> HomeScreen(state, cache, viewModel, onOpenWeb)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(LucideIcons.TARGET), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(10.dp))
        Column {
            Text("LIFE MAPP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error == null) return
    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SignInScreen(state: AppUiState, onSignIn: (String, String) -> Unit, onGoogle: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.sign_in_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.password)) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    )
    ErrorText(state.error)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { onSignIn(email, password) },
        enabled = !state.busy && email.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sign_in))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onGoogle, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sign_in_google))
    }
}

@Composable
private fun UnlockScreen(state: AppUiState, onUnlock: (String) -> Unit, onSkip: () -> Unit) {
    var password by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(LucideIcons.LOCK), null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
    Text(stringResource(R.string.unlock_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.encryption_password)) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    )
    ErrorText(state.error)
    Spacer(Modifier.height(12.dp))
    Button(onClick = { onUnlock(password) }, enabled = !state.busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.unlock))
    }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.not_now)) }
    Text(stringResource(R.string.unlock_recovery_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, cache: CachedState, viewModel: AppViewModel, onOpenWeb: (String) -> Unit) {
    val context = LocalContext.current
    Text(stringResource(R.string.signed_in_as, state.email ?: "—"), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))

    Section(stringResource(R.string.sync_title)) {
        val last = cache.lastSuccessAt.takeIf { it > 0 }?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
        Text(if (last != null) stringResource(R.string.last_sync, last) else stringResource(R.string.never_synced))
        val error = when (cache.lastError) {
            SyncError.NETWORK -> stringResource(R.string.error_network)
            SyncError.SERVER -> stringResource(R.string.sync_error_server)
            SyncError.UNAUTHORIZED -> stringResource(R.string.sync_error_unauthorized)
            SyncError.LOCKED_DEVICE, null -> null
        }
        ErrorText(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val syncing = cache.isSyncing(System.currentTimeMillis())
            Button(onClick = viewModel::syncNow, enabled = !syncing) {
                if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sync_now))
            }
            OutlinedButton(onClick = { onOpenWeb(WebRoutes.TODAY) }) { Text(stringResource(R.string.open_web)) }
        }
    }

    Section(stringResource(R.string.widgets_title)) {
        Text(androidx.compose.ui.res.pluralStringResource(R.plurals.widgets_placed, state.placedWidgets, state.placedWidgets), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.widgets_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            OutlinedButton(
                onClick = { manager.requestPinAppWidget(ComponentName(context, DashboardWidgetReceiver::class.java), null, null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.pin_dashboard)) }
        }
    }

    if (state.encryptionEnabled) {
        Section(stringResource(R.string.encryption_title)) {
            Text(stringResource(if (state.unlocked) R.string.encryption_unlocked else R.string.encryption_locked))
            if (state.unlocked) {
                OutlinedButton(onClick = viewModel::lock) { Text(stringResource(R.string.lock)) }
            } else {
                Button(onClick = viewModel::showUnlock) { Text(stringResource(R.string.unlock)) }
            }
        }
    }

    Section(stringResource(R.string.battery_title)) {
        Text(stringResource(R.string.battery_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val ignoring = isIgnoringBatteryOptimizations(context)
        Text(stringResource(if (ignoring) R.string.battery_unrestricted else R.string.battery_optimized))
        if (!ignoring) {
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(stringResource(R.string.battery_action)) }
        }
    }

    Section(stringResource(R.string.account_title)) {
        TextButton(onClick = viewModel::relinkWeb) { Text(stringResource(R.string.relink_web)) }
        HorizontalDivider()
        TextButton(onClick = viewModel::signOut, enabled = !state.busy) {
            Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
        }
    }
    ErrorText(state.error)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
