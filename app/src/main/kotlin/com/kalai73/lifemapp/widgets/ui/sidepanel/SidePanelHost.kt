package com.kalai73.lifemapp.widgets.ui.sidepanel

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.presentation.ShortcutUi
import com.kalai73.lifemapp.widgets.ui.Ic
import com.kalai73.lifemapp.widgets.ui.LocalTok
import com.kalai73.lifemapp.widgets.ui.Tok
import com.kalai73.lifemapp.widgets.ui.framed

/** What the Today and Monthly pages use to open the side panel (setLinksPanelOpen, openLinksPanelOnDomain). */
internal interface PanelOpener {
    fun open(mode: PanelMode? = null)
    fun openOnDomain(domainId: String)
    /** The Links tab on its quick links landing (the Shortcuts card's Links header, quick-links-section "Manage") */
    fun openQuickLinks()
}

internal val LocalPanel = staticCompositionLocalOf<PanelOpener?> { null }

internal fun SidePanelViewModel.opener(): PanelOpener = object : PanelOpener {
    override fun open(mode: PanelMode?) = this@opener.open(mode)
    override fun openOnDomain(domainId: String) = this@opener.openOnDomain(domainId)
    override fun openQuickLinks() {
        this@opener.open(PanelMode.LINKS)
        this@opener.showLanding()
    }
}

/**
 * The web's side panel on a phone: a full-height sheet sliding in from the left over the dashboard, a scrim behind
 * it, the [ModeToggle] (Editor | Links | Alerts | Feeds) and close in its header. System Back steps out of a sub-pane
 * first, then closes it (LinksPanel's Escape and the URL's history entry).
 */
@Composable
internal fun SidePanelHost(
    vm: SidePanelViewModel,
    shortcuts: List<ShortcutUi>,
    openLink: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    val t = LocalTok.current
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.messageShown()
        }
    }
    BackHandler(enabled = state.open) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(state.open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (t.dark) 0.6f else 0.4f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.close() },
            )
        }
        AnimatedVisibility(
            state.open,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            Column(
                Modifier.fillMaxHeight().fillMaxWidth(0.96f)
                    .background(t.card)
                    .border(1.dp, t.line)
                    // Taps on the sheet must not reach the scrim
                    // (system bars and the keyboard are already inset by the screen's safeDrawingPadding)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Row(
                    Modifier.fillMaxWidth().background(t.well.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeToggle(state.mode, vm::setMode, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    PanelIconButton(LucideIcons.CLOSE, stringResource(R.string.panel_close)) { vm.close() }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    // Switching to and from Feeds swaps the content in place, as switchSidePanel does
                    Crossfade(state.mode == PanelMode.FEEDS, label = "panel") { feeds ->
                        if (feeds) FeedsPane(vm, openLink)
                        else LinksPane(vm, state, shortcuts, openLink, onOpenWeb, onUnlock)
                    }
                }
            }
        }
    }
}

