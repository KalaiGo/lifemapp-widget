package com.kalai73.lifemapp.widgets.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.LocalPdPalette
import com.kalai73.lifemapp.widgets.core.designsystem.LocalUiStyle
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdButton
import com.kalai73.lifemapp.widgets.core.designsystem.PdDestructiveButton
import com.kalai73.lifemapp.widgets.core.designsystem.PdFilterChip
import com.kalai73.lifemapp.widgets.core.designsystem.PdHexIconWell
import com.kalai73.lifemapp.widgets.core.designsystem.PdOutlinedButton
import com.kalai73.lifemapp.widgets.core.designsystem.PdSectionHeader
import com.kalai73.lifemapp.widgets.core.designsystem.PdTextButton
import com.kalai73.lifemapp.widgets.core.designsystem.pdCaptionStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdLabelStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdPanel
import com.kalai73.lifemapp.widgets.core.designsystem.pdTitleStyle
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.ThemeMode
import com.kalai73.lifemapp.widgets.core.model.UiStyle
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
                Screen.LOADING -> Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
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
    val style = LocalUiStyle.current
    val pd = LocalPdPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (style == UiStyle.PREDATOR) {
            PdHexIconWell(iconRes = LucideIcons.TARGET, contentDescription = null, size = 36.dp, palette = pd)
        } else {
            Icon(painterResource(LucideIcons.TARGET), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                "LIFE MAPP",
                style = if (style == UiStyle.PREDATOR) pdTitleStyle(size = 20.sp, color = pd.ink, letterSpacing = 0.12.em)
                else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary),
            )
            Text(
                stringResource(R.string.app_tagline),
                style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 11.sp, color = pd.dim)
                else MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
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
    val style = LocalUiStyle.current
    val pd = LocalPdPalette.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (style == UiStyle.PREDATOR) {
        PdSectionHeader(title = stringResource(R.string.sign_in_title), palette = pd)
    } else {
        Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.sign_in_body),
        style = if (style == UiStyle.PREDATOR) MaterialTheme.typography.bodyMedium.copy(color = pd.dim)
        else MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
    Spacer(Modifier.height(18.dp))

    val textFieldColors = if (style == UiStyle.PREDATOR) {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = pd.ink,
            unfocusedBorderColor = pd.edge,
            focusedLabelColor = pd.ink,
            unfocusedLabelColor = pd.dim,
            focusedContainerColor = pd.raised,
            unfocusedContainerColor = pd.raised,
            cursorColor = pd.ink,
            focusedTextColor = pd.text,
            unfocusedTextColor = pd.text,
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = {
            Text(
                if (style == UiStyle.PREDATOR) stringResource(R.string.email).uppercase() else stringResource(R.string.email),
                style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 11.sp) else TextStyle.Default,
            )
        },
        shape = if (style == UiStyle.PREDATOR) RectangleShape else OutlinedTextFieldDefaults.shape,
        colors = textFieldColors,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = {
            Text(
                if (style == UiStyle.PREDATOR) stringResource(R.string.password).uppercase() else stringResource(R.string.password),
                style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 11.sp) else TextStyle.Default,
            )
        },
        shape = if (style == UiStyle.PREDATOR) RectangleShape else OutlinedTextFieldDefaults.shape,
        colors = textFieldColors,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    )
    ErrorText(state.error)
    Spacer(Modifier.height(16.dp))

    if (style == UiStyle.PREDATOR) {
        PdButton(
            onClick = { onSignIn(email, password) },
            enabled = !state.busy && email.isNotBlank() && password.isNotEmpty(),
            busy = state.busy,
            modifier = Modifier.fillMaxWidth(),
            palette = pd,
        ) {
            Text(stringResource(R.string.sign_in).uppercase())
        }
        Spacer(Modifier.height(10.dp))
        PdOutlinedButton(
            onClick = onGoogle,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            palette = pd,
        ) {
            Text(stringResource(R.string.sign_in_google).uppercase())
        }
    } else {
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
}

