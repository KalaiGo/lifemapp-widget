package com.kalai73.lifemapp.widgets.widget.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdDrawables
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.core.designsystem.PredatorColors
import com.kalai73.lifemapp.widgets.core.designsystem.WidgetStyle
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import com.kalai73.lifemapp.widgets.core.weblauncher.WebLauncher
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import com.kalai73.lifemapp.widgets.widget.R

val LocalWidgetStyle = staticCompositionLocalOf { WidgetStyle.of(UiStyle.CLASSIC) }

/**
 * Theme, background card and style for every LIFE MAPP widget. [content] emits one element filling the card
 * and must apply the modifier it gets (the inner padding, in Predator).
 */
@Composable
fun LifeMappWidgetTheme(ui: WidgetUi, content: @Composable (GlanceModifier) -> Unit) {
    val style = WidgetStyle.of(ui.uiStyle)
    if (style.isPredator) {
        val pd = PdColors(ui.colorScheme)
        GlanceTheme(colors = PredatorColors.glance(ui.colorScheme)) {
            CompositionLocalProvider(LocalWidgetStyle provides style, LocalPd provides pd) {
                // The panel: chamfered, 1dp edge, accent on the cut corners (styleguide §5.2, §5.3).
                // Inset so the launcher's rounded-corner clip (Android 12+) doesn't swallow the cuts.
                // The layers and the content share one Box (padding on the Box insets every layer, the content
                // pads itself), so the panel costs a single view level: RemoteViews allow at most 10 nested levels.
                Box(modifier = GlanceModifier.fillMaxSize().padding(launcherCornerInset())) {
                    PdFrameLayers(PdShape.CHAMFER_LG, edge = pd.edge, fill = pd.panel, corners = PdDrawables.CHAMFER_LG_CORNERS to pd.accent)
                    content(GlanceModifier.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
        return
    }
    GlanceTheme(colors = LifeMappColors.glance(ui.colorScheme)) {
        CompositionLocalProvider(LocalWidgetStyle provides style) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.background)
                    .cornerRadius(style.cornerRadius)
                    .padding(12.dp),
            ) {
                content(GlanceModifier)
            }
        }
    }
}

/**
 * Launchers on Android 12+ clip widgets to the system corner radius R. A 45° chamfer stays fully visible
 * when the panel is inset by R·(1 − 1/√2), the point where the corner arc meets the diagonal.
 */
@Composable
private fun launcherCornerInset(): Dp {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return 0.dp
    val resources = LocalContext.current.resources
    val radius = runCatching { resources.getDimension(android.R.dimen.system_app_widget_background_radius) }.getOrDefault(0f) /
        resources.displayMetrics.density
    return (radius * 0.2929f).coerceIn(0f, 12f).dp
}

/** Body text: user content (titles, names) keeps the default face in both styles. */
@Composable
fun textStyle(
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Normal,
    color: ColorProvider = GlanceTheme.colors.onBackground,
): TextStyle = TextStyle(color = color, fontSize = size, fontWeight = weight)

/** pd-label: short fixed labels. Predator: condensed bold italic (pass the text through WidgetStyle.label). */
@Composable
fun labelStyle(
    size: TextUnit = 13.sp,
    color: ColorProvider = GlanceTheme.colors.primary,
    weight: FontWeight = FontWeight.Bold,
): TextStyle {
    val style = LocalWidgetStyle.current
    return TextStyle(
        color = color, fontSize = size, fontWeight = weight,
        fontStyle = if (style.italicLabels) FontStyle.Italic else null,
        fontFamily = style.labelFamily,
    )
}

/** Data captions (dates, times, info lines). Predator: monospace. */
@Composable
fun captionStyle(
    size: TextUnit = 11.sp,
    color: ColorProvider = GlanceTheme.colors.onSurfaceVariant,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = LocalWidgetStyle.current.captionFamily)

/** Numbers (counters, scores). Predator: condensed bold italic. */
@Composable
fun numberStyle(size: TextUnit = 16.sp, color: ColorProvider = GlanceTheme.colors.onBackground): TextStyle =
    labelStyle(size = size, color = color, weight = FontWeight.Bold)

@Composable
fun Icon(res: Int, description: String?, size: Dp = 18.dp, tint: ColorProvider = GlanceTheme.colors.primary, modifier: GlanceModifier = GlanceModifier) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

fun color(value: Color): ColorProvider = ColorProvider(value)

/** Opens a LIFE MAPP web route (signed in) in a Custom Tab. */
fun openRoute(context: Context, path: String): Action = actionStartActivity(WebLauncher.routeIntent(context, path))

/** Opens an external link with whatever app handles it. */
fun openExternal(url: String): Action = actionStartActivity(WebLauncher.externalIntent(url))

fun openApp(context: Context): Action = actionStartActivity(LifeMappWidgets.appIntent(context))

/** Widget header: title (tapping opens [onTitle]), optional trailing content, reload button with spinner. */
@Composable
fun WidgetHeader(
    title: String,
    icon: Int,
    ui: WidgetUi,
    onTitle: Action,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val style = LocalWidgetStyle.current
    if (style.isPredator) {
        // Hex icon well, pd-label title, mono caption, then the accent rule (styleguide §5.3 pd-rule)
        Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = GlanceModifier.defaultWeight().clickable(onTitle), verticalAlignment = Alignment.CenterVertically) {
                    PdIconWell(icon, null)
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Text(style.label(title), style = labelStyle(size = 15.sp), maxLines = 1)
                        if (subtitle != null) Text(style.label(subtitle), style = captionStyle(size = 10.sp), maxLines = 1)
                    }
                }
                trailing()
                Spacer(GlanceModifier.width(4.dp))
                ReloadButton(ui)
            }
            PdRule()
        }
        return
    }
    Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.defaultWeight().clickable(onTitle), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, size = 18.dp)
            Spacer(GlanceModifier.width(6.dp))
            Column {
                Text(style.label(title), style = textStyle(size = 15.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.primary), maxLines = 1)
                if (subtitle != null) Text(subtitle, style = textStyle(size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            }
        }
        trailing()
        ReloadButton(ui)
    }
}