/** LinksStreamModeToggle: four segments, the active one filled with the accent. */
@Composable
private fun ModeToggle(mode: PanelMode, onMode: (PanelMode) -> Unit, modifier: Modifier) {
    val t = LocalTok.current
    val entries = listOf(
        Triple(PanelMode.EDITOR, LucideIcons.CONTENT, R.string.panel_editor),
        Triple(PanelMode.LINKS, LucideIcons.LINK_2, R.string.panel_links),
        Triple(PanelMode.ALERTS, LucideIcons.ALERTS, R.string.panel_alerts),
        Triple(PanelMode.FEEDS, LucideIcons.RSS, R.string.panel_feeds),
    )
    Row(
        modifier.framed(t, t.well.copy(alpha = 0.5f), edge = t.line, cut = 6.dp).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        entries.forEach { (m, icon, label) ->
            val active = m == mode
            Row(
                Modifier.weight(1f).height(34.dp)
                    .framed(t, if (active) (if (t.predator) t.pd.accent.copy(alpha = 0.15f) else t.accent) else Color.Transparent,
                        edge = if (active && t.predator) t.accent else Color.Transparent, cut = 4.dp)
                    .clickable(role = Role.RadioButton, onClickLabel = stringResource(label)) { onMode(m) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (active) (if (t.predator) t.ink else t.onAccent) else t.dim
                Ic(icon, 14.dp, ink, desc = stringResource(label))
                Spacer(Modifier.width(4.dp))
                Text(t.s(stringResource(label)), style = if (t.predator) t.label(10.sp, ink) else t.body(11.sp, ink, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

// ── Shared pieces ───────────────────────────────────────────────────────────

@Composable
internal fun PanelIconButton(icon: Int, description: String, tint: Color = LocalTok.current.dim, size: Dp = 32.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ic(icon, 16.dp, if (enabled) tint else tint.copy(alpha = 0.4f), desc = description)
    }
}

/**
 * The streams' scope button on a phone (LinksStream / FeedStream, FolderTree): where the stream is in the hierarchy
 * ("Domain › Subdomain › Item"); tapping it opens the tree.
 */
@Composable
internal fun ScopeButton(label: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        modifier.framed(t, t.well.copy(alpha = 0.4f), edge = t.line, cut = 4.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.panel_folders), onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(LucideIcons.FOLDER, 15.dp, t.dim)
        Spacer(Modifier.width(8.dp))
        Text(label, style = if (t.predator) t.label(13.sp) else t.body(14.sp, t.ink, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Ic(LucideIcons.CHEVRON_RIGHT, 15.dp, t.dim)
    }
}

/** A small pill / chamfered chip: filters, sort, layout, presets. */
@Composable
internal fun PanelChip(label: String, selected: Boolean, icon: Int? = null, count: Int? = null, onClick: () -> Unit) {
    val t = LocalTok.current
    val fill = if (selected) (if (t.predator) t.pd.accent.copy(alpha = 0.15f) else t.accent) else Color.Transparent
    val ink = if (selected) (if (t.predator) t.ink else t.onAccent) else t.text
    Row(
        Modifier.framed(t, fill, edge = if (selected) t.accent else t.line, cut = 4.dp)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Ic(it, 12.dp, ink); Spacer(Modifier.width(4.dp)) }
        Text(t.s(label), style = if (t.predator) t.label(10.sp, ink) else t.body(12.sp, ink, FontWeight.Medium), maxLines = 1)
        count?.let {
            Spacer(Modifier.width(4.dp))
            Text("$it", style = t.caption(10.sp, if (selected) ink else t.dim))
        }
    }
}

/** The stream's search box */
@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTok.current
    Row(
        modifier.framed(t, t.well.copy(alpha = 0.4f), edge = t.line, cut = 4.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(LucideIcons.SEARCH, 14.dp, t.dim)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(stringResource(R.string.panel_search_hint), style = t.body(13.sp, t.dim.copy(alpha = 0.6f)))
            BasicTextField(value, onChange, singleLine = true, textStyle = t.body(13.sp), cursorBrush = SolidColor(t.accent), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) PanelIconButton(LucideIcons.CLOSE, stringResource(R.string.panel_clear), size = 20.dp) { onChange("") }
    }
}

@Composable
internal fun EmptyState(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = t.body(13.sp, t.dim, FontWeight.Medium))
        action?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = t.body(13.sp, t.accent, FontWeight.SemiBold), modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(8.dp))
        }
    }
}

/** A header row inside a pane: an optional leading action, a title, trailing actions */
@Composable
internal fun PaneBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { PanelIconButton(LucideIcons.BACK, stringResource(R.string.panel_back), tint = t.ink, onClick = it) }
        Text(
            t.s(title), style = if (t.predator) t.label(13.sp) else t.body(14.sp, t.ink, FontWeight.Bold),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        actions()
    }
}

@Composable
internal fun Divider(t: Tok = LocalTok.current) = Box(Modifier.fillMaxWidth().height(1.dp).background(t.line.copy(alpha = 0.6f)))

/** A row's tap area with the usual padding */
internal fun Modifier.rowClick(onClick: () -> Unit): Modifier = clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp)

@Composable
internal fun BoxScope.Centered(content: @Composable () -> Unit) = Box(Modifier.align(Alignment.Center)) { content() }