@Composable
private fun UnlockScreen(state: AppUiState, onUnlock: (String) -> Unit, onSkip: () -> Unit) {
    val style = LocalUiStyle.current
    val pd = LocalPdPalette.current
    var password by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (style == UiStyle.PREDATOR) {
            PdHexIconWell(iconRes = LucideIcons.LOCK, contentDescription = null, size = 32.dp, palette = pd)
            Spacer(Modifier.size(10.dp))
            PdSectionHeader(title = stringResource(R.string.unlock_title), palette = pd)
        } else {
            Icon(painterResource(LucideIcons.LOCK), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.unlock_body),
        style = if (style == UiStyle.PREDATOR) MaterialTheme.typography.bodyMedium.copy(color = pd.dim)
        else MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
    Spacer(Modifier.height(16.dp))

    val textFieldColors = if (style == UiStyle.PREDATOR) {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = pd.ink,
            unfocusedBorderColor = pd.edge,
            focusedLabelColor = pd.ink,
            unfocusedLabelColor = pd.dim,
            focusedContainerColor = pd.raised,
            unfocusedContainerColor = pd.raised,
            cursorColor = pd.ink,
            focusedTextColor = pd.text,
            unfocusedTextColor = pd.text,
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = {
            Text(
                if (style == UiStyle.PREDATOR) stringResource(R.string.encryption_password).uppercase() else stringResource(R.string.encryption_password),
                style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 11.sp) else TextStyle.Default,
            )
        },
        shape = if (style == UiStyle.PREDATOR) RectangleShape else OutlinedTextFieldDefaults.shape,
        colors = textFieldColors,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    )
    ErrorText(state.error)
    Spacer(Modifier.height(16.dp))

    if (style == UiStyle.PREDATOR) {
        PdButton(
            onClick = { onUnlock(password) },
            enabled = !state.busy && password.isNotEmpty(),
            busy = state.busy,
            modifier = Modifier.fillMaxWidth(),
            palette = pd,
        ) {
            Text(stringResource(R.string.unlock).uppercase())
        }
        Spacer(Modifier.height(8.dp))
        PdTextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth(), palette = pd) {
            Text(stringResource(R.string.not_now).uppercase())
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.unlock_recovery_note),
            style = pdCaptionStyle(size = 11.sp, color = pd.dim),
        )
    } else {
        Button(onClick = { onUnlock(password) }, enabled = !state.busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.unlock))
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.not_now)) }
        Text(stringResource(R.string.unlock_recovery_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val style = LocalUiStyle.current
    val pd = LocalPdPalette.current
    if (style == UiStyle.PREDATOR) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .pdPanel(palette = pd, cut = 12.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PdSectionHeader(title = title, palette = pd)
                content()
            }
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content()
            }
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, cache: CachedState, viewModel: AppViewModel, onOpenWeb: (String) -> Unit) {
    val context = LocalContext.current
    val style = LocalUiStyle.current
    val pd = LocalPdPalette.current

    val signedInText = stringResource(R.string.signed_in_as, state.email ?: "—")
    Text(
        if (style == UiStyle.PREDATOR) signedInText.uppercase() else signedInText,
        style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 11.sp, color = pd.dim) else MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(12.dp))

    Section(stringResource(R.string.sync_title)) {
        val last = cache.lastSuccessAt.takeIf { it > 0 }?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
        val syncLabel = if (last != null) stringResource(R.string.last_sync, last) else stringResource(R.string.never_synced)
        Text(
            if (style == UiStyle.PREDATOR) syncLabel.uppercase() else syncLabel,
            style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 11.sp, color = pd.text) else MaterialTheme.typography.bodyMedium,
        )
        val error = when (cache.lastError) {
            SyncError.NETWORK -> stringResource(R.string.error_network)
            SyncError.SERVER -> stringResource(R.string.sync_error_server)
            SyncError.UNAUTHORIZED -> stringResource(R.string.sync_error_unauthorized)
            SyncError.LOCKED_DEVICE, null -> null
        }
        ErrorText(error)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val syncing = cache.isSyncing(System.currentTimeMillis())
            if (style == UiStyle.PREDATOR) {
                PdButton(onClick = viewModel::syncNow, enabled = !syncing, busy = syncing, palette = pd) {
                    Text(stringResource(R.string.sync_now).uppercase())
                }
                PdOutlinedButton(onClick = { onOpenWeb(WebRoutes.TODAY) }, palette = pd) {
                    Text(stringResource(R.string.open_web).uppercase())
                }
            } else {
                Button(onClick = viewModel::syncNow, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sync_now))
                }
                OutlinedButton(onClick = { onOpenWeb(WebRoutes.TODAY) }) { Text(stringResource(R.string.open_web)) }
            }
        }
    }

    Section(stringResource(R.string.widgets_title)) {
        val countText = androidx.compose.ui.res.pluralStringResource(R.plurals.widgets_placed, state.placedWidgets, state.placedWidgets)
        Text(
            countText,
            style = if (style == UiStyle.PREDATOR) pdLabelStyle(size = 14.sp, color = pd.text) else MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.widgets_hint),
            style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 11.sp, color = pd.dim)
            else MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            if (style == UiStyle.PREDATOR) {
                PdOutlinedButton(
                    onClick = { manager.requestPinAppWidget(ComponentName(context, DashboardWidgetReceiver::class.java), null, null) },
                    modifier = Modifier.fillMaxWidth(),
                    palette = pd,
                ) {
                    Text(stringResource(R.string.pin_dashboard).uppercase())
                }
            } else {
                OutlinedButton(
                    onClick = { manager.requestPinAppWidget(ComponentName(context, DashboardWidgetReceiver::class.java), null, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.pin_dashboard)) }
            }
        }
    }

    Section(stringResource(R.string.appearance_title)) {
        Text(
            stringResource(R.string.appearance_hint),
            style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 11.sp, color = pd.dim)
            else MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (mode in ThemeMode.entries) {
                val label = stringResource(
                    when (mode) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                )
                val selected = cache.themeMode == mode
                if (style == UiStyle.PREDATOR) {
                    PdFilterChip(selected = selected, onClick = { viewModel.setThemeMode(mode) }, label = label, palette = pd)
                } else {
                    FilterChip(selected = selected, onClick = { viewModel.setThemeMode(mode) }, label = { Text(label) })
                }
            }
        }
    }

    if (state.encryptionEnabled) {
        Section(stringResource(R.string.encryption_title)) {
            val encText = stringResource(if (state.unlocked) R.string.encryption_unlocked else R.string.encryption_locked)
            Text(
                if (style == UiStyle.PREDATOR) encText.uppercase() else encText,
                style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 12.sp, color = pd.text) else MaterialTheme.typography.bodyMedium,
            )
            if (state.unlocked) {
                if (style == UiStyle.PREDATOR) {
                    PdOutlinedButton(onClick = viewModel::lock, palette = pd) {
                        Text(stringResource(R.string.lock).uppercase())
                    }
                } else {
                    OutlinedButton(onClick = viewModel::lock) { Text(stringResource(R.string.lock)) }
                }
            } else {
                if (style == UiStyle.PREDATOR) {
                    PdButton(onClick = viewModel::showUnlock, palette = pd) {
                        Text(stringResource(R.string.unlock).uppercase())
                    }
                } else {
                    Button(onClick = viewModel::showUnlock) { Text(stringResource(R.string.unlock)) }
                }
            }
        }
    }

    Section(stringResource(R.string.battery_title)) {
        Text(
            stringResource(R.string.battery_body),
            style = if (style == UiStyle.PREDATOR) MaterialTheme.typography.bodySmall.copy(color = pd.dim)
            else MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        val ignoring = isIgnoringBatteryOptimizations(context)
        val batStatus = stringResource(if (ignoring) R.string.battery_unrestricted else R.string.battery_optimized)
        Text(
            if (style == UiStyle.PREDATOR) batStatus.uppercase() else batStatus,
            style = if (style == UiStyle.PREDATOR) pdCaptionStyle(size = 12.sp, color = pd.text) else MaterialTheme.typography.bodyMedium,
        )
        if (!ignoring) {
            if (style == UiStyle.PREDATOR) {
                PdOutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    palette = pd,
                ) {
                    Text(stringResource(R.string.battery_action).uppercase())
                }
            } else {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.battery_action)) }
            }
        }
    }

    Section(stringResource(R.string.account_title)) {
        if (style == UiStyle.PREDATOR) {
            PdTextButton(onClick = viewModel::relinkWeb, palette = pd) {
                Text(stringResource(R.string.relink_web).uppercase())
            }
            HorizontalDivider(color = pd.edge)
            PdDestructiveButton(onClick = viewModel::signOut, enabled = !state.busy, palette = pd) {
                Text(stringResource(R.string.sign_out).uppercase())
            }
        } else {
            TextButton(onClick = viewModel::relinkWeb) { Text(stringResource(R.string.relink_web)) }
            HorizontalDivider()
            TextButton(onClick = viewModel::signOut, enabled = !state.busy) {
                Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    ErrorText(state.error)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