/**
 * A square header button. Predator: a chamfered icon button, framed panel or, when [primary], the accent
 * fill with the icon in pd-on (styleguide §6 Button).
 */
@Composable
fun HeaderButton(
    icon: Int,
    description: String?,
    onClick: Action,
    primary: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val pd = LocalPd.current
    if (pd != null) {
        PdFrame(
            PdShape.CHAMFER_SM,
            edge = if (primary) pd.accentHi else pd.edge,
            fill = if (primary) pd.accent else pd.panel,
            modifier = GlanceModifier.size(28.dp).clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (content != null) content() else Icon(icon, description, size = 16.dp, tint = if (primary) pd.on else pd.ink)
        }
    } else {
        Box(
            modifier = GlanceModifier.size(32.dp).cornerRadius(16.dp).clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (content != null) content() else Icon(icon, description, size = if (primary) 20.dp else 18.dp)
        }
    }
}

@Composable
fun ReloadButton(ui: WidgetUi) {
    val description = LocalContext.current.getString(R.string.reload)
    val predator = LocalPd.current != null
    HeaderButton(LucideIcons.RELOAD, description, actionRunCallback<ReloadAction>()) {
        if (ui.syncing) {
            CircularProgressIndicator(modifier = GlanceModifier.size(if (predator) 14.dp else 18.dp), color = GlanceTheme.colors.primary)
        } else {
            Icon(LucideIcons.RELOAD, description, size = if (predator) 15.dp else 18.dp)
        }
    }
}

/** "Updated 10:42", the stale warning, or the last sync error. */
@Composable
fun StatusLine(ui: WidgetUi) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    val text = when {
        ui.error == SyncError.UNAUTHORIZED -> context.getString(R.string.error_signed_out)
        ui.stale && ui.updatedAt != null -> context.getString(R.string.stale, ui.updatedAt)
        ui.error == SyncError.NETWORK -> context.getString(R.string.error_offline)
        ui.error == SyncError.SERVER -> context.getString(R.string.error_server)
        ui.updatedAt != null -> context.getString(R.string.updated_at, ui.updatedAt)
        else -> return
    }
    val warning = ui.stale || ui.error != null
    Text(
        // The info line is a short caption; warnings are sentences and stay readable in sentence case
        if (warning) text else style.label(text),
        style = captionStyle(size = 10.sp, color = if (warning) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant),
        maxLines = 1,
        modifier = if (ui.error == SyncError.UNAUTHORIZED) GlanceModifier.clickable(openApp(context)) else GlanceModifier,
    )
}

/** Shown instead of content while signed out (or before the first unlock after a reboot). */
@Composable
fun SignedOutContent(modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val style = LocalWidgetStyle.current
    Column(
        modifier = modifier.fillMaxSize().clickable(openApp(context)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (style.isPredator) {
            PdIconWell(LucideIcons.TARGET, null, width = 40.dp, iconSize = 20.dp)
            Spacer(GlanceModifier.size(8.dp))
            Text(style.label(context.getString(R.string.sign_in_prompt)), style = labelStyle(size = 14.sp, color = GlanceTheme.colors.onBackground))
            Text(context.getString(R.string.sign_in_hint), style = captionStyle(size = 10.sp))
        } else {
            Icon(LucideIcons.TARGET, null, size = 28.dp)
            Spacer(GlanceModifier.size(6.dp))
            Text(context.getString(R.string.sign_in_prompt), style = textStyle(size = 14.sp, weight = FontWeight.Bold))
            Text(context.getString(R.string.sign_in_hint), style = textStyle(size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
        }
    }
}

@Composable
fun CenteredMessage(text: String, action: Action? = null, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier.fillMaxSize().let { if (action != null) it.clickable(action) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = textStyle(size = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
    }
}

/**
 * Shared root: signed-out prompt, first-load placeholder, or the widget's content. [content] emits one element
 * filling the card and must apply the modifier it gets (see [LifeMappWidgetTheme]).
 */
@Composable
fun WidgetRoot(ui: WidgetUi, content: @Composable (GlanceModifier) -> Unit) {
    LifeMappWidgetTheme(ui) { modifier ->
        when {
            ui.authState == AuthState.SIGNED_OUT -> SignedOutContent(modifier)
            // Never synced yet: "Loading…" while a sync runs, else why it failed (tap to retry)
            !ui.hasData -> {
                val context = LocalContext.current
                val message = when {
                    ui.syncing || ui.error == null -> context.getString(R.string.loading)
                    ui.error == SyncError.UNAUTHORIZED -> context.getString(R.string.error_signed_out)
                    ui.error == SyncError.NETWORK -> context.getString(R.string.first_sync_offline)
                    else -> context.getString(R.string.first_sync_failed)
                }
                val action = if (ui.error == SyncError.UNAUTHORIZED) openApp(context) else actionRunCallback<ReloadAction>()
                CenteredMessage(message, action, modifier)
            }
            else -> content(modifier)
        }
    }
}

/** A small colored pill (calendar dot, list color). */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(modifier = GlanceModifier.size(size).cornerRadius(size / 2).background(color)) {}
}
