package com.kalai73.lifemapp.widgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.presentation.DayKind
import com.kalai73.lifemapp.widgets.core.data.presentation.EventDayUi
import com.kalai73.lifemapp.widgets.core.data.presentation.EventUi
import com.kalai73.lifemapp.widgets.core.data.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.data.presentation.MailTagUi
import com.kalai73.lifemapp.widgets.core.data.presentation.ShortcutUi
import com.kalai73.lifemapp.widgets.core.data.presentation.TaskUi
import com.kalai73.lifemapp.widgets.core.data.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.data.presentation.TopicUi
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.designsystem.ColorContrast
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LocalPdPalette
import com.kalai73.lifemapp.widgets.core.designsystem.LocalUiStyle
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdDrawables
import com.kalai73.lifemapp.widgets.core.designsystem.PdHexagonShape
import com.kalai73.lifemapp.widgets.core.designsystem.PdPalette
import com.kalai73.lifemapp.widgets.core.designsystem.PredatorColors
import com.kalai73.lifemapp.widgets.core.designsystem.pdCaptionStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdChamferShape
import com.kalai73.lifemapp.widgets.core.designsystem.pdLabelStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.SyncError
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import com.kalai73.lifemapp.widgets.widget.events.EventsConfig
import com.kalai73.lifemapp.widgets.widget.voice.VoiceInputActivity
import kotlinx.coroutines.launch

/**
 * Native Today screen (docs/todayScreenPlan.md), drawn in the widgets' look and feel: in Predator the framed hero
 * (greeting, date, counters, weather), numbered hex rows with color bars, the score gauge and the amber warnings
 * bar; in Classic the rounded flat variants. It renders the same [WidgetUi] the widgets use.
 */
@Composable
fun TodayScreen(
    ui: WidgetUi,
    onOpenSettings: () -> Unit,
    onReload: () -> Unit,
    onCompleteTask: (String) -> Unit,
    onRenameItem: (String, String) -> Unit,
    onDeleteItem: (String) -> Unit,
    /** Topic id and the collapsed state to switch to */
    onToggleTopic: (String, Boolean) -> Unit,
    /** Item id and the collapsed state to switch to */
    onToggleItem: (String, Boolean) -> Unit,
    onUnlock: () -> Unit,
    onOpenWeb: (String) -> Unit,
) {
    val t = rememberTokens()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        if (url.startsWith("http", ignoreCase = true)) runCatching { uriHandler.openUri(url) } else onOpenWeb(url)
    }
    val locked = ui.encryption == EncryptionState.LOCKED
    val keys = buildList {
        add("greeting"); add("date"); add("counters")
        if (ui.weather != null || ui.averageRating != null) add("weather")
        add("actions")
        if (locked) add("locked")
        if (ui.integrity != null) add("integrity")
        add("events"); add("tasks"); add("shortcuts")
        // Serenity, Vitality, Stability, Tenacity follow the Integrity card's widgets, as on the web's Today page
        ui.todayDomains.forEach { add(domainKey(it)) }
        add("end")
    }
    val scrollTo = { key: String -> scope.launch { listState.animateScrollToItem(keys.indexOf(key).coerceAtLeast(0)) }; Unit }
    var navOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = navOpen) { navOpen = false }

    CompositionLocalProvider(LocalTok provides t) {
      Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(t.page)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            ) {
                items(keys, key = { it }) { key ->
                    when (key) {
                        "greeting" -> GreetingBlock(ui)
                        "date" -> DateBlock(ui, onReload)
                        "counters" -> Counters(
                            ui,
                            onEvents = { scrollTo("events") },
                            onTasks = { scrollTo("tasks") },
                            onMail = { openLink(ui.mailUrl ?: WebRoutes.MAIL) },
                        )
                        "weather" -> WeatherCard(ui) { onOpenWeb(WebRoutes.SETTINGS) }
                        "actions" -> QuickActions(onOpenWeb)
                        "locked" -> LockedBanner(onUnlock)
                        "integrity" -> DomainCard(ui.integrity!!, onOpenWeb, onRenameItem, onDeleteItem, onToggleTopic, onToggleItem)
                        "events" -> EventsSection(ui, openLink, onOpenWeb)
                        "tasks" -> TasksSection(ui, onCompleteTask, onOpenWeb)
                        "shortcuts" -> ShortcutsSection(ui, openLink, onOpenWeb, onUnlock)
                        "end" -> Spacer(Modifier.height(28.dp))
                        else -> ui.todayDomains.firstOrNull { domainKey(it) == key }?.let {
                            DomainCard(it, onOpenWeb, onRenameItem, onDeleteItem, onToggleTopic, onToggleItem)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
            TopBar(ui, onOpenSettings, onReload, navOpen = navOpen, onToggleNav = { navOpen = !navOpen })
        }
        if (navOpen) {
            NavOverlay(
                ui,
                onDismiss = { navOpen = false },
                onTop = { navOpen = false; scope.launch { listState.animateScrollToItem(0) } },
                onSection = { key -> navOpen = false; scrollTo(key) },
            )
        }
      }
    }
}

// ── Navigation overlay ──────────────────────────────────────────────────────

/**
 * The web's DomainFloatingNav on Today: a scrim and a small centered list above the footer with "Top", the
 * Integrity domain and its sections (events, tasks, shortcuts), then the other Today domains. Tapping an entry
 * scrolls the list to it.
 */
@Composable
private fun BoxScope.NavOverlay(ui: WidgetUi, onDismiss: () -> Unit, onTop: () -> Unit, onSection: (String) -> Unit) {
    val t = LocalTok.current
    Box(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    Column(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).width(200.dp)
            .framed(t, t.card, edge = t.line, cut = 8.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        NavEntry(LucideIcons.ARROW_UP, stringResource(R.string.today_nav_top), t.dim, onTop)
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(t.line))
        ui.integrity?.let { integrity ->
            NavEntry(LucideIcons.domain(integrity.position), domainName(integrity), t.ink) { onSection("integrity") }
        }
        NavEntry(LucideIcons.CALENDAR, stringResource(R.string.today_events), t.dim) { onSection("events") }
        NavEntry(LucideIcons.TASKS, stringResource(R.string.today_tasks), t.dim) { onSection("tasks") }
        NavEntry(LucideIcons.ZAP, stringResource(R.string.today_shortcuts), t.dim) { onSection("shortcuts") }
        ui.todayDomains.forEach { domain ->
            NavEntry(LucideIcons.domain(domain.position), domainName(domain), t.ink) { onSection(domainKey(domain)) }
        }
    }
}

@Composable
private fun NavEntry(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, 16.dp, color)
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            style = if (t.predator) t.label(13.sp, color) else t.body(13.sp, color, FontWeight.Medium),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Style tokens ────────────────────────────────────────────────────────────

/** The widgets' tokens for the current UI style: Predator palette, or the Material scheme for Classic. */
private class Tok(val predator: Boolean, val pd: PdPalette, val dark: Boolean, private val cs: androidx.compose.material3.ColorScheme) {
    val page: Color = if (predator) pd.panel else cs.background
    val ink: Color = if (predator) pd.ink else cs.primary
    val text: Color = if (predator) pd.text else cs.onBackground
    val dim: Color = if (predator) pd.dim else cs.onSurfaceVariant
    val faint: Color = if (predator) pd.faint else cs.outline
    val line: Color = if (predator) pd.edge else cs.outlineVariant
    val accent: Color = if (predator) pd.accent else cs.primary
    val onAccent: Color = if (predator) pd.on else cs.onPrimary
    val card: Color = if (predator) pd.raised else cs.surface
    val row: Color = if (predator) pd.row else Color.Transparent
    val well: Color = if (predator) pd.well else cs.surfaceVariant
    val destructive: Color = if (predator) pd.destructive else cs.error
    val tint: Color = if (predator) pd.accentTint else cs.secondaryContainer
    val onTint: Color = if (predator) pd.ink else cs.primary

    fun s(text: String) = if (predator) text.uppercase() else text

    /** Short fixed labels: condensed bold italic caps (Predator), bold (Classic). */
    fun label(size: TextUnit, color: Color = ink): TextStyle =
        if (predator) pdLabelStyle(size = size, color = color) else TextStyle(fontSize = size, fontWeight = FontWeight.Bold, color = color)

    /** Data captions: monospace caps (Predator). */
    fun caption(size: TextUnit, color: Color = dim): TextStyle =
        if (predator) pdCaptionStyle(size = size, color = color) else TextStyle(fontSize = (size.value + 1f).sp, color = color)

    /** Hero captions ("HELLO,", "TODAY IS", "AND YOU HAVE:"): monospace caps (Predator), bold (Classic), as on the widget. */
    fun heroCaption(size: TextUnit, color: Color = ink): TextStyle =
        if (predator) pdCaptionStyle(size = size, color = color) else TextStyle(fontSize = (size.value + 1f).sp, fontWeight = FontWeight.Bold, color = color)

    /** User content: default face in both styles. */
    fun body(size: TextUnit, color: Color = text, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = size, fontWeight = weight, color = color)

    fun shape(cut: Dp = 6.dp): Shape = if (predator) pdChamferShape(cut) else RoundedCornerShape(if (cut > 8.dp) 20.dp else 12.dp)

    /** A user color at [amount] over the page, opaque (the widgets' pd.tint). */
    fun tinted(color: Color, amount: Float): Color = PredatorColors.mix(page, color, amount)

    fun priority(p: Int): Color? = LifeMappColors.priority(p, dark)
}

private val LocalTok = compositionLocalOf<Tok> { error("Tok") }

@Composable
private fun rememberTokens(): Tok {
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val pd = LocalPdPalette.current
    return Tok(predator, pd, pd.isDark, MaterialTheme.colorScheme)
}

/** The widgets' pd-frame: shape filled with [fill], 1dp [edge]. */
private fun Modifier.framed(t: Tok, fill: Color, edge: Color = fill, cut: Dp = 6.dp, shape: Shape = t.shape(cut)): Modifier {
    return clip(shape).background(fill, shape).border(1.dp, edge, shape)
}

/** The hero banner (styleguide §7): a block pointing right, like the web's hero. */
private val ArrowShape: Shape = GenericShape { size, _ ->
    val tip = minOf(size.height / 2f, 22f * 3f)
    moveTo(0f, 0f)
    lineTo(size.width - tip, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - tip, size.height)
    lineTo(0f, size.height)
    close()
}

/** L-shaped brackets in the four corners plus short ticks at the middle of the top and bottom edges. */
private fun Modifier.bracketCorners(color: Color, arm: Dp = 9.dp, stroke: Dp = 1.5.dp, tick: Dp = 5.dp): Modifier = drawBehind {
    val a = arm.toPx(); val s = stroke.toPx(); val h = s / 2f; val k = tick.toPx()
    val w = size.width; val ht = size.height
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = s)
    // corners
    line(0f, h, a, h); line(h, 0f, h, a)
    line(w, h, w - a, h); line(w - h, 0f, w - h, a)
    line(0f, ht - h, a, ht - h); line(h, ht, h, ht - a)
    line(w, ht - h, w - a, ht - h); line(w - h, ht, w - h, ht - a)
    // center ticks
    line(w / 2f, 0f, w / 2f, k)
    line(w / 2f, ht, w / 2f, ht - k)
}

@Composable
private fun Hex(size: Dp, edge: Color, fill: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(size, size * 1.1f).clip(PdHexagonShape).background(fill, PdHexagonShape).border(1.5.dp, edge, PdHexagonShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Numbered marker: hex with accent edge (Predator), tinted circle (Classic). */
@Composable
private fun NumberBadge(n: Int, size: Dp = 22.dp, faded: Boolean = false) {
    val t = LocalTok.current
    if (t.predator) {
        Hex(size, edge = if (faded) t.pd.edge2 else t.accent, fill = t.pd.panel) {
            Text("$n", style = t.label(9.sp, if (faded) t.faint else t.ink))
        }
    } else {
        Box(Modifier.size(size - 4.dp).clip(CircleShape).background(t.tint), contentAlignment = Alignment.Center) {
            Text("$n", style = t.body(10.sp, if (faded) t.dim else t.onTint, FontWeight.Bold))
        }
    }
}

/** The color bar leaning like the widgets' (task list / calendar color). */
@Composable
private fun Bar(color: Color, height: Dp = 26.dp) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdParallelogramShape(2.dp) else RoundedCornerShape(2.dp)
    Box(Modifier.size(if (t.predator) 8.dp else 4.dp, height).clip(shape).background(color, shape))
}

@Composable
private fun Ic(res: Int, size: Dp = 14.dp, tint: Color = LocalTok.current.ink, desc: String? = null) {
    Icon(painterResource(res), desc, Modifier.size(size), tint = tint)
}

// ── Top bar and hero ────────────────────────────────────────────────────────

@Composable
private fun HeaderButton(onClick: () -> Unit, desc: String, primary: Boolean = false, width: Dp = 36.dp, content: @Composable () -> Unit) {
    val t = LocalTok.current
    val mod = Modifier.size(width, 36.dp).let {
        if (t.predator) it.framed(t, fill = if (primary) t.accent else t.pd.panel, edge = if (primary) t.pd.accentHi else t.pd.edge)
        else it.clip(CircleShape).let { m -> if (primary) m.background(t.accent) else m }
    }
    Box(mod.clickable(onClickLabel = desc, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun TopBar(ui: WidgetUi, onOpenSettings: () -> Unit, onReload: () -> Unit, navOpen: Boolean, onToggleNav: () -> Unit) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderButton(onOpenSettings, stringResource(R.string.settings_open)) {
            Ic(LucideIcons.PANEL, 18.dp, t.ink, stringResource(R.string.settings_open))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("LIFE MAPP", style = t.label(16.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            StatusLine(ui)
        }
        // Navigation overlay (the web's DomainFloatingNav), highlighted while open
        HeaderButton(onToggleNav, stringResource(R.string.today_nav), primary = navOpen, width = 64.dp) {
            val tint = if (navOpen) t.onAccent else t.ink
            Ic(if (navOpen) LucideIcons.CLOSE else LucideIcons.NAVIGATION, 18.dp, tint, stringResource(R.string.today_nav))
        }
        Spacer(Modifier.width(8.dp))
        // Mic: add tasks and reminders by voice (the widgets' VoiceInputActivity)
        val context = LocalContext.current
        HeaderButton({ context.startActivity(VoiceInputActivity.intent(context)) }, stringResource(R.string.today_voice), width = 64.dp) {
            Ic(LucideIcons.MIC, 18.dp, t.ink, stringResource(R.string.today_voice))
        }
        Spacer(Modifier.width(8.dp))
        ReloadButton(ui, onReload, primary = false)
    }
}

@Composable
private fun ReloadButton(ui: WidgetUi, onReload: () -> Unit, primary: Boolean) {
    val t = LocalTok.current
    val tint = if (primary) t.onAccent else t.ink
    HeaderButton(onReload, stringResource(R.string.today_reload), primary, width = 64.dp) {
        if (ui.syncing) CircularProgressIndicator(Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
        else Ic(LucideIcons.RELOAD, 16.dp, tint, stringResource(R.string.today_reload))
    }
}

/** The last sync error, if any (no "Updated" time). */
@Composable
private fun StatusLine(ui: WidgetUi) {
    val t = LocalTok.current
    val text = when (ui.error) {
        SyncError.UNAUTHORIZED -> stringResource(R.string.sync_error_unauthorized)
        SyncError.NETWORK -> stringResource(R.string.error_network)
        SyncError.SERVER -> stringResource(R.string.sync_error_server)
        else -> return
    }
    Text(text, style = t.caption(10.sp, t.destructive), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** "HELLO," over the name, in a frame as wide as its text, centered. */
@Composable
private fun GreetingBlock(ui: WidgetUi) {
    val t = LocalTok.current
    val name = ui.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.today_welcome)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        Column(
            Modifier.framed(t, t.card, cut = 8.dp, shape = if (t.predator) ArrowShape else t.shape(8.dp))
                .padding(start = 22.dp, end = if (t.predator) 40.dp else 22.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(t.s(stringResource(R.string.today_hello)), style = t.heroCaption(10.sp))
            Text(
                name,
                style = (if (t.predator) t.label(28.sp) else t.body(26.sp, t.ink, FontWeight.Bold)).copy(textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "TODAY IS," over the date, the reload button on the right of the same frame. */
@Composable
private fun DateBlock(ui: WidgetUi, onReload: () -> Unit) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier.fillMaxWidth(0.9f).framed(t, t.card, cut = 8.dp).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t.s(stringResource(R.string.today_is)), style = t.heroCaption(10.sp),
                    modifier = Modifier.align(Alignment.Start),
                )
                Text(
                    ui.todayLabel,
                    style = if (t.predator) t.label(22.sp) else t.body(20.sp, t.ink, FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            ReloadButton(ui, onReload, primary = true)
        }
    }
}

@Composable
private fun Counters(ui: WidgetUi, onEvents: () -> Unit, onTasks: () -> Unit, onMail: () -> Unit) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().framed(t, t.card, cut = 8.dp).padding(vertical = 6.dp)) {
        Text(t.s(stringResource(R.string.today_and_you_have)), style = t.heroCaption(10.sp), modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Counter(Modifier.weight(1f), LucideIcons.CALENDAR, ui.eventsCount.toString(), stringResource(R.string.today_events), onEvents)
            if (t.predator) Box(Modifier.width(1.dp).height(28.dp).background(t.line))
            Counter(Modifier.weight(1f), LucideIcons.TASKS, ui.tasksCount.toString(), stringResource(R.string.today_tasks), onTasks)
            if (t.predator) Box(Modifier.width(1.dp).height(28.dp).background(t.line))
            Counter(Modifier.weight(1f), LucideIcons.MAIL, if (ui.mailConnected) ui.mailCount.toString() else "–", stringResource(R.string.today_mail), onMail)
        }
    }
}

@Composable
private fun Counter(modifier: Modifier, icon: Int, value: String, label: String, onClick: () -> Unit) {
    val t = LocalTok.current
    Column(modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, 16.dp, t.ink)
            Spacer(Modifier.width(5.dp))
            Text(value, style = if (t.predator) t.label(20.sp, t.text) else t.body(20.sp, t.text, FontWeight.Bold))
        }
        Text(t.s(label), style = if (t.predator) t.caption(9.sp) else t.body(11.sp, t.dim), maxLines = 1)
    }
}

/** "WEATHER IN ZURICH", today's icon, high / low and condition, and the average rating hex. */
@Composable
private fun WeatherCard(ui: WidgetUi, onClick: () -> Unit) {
    val t = LocalTok.current
    val w = ui.weather
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).framed(t, t.card, cut = 8.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (w != null) {
            Text(
                t.s(if (w.location.isNotBlank()) stringResource(R.string.today_weather_in, w.location) else stringResource(R.string.today_weather)),
                style = t.heroCaption(10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (w != null) {
                Ic(LucideIcons.weather(w.icon), 34.dp, t.ink, w.condition)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(w.temperature, style = if (t.predator) t.label(24.sp) else t.body(22.sp, t.text, FontWeight.Bold))
                    Text(w.low, style = t.caption(11.sp))
                }
                Spacer(Modifier.width(12.dp))
                Text(t.s(w.condition), style = t.caption(10.sp), maxLines = 2, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            ui.averageRating?.let { rating ->
                if (w != null) {
                    Box(Modifier.width(1.dp).height(38.dp).background(t.line))
                    Spacer(Modifier.width(12.dp))
                }
                if (t.predator) {
                    Hex(40.dp, edge = t.pd.accentHi, fill = t.accent) { Text("%.1f".format(rating), style = t.label(13.sp, t.onAccent)) }
                } else {
                    Text(
                        "★ ${"%.1f".format(rating)}",
                        style = t.body(13.sp, t.onAccent, FontWeight.Bold),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(t.accent).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** Journal and Budget: parallelograms in the accent tint (Predator), tonal pills (Classic). */
@Composable
private fun QuickActions(onOpenWeb: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(Modifier.weight(1f), LucideIcons.JOURNAL, stringResource(R.string.today_journal)) { onOpenWeb(WebRoutes.JOURNAL) }
        ActionButton(Modifier.weight(1f), LucideIcons.BUDGET, stringResource(R.string.today_budget)) { onOpenWeb(WebRoutes.BUDGET) }
    }
}

@Composable
private fun ActionButton(modifier: Modifier, icon: Int, label: String, onClick: () -> Unit) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdParallelogramShape(8.dp) else RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).background(t.tint, shape).let { if (t.predator) it.border(1.dp, t.accent, shape) else it }
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, 14.dp, t.onTint)
        Spacer(Modifier.width(6.dp))
        Text(t.s(label), style = if (t.predator) t.label(12.sp) else t.body(13.sp, t.onTint, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun LockedBanner(onUnlock: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).framed(t, t.tint, edge = if (t.predator) t.pd.edge2 else t.tint, cut = 8.dp)
            .clickable(onClick = onUnlock).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(LucideIcons.LOCK, 18.dp, t.onTint)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.today_locked_banner), style = t.body(12.sp, t.text), modifier = Modifier.weight(1f))
    }
}

// ── Sections ────────────────────────────────────────────────────────────────

/** Centered section title: icon and name in the ink color, no rule. */
@Composable
private fun SectionHeader(title: String, icon: Int, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 32.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Predator: hex icon well (accent edge, raised fill); Classic: plain icon
        if (t.predator) Hex(28.dp, edge = t.accent, fill = t.card) { Ic(icon, 14.dp, t.ink) } else Ic(icon, 15.dp, t.ink)
        Spacer(Modifier.width(8.dp))
        Text(t.s(title), style = if (t.predator) t.label(16.sp) else t.body(15.sp, t.ink, FontWeight.Bold), maxLines = 1)
    }
}

/** Centered day divider: a line, "TODAY" / "TOMORROW" / the date, a line. */
@Composable
private fun DayHeader(label: String, emphasized: Boolean = false) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(t.line))
        Text(
            t.s(label),
            style = (if (emphasized) t.ink else t.dim).let { c -> if (t.predator) t.label(11.sp, c) else t.body(12.sp, c, FontWeight.Bold) },
            modifier = Modifier.padding(horizontal = 10.dp), maxLines = 1,
        )
        Box(Modifier.weight(1f).height(1.dp).background(t.line))
    }
}

@Composable
private fun EmptyLine(text: String, onClick: () -> Unit) {
    val t = LocalTok.current
    Text(text, style = t.body(13.sp, t.dim), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp))
}

@Composable
private fun EventsSection(ui: WidgetUi, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit) {
    // The Dashboard widget's own filter: the next days (default 3), today always listed
    val days = EventsConfig.filter(ui.eventDays, ui.today, EventsConfig.DEFAULT_DAYS, emptySet())
    Column {
        SectionHeader(stringResource(R.string.today_events), LucideIcons.CALENDAR) { onOpenWeb(WebRoutes.EVENTS) }
        when {
            !ui.calendarConnected && ui.hasData -> EmptyLine(stringResource(R.string.today_not_connected)) { onOpenWeb(WebRoutes.SETTINGS) }
            days.none { it.events.isNotEmpty() } -> EmptyLine(stringResource(R.string.today_no_events)) { onOpenWeb(WebRoutes.EVENTS) }
            else -> {
                // Events are numbered across days (1, 2, 3 today, 4, 5 tomorrow), as on the web
                var offset = 0
                for (day in days) {
                    DayHeader(dayName(day))
                    if (day.events.isEmpty()) EmptyLine(stringResource(R.string.today_no_events)) { onOpenWeb(WebRoutes.EVENTS) }
                    day.events.forEachIndexed { i, e -> EventRow(e, offset + i + 1, day, openLink, onOpenWeb) }
                    offset += day.events.size
                }
            }
        }
    }
}

@Composable
private fun dayName(day: EventDayUi): String = when (day.kind) {
    DayKind.TODAY -> stringResource(R.string.today_day_today)
    DayKind.TOMORROW -> stringResource(R.string.today_day_tomorrow)
    DayKind.OTHER -> day.label
}

/** Numbered hex, calendar color bar, title with the calendar tag, then clock, time and date. Days after today are faded. */
@Composable
private fun EventRow(event: EventUi, number: Int, day: EventDayUi, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit) {
    val t = LocalTok.current
    val color = LifeMappColors.parseHex(event.color) ?: Color(0xFF3B82F6)
    val upcoming = day.kind != DayKind.TODAY
    val dimmed = upcoming || event.secondary
    val time = listOfNotNull(event.timeLabel ?: stringResource(R.string.today_all_day), day.label.ifEmpty { null }).joinToString(" · ")
    val calendar = event.calendarName?.takeIf { it.isNotBlank() }
    val titleColor = if (upcoming) t.faint else if (event.secondary) t.dim else t.text
    val captionColor = if (upcoming) t.faint else t.dim
    val barColor = if (dimmed) color.copy(alpha = 0.45f) else color
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberBadge(number, faded = upcoming)
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.weight(1f).framed(t, t.row).clickable { openLink(event.link ?: WebRoutes.EVENTS) }.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bar(barColor, 30.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        event.title, style = t.body(14.sp, titleColor, FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (calendar != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.s(calendar),
                            style = t.label(9.sp, if (dimmed) color.copy(alpha = 0.55f) else color),
                            maxLines = 1,
                            modifier = Modifier.background(t.tinted(color, if (dimmed) 0.06f else 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic(LucideIcons.CLOCK, 11.dp, captionColor)
                    Spacer(Modifier.width(4.dp))
                    Text(time, style = t.caption(10.sp, captionColor), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TasksSection(ui: WidgetUi, onComplete: (String) -> Unit, onOpenWeb: (String) -> Unit) {
    val t = LocalTok.current
    Column {
        SectionHeader(stringResource(R.string.today_tasks), LucideIcons.TASKS) { onOpenWeb(WebRoutes.TASKS) }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Modifier.weight(1f), LucideIcons.ADD, stringResource(R.string.today_add_task)) { onOpenWeb(WebRoutes.NEW_TASK) }
            ActionButton(Modifier.weight(1f), LucideIcons.TASKS, stringResource(R.string.today_task_lists)) { onOpenWeb(WebRoutes.TASKS) }
        }
        if (ui.completionFailed) {
            Text(stringResource(R.string.today_failed_completion), style = t.body(12.sp, t.destructive), modifier = Modifier.padding(vertical = 4.dp))
        }
        if (ui.tasks.isEmpty()) {
            EmptyLine(stringResource(R.string.today_no_tasks)) { onOpenWeb(WebRoutes.TASKS) }
        } else {
            // Segmented as on the web's Task List Widget: TODAY, TOMORROW, TOMORROW ++ 1, numbered across them.
            // Today always gets its divider; an empty one says so, as the web's "No tasks today" box does.
            if (ui.tasks.first().day != DayKind.TODAY) {
                DayHeader(taskDayName(DayKind.TODAY), emphasized = true)
                EmptyLine(stringResource(R.string.today_no_tasks)) { onOpenWeb(WebRoutes.TASKS) }
            }
            ui.tasks.forEachIndexed { i, task ->
                if (i == 0 || ui.tasks[i - 1].day != task.day) DayHeader(taskDayName(task.day), emphasized = true)
                TaskRow(task, i + 1, onComplete) { onOpenWeb(WebRoutes.TASKS) }
            }
        }
    }
}

@Composable
private fun taskDayName(day: DayKind): String = when (day) {
    DayKind.TODAY -> stringResource(R.string.today_day_today)
    DayKind.TOMORROW -> stringResource(R.string.today_day_tomorrow)
    DayKind.OTHER -> stringResource(R.string.today_day_later)
}

/** Numbered hex, task tier color bar, checkbox, title with provider tag, then due date. Days after today are faded. */
@Composable
private fun TaskRow(task: TaskUi, number: Int, onComplete: (String) -> Unit, onOpen: () -> Unit) {
    val t = LocalTok.current
    val upcoming = task.upcoming
    val muted = if (upcoming) t.faint else t.dim
    // The web's color tier (red, orange, blue, neutral) for the bar and checkbox; upcoming tasks dimmed
    val priority = if (upcoming) null else t.priority(task.colorTier)
    val barColor = priority ?: (if (t.predator) t.pd.edge2 else t.line)
    val dueColor = if (task.overdue && !upcoming) t.destructive else muted
    val titleColor = if (upcoming) t.faint else if (task.title == null) t.dim else t.text
    val tagLabel = task.tagLabel
    val tagColor = LifeMappColors.parseHex(task.tagColor) ?: Color(0xFF10B981)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberBadge(number, faded = upcoming)
        Spacer(Modifier.width(6.dp))
        Row(Modifier.weight(1f).framed(t, t.row).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Bar(barColor, 30.dp)
            // Completion checkbox (LIFE MAPP tasks only), or a gap of the same width
            if (task.completable) {
                Box(Modifier.size(40.dp).clickable { onComplete(task.id) }, contentAlignment = Alignment.Center) {
                    Ic(
                        if (t.predator) PdDrawables.CHECK_SQUARE else LucideIcons.CHECK_OFF, 20.dp,
                        priority ?: muted, stringResource(R.string.today_complete_task),
                    )
                }
            } else {
                Spacer(Modifier.width(40.dp))
            }
            Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title ?: stringResource(R.string.today_locked),
                        style = t.body(14.sp, titleColor, FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tagLabel != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.s(tagLabel),
                            style = t.label(9.sp, if (upcoming) tagColor.copy(alpha = 0.55f) else tagColor),
                            maxLines = 1,
                            modifier = Modifier.background(t.tinted(tagColor, if (upcoming) 0.06f else 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                if (task.dueLabel != null || task.recurring) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        task.dueLabel?.let {
                            Ic(LucideIcons.CALENDAR, 11.dp, dueColor)
                            Spacer(Modifier.width(4.dp))
                            Text(it, style = t.caption(10.sp, dueColor), maxLines = 1)
                            if (task.overdue && !upcoming) {
                                Spacer(Modifier.width(4.dp))
                                Ic(LucideIcons.WARNING, 11.dp, dueColor)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (task.recurring) Ic(LucideIcons.REPEAT, 11.dp, muted)
                    }
                }
            }
        }
    }
}

// ── Shortcuts ───────────────────────────────────────────────────────────────

@Composable
private fun ShortcutsSection(ui: WidgetUi, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit, onUnlock: () -> Unit) {
    val t = LocalTok.current
    Column {
        SectionHeader(stringResource(R.string.today_shortcuts), LucideIcons.ZAP) { onOpenWeb(WebRoutes.QUICK_LINKS) }
        SubHeader(LucideIcons.MAIL, stringResource(R.string.today_mail), count = null) { openLink(ui.mailUrl ?: WebRoutes.MAIL) }
        MailBody(ui, openLink, onOpenWeb)
        if (ui.driveLinks.isNotEmpty()) {
            SubHeader(LucideIcons.FOLDER, stringResource(R.string.today_drive), count = null, top = SUB_HEADER_GAP) { openLink(ui.driveLinks.first().url) }
            DriveBody(ui, openLink)
        }
        SubHeader(
            LucideIcons.LINK, stringResource(R.string.today_links),
            count = ui.shortcuts.size.takeUnless { ui.shortcutsLocked },
            onAdd = { onOpenWeb(WebRoutes.QUICK_LINKS) }.takeUnless { ui.shortcutsLocked },
            top = SUB_HEADER_GAP,
        ) { onOpenWeb(WebRoutes.QUICK_LINKS) }
        when {
            ui.shortcutsLocked -> EmptyLine(stringResource(R.string.today_locked_banner), onUnlock)
            ui.shortcuts.isEmpty() -> EmptyLine(stringResource(R.string.today_no_links)) { onOpenWeb(WebRoutes.QUICK_LINKS) }
            // Predator: numbered rows, two per line; Classic: icon tiles, four per line
            t.predator -> ui.shortcuts.chunked(2).forEachIndexed { r, pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEachIndexed { i, s -> ShortcutRow(s, r * 2 + i + 1, Modifier.weight(1f), openLink) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            else -> ui.shortcuts.chunked(4).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { s -> ShortcutTile(s, Modifier.weight(1f), openLink) }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A divider line above, small icon and label in the ink color, an optional count and "+". */
/** Space above the Drive and Links sub-headers, which follow content (Mail sits right under the section title). */
private val SUB_HEADER_GAP = 24.dp

@Composable
private fun SubHeader(icon: Int, title: String, count: Int?, onAdd: (() -> Unit)? = null, top: Dp = 12.dp, onClick: () -> Unit) {
    val t = LocalTok.current
    Column(Modifier.fillMaxWidth().padding(top = top, bottom = 2.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, 13.dp, t.ink)
            Spacer(Modifier.width(6.dp))
            Text(t.s(title), style = if (t.predator) t.label(13.sp) else t.body(13.sp, t.ink, FontWeight.Bold), maxLines = 1)
            if (count != null) Text("$count", style = t.caption(11.sp), modifier = Modifier.padding(start = 6.dp))
            if (onAdd != null) {
                Box(Modifier.size(28.dp).clickable(onClick = onAdd), contentAlignment = Alignment.Center) { Ic(LucideIcons.ADD, 14.dp, t.dim) }
            }
        }
    }
}

/** Drive: the user's service links as badges in one row (up to three, as the widget shows), each opening its link. */
@Composable
private fun DriveBody(ui: WidgetUi, openLink: (String) -> Unit) {
    val t = LocalTok.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        ui.driveLinks.take(3).forEach { link ->
            Box(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                Row(
                    Modifier.fillMaxWidth().framed(t, if (t.predator) t.pd.panel else t.card, edge = if (t.predator) t.pd.edge else t.card)
                        .clickable { openLink(link.url) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Ic(LucideIcons.FOLDER, 12.dp, t.accent)
                    Spacer(Modifier.width(5.dp))
                    // Labels are user content: no uppercase
                    Text(link.label, style = t.body(11.sp, t.dim, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Mail: the connect prompt, the web E-Mails widget's tags, or (older servers) the inbox row with its count. */
@Composable
private fun MailBody(ui: WidgetUi, openLink: (String) -> Unit, onOpenWeb: (String) -> Unit) {
    val t = LocalTok.current
    if (!ui.mailConnected) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).framed(t, if (t.predator) t.pd.well else t.well, edge = if (t.predator) t.pd.edge else t.well, cut = 8.dp)
                .clickable { onOpenWeb(WebRoutes.MAIL) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(LucideIcons.MAIL, 18.dp, t.dim)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_mail_not_connected), style = t.body(13.sp, t.text, FontWeight.Medium))
                Text(stringResource(R.string.today_connect_mail), style = t.caption(10.sp))
            }
        }
        return
    }
    if (ui.mailTags.isNotEmpty()) {
        // Two columns, filled row by row (1 2 / 3 4 ...); an odd last tag keeps half the width
        ui.mailTags.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEachIndexed { c, tag -> MailTagRow(tag, r * 2 + c + 1, openLink, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        return
    }
    // The inbox row alone still takes one of the two tag columns
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InboxRow(ui, openLink, Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

/** The inbox with its unread count (older servers without the tag list). */
@Composable
private fun InboxRow(ui: WidgetUi, openLink: (String) -> Unit, modifier: Modifier) {
    val t = LocalTok.current
    val unread = ui.mailCount > 0
    Row(
        modifier.padding(vertical = 4.dp).framed(t, if (t.predator) t.row else t.card, edge = if (t.predator) t.pd.edge else t.card)
            .clickable { openLink(ui.mailUrl ?: WebRoutes.MAIL) }.padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (t.predator) {
            Bar(t.accent, 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Ic(LucideIcons.INBOX, 16.dp, if (unread) t.accent else t.dim)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.today_inbox), style = t.body(14.sp, t.text, FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f))
        // Unread count: accent parallelogram when there is unread mail, framed panel otherwise
        val shape: Shape = if (t.predator) pdParallelogramShape(5.dp) else RoundedCornerShape(10.dp)
        Text(
            "${ui.mailCount}",
            style = t.label(12.sp, if (unread) t.onAccent else t.dim),
            modifier = Modifier.clip(shape).background(if (unread) t.accent else if (t.predator) t.pd.panel else t.well, shape)
                .let { if (!unread && t.predator) it.border(1.dp, t.pd.edge, shape) else it }
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

/**
 * A mail tag as the web E-Mails widget lists it: running number, color bar, icon, name ("Today" for the inbox) and
 * unread / total. Later horizons are dimmed; the inbox count is filled with the accent when there is unread mail.
 */
@Composable
private fun MailTagRow(tag: MailTagUi, number: Int, openLink: (String) -> Unit, modifier: Modifier) {
    val t = LocalTok.current
    val inbox = tag.name == null
    val unread = tag.unread > 0
    val tint = LifeMappColors.parseHex(tag.color) ?: t.accent
    Row(
        modifier.padding(vertical = 2.dp).alpha(tag.fade)
            .framed(t, if (t.predator) t.row else t.card, edge = if (t.predator) t.pd.edge else t.card)
            .clickable { openLink(tag.url ?: WebRoutes.MAIL) }.padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBadge(number, size = 18.dp)
        Spacer(Modifier.width(4.dp))
        Bar(tint, 18.dp)
        Spacer(Modifier.width(5.dp))
        Ic(LucideIcons.resolve(tag.icon) ?: LucideIcons.MAIL, 14.dp, if (inbox && unread) t.accent else t.dim)
        Spacer(Modifier.width(5.dp))
        Text(
            tag.name ?: stringResource(R.string.today_mail_today),
            style = t.body(13.sp, t.text, if (inbox) FontWeight.Bold else FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val shape: Shape = if (t.predator) pdParallelogramShape(5.dp) else RoundedCornerShape(10.dp)
        val filled = inbox && unread
        val ink = when {
            filled -> t.onAccent
            unread && !inbox -> tint
            unread -> t.accent
            else -> t.dim
        }
        Text(
            "${tag.unread} / ${tag.total}",
            style = t.label(11.sp, ink),
            maxLines = 1,
            modifier = Modifier.clip(shape).background(if (filled) t.accent else if (t.predator) t.pd.panel else t.well, shape)
                .let { if (filled) it else it.border(1.dp, if (unread) ink.copy(alpha = 0.4f) else t.line, shape) }
                .padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}

/** A quick link as a numbered row (Predator): number hex, divider, the link icon in its color and the label. */
@Composable
private fun ShortcutRow(s: ShortcutUi, number: Int, modifier: Modifier, openLink: (String) -> Unit) {
    val t = LocalTok.current
    val tint = LifeMappColors.parseHex(s.color)
    Box(modifier.padding(2.dp)) {
        Row(
            Modifier.fillMaxWidth().framed(t, t.row).clickable { openLink(s.url) }.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBadge(number, size = 20.dp)
            Spacer(Modifier.width(5.dp))
            Box(Modifier.width(1.dp).height(16.dp).background(t.line))
            Spacer(Modifier.width(6.dp))
            LinkGlyph(s, tint, 14.dp, linkFallback = true)
            Spacer(Modifier.width(6.dp))
            // Labels are user content: no uppercase
            Text(s.label, style = t.body(13.sp, t.text, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShortcutTile(s: ShortcutUi, modifier: Modifier, openLink: (String) -> Unit) {
    val t = LocalTok.current
    val tint = LifeMappColors.parseHex(s.color)
    Column(modifier.clickable { openLink(s.url) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint?.copy(alpha = 0.16f) ?: t.tint),
            contentAlignment = Alignment.Center,
        ) { LinkGlyph(s, tint, 22.dp) }
        Spacer(Modifier.height(3.dp))
        Text(s.label, style = t.body(11.sp, t.text).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The link's Lucide icon, its emoji, or (unbundled icon) the link glyph when [linkFallback], else the first letter
 * of the label. The link color is raised to icon contrast over the page, as the widget does.
 */
@Composable
private fun LinkGlyph(s: ShortcutUi, tint: Color?, size: Dp, linkFallback: Boolean = false) {
    val t = LocalTok.current
    val drawable = LucideIcons.resolve(s.icon)
    val bg = if (t.predator) t.row else t.page
    val iconTint = tint?.let { ColorContrast.ensure(it, bg, ColorContrast.GRAPHIC) } ?: t.ink
    when {
        drawable != null -> Ic(drawable, size, iconTint)
        LucideIcons.isEmoji(s.icon) -> Text(s.icon, style = t.body((size.value - 2).sp))
        linkFallback -> Ic(LucideIcons.LINK, size, iconTint)
        else -> Text((s.label.firstOrNull() ?: '?').uppercase(), style = t.label((size.value - 2).sp, tint?.let { ColorContrast.ensure(it, bg) } ?: t.ink))
    }
}

// ── Integrity ───────────────────────────────────────────────────────────────

/** The semantic red → green ramp of the web's score gauge; dark mode brightens the greens. */
private val RampLight = longArrayOf(0xFFE5484D, 0xFFEF5B3A, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF93BC2A, 0xFF3DB45F, 0xFF1E9E52)
private val RampDark = longArrayOf(0xFFE5484D, 0xFFEF5B3A, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFFA3C93A, 0xFF4CC26B, 0xFF22B35E)
private val Warn = Color(0xFFF59E0B)

private fun ramp(i: Int, dark: Boolean) = Color((if (dark) RampDark else RampLight)[minOf(i, 7)])

/** LazyColumn key of one of the other Today domains (positions 2–5) */
private fun domainKey(domain: IntegrityUi) = "domain-${domain.domainId}"

/** The domain's own name; its default by position when it has none or is encrypted and locked. */
@Composable
private fun domainName(domain: IntegrityUi): String = domain.name?.takeIf { it.isNotBlank() } ?: when (domain.position) {
    1 -> stringResource(R.string.today_integrity)
    2 -> "Serenity"
    3 -> "Vitality"
    4 -> "Stability"
    5 -> "Tenacity"
    else -> stringResource(R.string.today_locked)
}

/**
 * The web's domain card: Integrity (with the Reception above Reminders and the Warnings bar) or one of the other
 * Today domains, Serenity, Vitality, Stability and Tenacity, which only this screen shows (never the widgets).
 */
@Composable
private fun DomainCard(
    integrity: IntegrityUi, onOpenWeb: (String) -> Unit,
    onRenameItem: (String, String) -> Unit, onDeleteItem: (String) -> Unit,
    onToggleTopic: (String, Boolean) -> Unit, onToggleItem: (String, Boolean) -> Unit,
) {
    val t = LocalTok.current
    val isIntegrity = integrity.position == 1
    val route = if (isIntegrity || integrity.domainId.isBlank()) WebRoutes.INTEGRITY else WebRoutes.domain(integrity.domainId)
    var editing by remember { mutableStateOf<TopicItemUi?>(null) }
    editing?.let { item ->
        EditItemDialog(
            item,
            onSave = { onRenameItem(item.id, it); editing = null },
            onDelete = { onDeleteItem(item.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    val name = domainName(integrity)
    // The web's domain card: one bordered, rounded (Predator: chamfered) surface around the whole domain
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp)
            .framed(t, t.card, edge = t.line, cut = if (t.predator) 8.dp else 24.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().clickable { onOpenWeb(route) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(integrity.position.toString().padStart(2, '0'), style = t.label(22.sp, t.faint))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (integrity.name != null) t.s(name) else name,
                    style = (if (t.predator) t.label(17.sp) else t.body(16.sp, t.ink, FontWeight.Bold)).copy(textAlign = TextAlign.Center),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                integrity.rating?.let { RatingBadge(it) }
            }
            Spacer(Modifier.height(8.dp))
            Gauge(integrity.rating)
            integrity.infoBox?.let { info ->
                Spacer(Modifier.height(8.dp))
                Text(
                    info,
                    style = (if (t.predator) t.caption(12.sp, t.text) else t.body(12.sp, t.onTint)).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().let {
                        // Predator: square tinted field, thin edge, bracket corners and center ticks (styleguide info box)
                        if (t.predator) it.background(t.pd.accentTint).border(1.dp, t.pd.edge).bracketCorners(t.pd.edge2)
                        else it.framed(t, t.tint)
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        integrity.topics.forEach { topic ->
            // The Reception sits right above the Reminders topic, as on the web
            if (topic.reminders) Reception()
            TopicHeader(topic) { onToggleTopic(topic.id, !topic.collapsed) }
            // A collapsed topic shows its header only, as on the web card
            if (topic.collapsed && topic.items.isNotEmpty()) return@forEach
            if (topic.items.isEmpty()) NoContent(topic) { onOpenWeb(route) }
            else topic.items.forEachIndexed { i, item ->
                ItemRow(
                    topic, item, i + 1,
                    onToggle = { onToggleItem(item.id, !item.collapsed) },
                    onEdit = { if (item.name != null) editing = item },
                )
            }
            if (topic.more > 0) {
                Text(
                    stringResource(R.string.today_items_more, topic.more), style = t.caption(10.sp),
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
                )
            }
        }
        // The Warnings bar counts flagged items of every domain; the web shows it on the Integrity card only
        if (isIntegrity) WarningsBar(integrity.warnings) { onOpenWeb(WebRoutes.WARNINGS) }
    }
}

@Composable
private fun RatingBadge(rating: Int) {
    val t = LocalTok.current
    val fill = if (rating > 0) ramp(rating - 1, t.dark) else t.well
    val on = if (rating > 0) ColorContrast.onFill(fill) else t.dim
    if (t.predator) {
        Hex(26.dp, edge = t.pd.edge2, fill = fill) { Text("$rating", style = t.label(12.sp, on)) }
    } else {
        Box(Modifier.size(26.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Text("$rating", style = t.body(12.sp, on, FontWeight.Bold))
        }
    }
}

/** Ten skewed segments, filled up to the rating; the rating's own segment stands taller, like the web's knob. */
@Composable
private fun Gauge(rating: Int?) {
    val t = LocalTok.current
    val filled = (rating ?: 0).coerceIn(0, 10)
    val shape: Shape = if (t.predator) pdParallelogramShape(2.dp) else RoundedCornerShape(3.dp)
    Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 10) {
            Box(
                Modifier.weight(1f).height(if (i == filled - 1) 14.dp else 8.dp).clip(shape)
                    .background(if (i < filled) ramp(i, t.dark) else t.well, shape),
            )
        }
    }
}

/** The landing zone above Reminders: mic to speak reminders, pencil to type one (VoiceInputActivity). */
@Composable
private fun Reception() {
    val t = LocalTok.current
    val context = LocalContext.current
    val hint = stringResource(R.string.today_create_reminder)
    val record = stringResource(R.string.today_record)
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .framed(t, if (t.predator) t.pd.well else t.well, edge = if (t.predator) t.pd.edge2 else t.well, cut = 8.dp)
            .padding(horizontal = 12.dp, vertical = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceptionButton(LucideIcons.MIC, record) { context.startActivity(VoiceInputActivity.intent(context, reminders = true)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t.s(stringResource(R.string.today_reception)), style = if (t.predator) t.label(14.sp, t.text) else t.body(13.sp, t.dim, FontWeight.Bold))
            Text(hint, style = t.caption(10.sp), maxLines = 1)
        }
        ReceptionButton(LucideIcons.PENCIL, hint) { context.startActivity(VoiceInputActivity.intent(context, reminders = true, typed = true)) }
    }
}

@Composable
private fun ReceptionButton(icon: Int, desc: String, onClick: () -> Unit) {
    val t = LocalTok.current
    Box(Modifier.clickable(onClickLabel = desc, onClick = onClick)) {
        if (t.predator) Hex(36.dp, edge = t.accent, fill = t.pd.panel) { Ic(icon, 16.dp, t.ink, desc) }
        else Box(Modifier.size(38.dp).clip(CircleShape).background(t.tint), contentAlignment = Alignment.Center) { Ic(icon, 18.dp, t.onTint, desc) }
    }
}

/**
 * Chevron, numbered hex, name and item count. One tap anywhere on it collapses or expands the topic, as the web
 * card's chevron does; Reminders (always expanded) and a topic without items have nothing to fold.
 */
@Composable
private fun TopicHeader(topic: TopicUi, onToggle: () -> Unit) {
    val t = LocalTok.current
    val name = topic.name ?: stringResource(R.string.today_locked)
    val toggles = !topic.reminders && topic.items.isNotEmpty()
    val label = stringResource(if (topic.collapsed) R.string.today_expand else R.string.today_collapse)
    Row(
        Modifier.fillMaxWidth()
            .let { if (toggles) it.clickable(onClickLabel = label, onClick = onToggle) else it }
            .padding(top = 20.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (topic.reminders) Text("•", style = t.body(14.sp, t.dim, FontWeight.Bold), modifier = Modifier.width(14.dp), textAlign = TextAlign.Center)
        else Ic(if (topic.collapsed) LucideIcons.CHEVRON_RIGHT else LucideIcons.CHEVRON_DOWN, 12.dp, t.dim, null).also { Spacer(Modifier.width(2.dp)) }
        Spacer(Modifier.width(4.dp))
        NumberBadge(topic.number, size = 22.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            if (topic.name != null) t.s(name) else name,
            style = if (t.predator) t.label(14.sp, if (topic.name != null) t.ink else t.dim) else t.body(14.sp, if (topic.name != null) t.ink else t.dim, FontWeight.Bold),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (topic.count > 0) Text("(${topic.count})", style = t.caption(11.sp), modifier = Modifier.padding(start = 6.dp))
    }
}

/** An empty topic: a recessed row with an accent chip, "No content yet" and what a tap does. */
@Composable
private fun NoContent(topic: TopicUi, onClick: () -> Unit) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, top = 3.dp, bottom = 3.dp)
            .framed(t, if (t.predator) t.pd.well else t.well, edge = if (t.predator) t.pd.edge else t.well)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (topic.reminders) LucideIcons.MIC else LucideIcons.ADD
        if (t.predator) Hex(28.dp, edge = t.accent, fill = t.pd.accentTint) { Ic(icon, 13.dp, t.accent) }
        else Box(Modifier.size(28.dp).clip(CircleShape).background(t.tint), contentAlignment = Alignment.Center) { Ic(icon, 14.dp, t.onTint) }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.today_no_content), style = t.body(13.sp, t.text, FontWeight.Medium))
    }
}

/** Rename or remove an item in the app; no detour through the web. */
@Composable
private fun EditItemDialog(item: TopicItemUi, onSave: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var text by remember(item.id) { mutableStateOf(item.name.orEmpty()) }
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_edit_title)) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = false, maxLines = 4, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank() && text.trim() != item.name) {
                Text(stringResource(R.string.item_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                    Text(
                        stringResource(R.string.item_delete) + if (confirmDelete) "?" else "",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.item_cancel)) }
            }
        },
    )
}

/** "⌄ 2.1 Item" indented under the topic's hex, as on the web card, with the warning icon when flagged. */
/**
 * An item (subsubdomain): its header and, unless collapsed, its text. One tap on the header collapses or expands
 * the text, as the web card's title does; a long press opens the edit dialog. An item without text has nothing to
 * fold and shows a dot instead of the chevron.
 */
@Composable
private fun ItemRow(topic: TopicUi, item: TopicItemUi, number: Int, onToggle: () -> Unit, onEdit: () -> Unit) {
    val t = LocalTok.current
    val name = item.name?.takeIf { it.isNotBlank() } ?: if (item.name == null) stringResource(R.string.today_locked) else ""
    val content = item.content
    val toggleLabel = stringResource(if (item.collapsed) R.string.today_expand else R.string.today_collapse)
    val editLabel = stringResource(R.string.item_edit_title)
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(
                onClickLabel = if (content != null) toggleLabel else null,
                onClick = { if (content != null) onToggle() },
                onLongClickLabel = editLabel,
                onLongClick = onEdit,
            )
            .padding(start = 18.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content != null) Ic(if (item.collapsed) LucideIcons.CHEVRON_RIGHT else LucideIcons.CHEVRON_DOWN, 11.dp, t.dim, null)
        else Box(Modifier.size(11.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(5.dp).clip(CircleShape).background(t.faint)) }
        Spacer(Modifier.width(6.dp))
        Text("${topic.number}.$number", style = t.caption(11.sp), maxLines = 1)
        Spacer(Modifier.width(5.dp))
        if (item.warning) {
            Ic(LucideIcons.WARNING, 13.dp, if (t.dark) Color(0xFFFBBF4D) else Color(0xFFA04D05))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            name, style = t.body(14.sp, if (item.name == null) t.dim else t.text, FontWeight.Bold),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
    }
    if (content != null && !item.collapsed) {
        val html = remember(content, t.accent) {
            AnnotatedString.fromHtml(content, TextLinkStyles(SpanStyle(color = t.accent, textDecoration = TextDecoration.Underline)))
        }
        Text(html, style = t.body(13.sp, t.text), modifier = Modifier.fillMaxWidth().padding(start = 35.dp, end = 4.dp, bottom = 6.dp))
    }
}

/** The amber "WARNINGS" bar with its count. */
@Composable
private fun WarningsBar(count: Int, onClick: () -> Unit) {
    val t = LocalTok.current
    val ink = if (t.dark) Color(0xFFFBBF4D) else Color(0xFFA04D05)
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)
            .framed(t, if (t.predator) t.tinted(Warn, 0.14f) else if (t.dark) Color(0xFF3A2A08) else Color(0xFFFEF3C7), edge = if (t.predator) Warn else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Ic(LucideIcons.WARNING, 14.dp, ink)
        Spacer(Modifier.width(6.dp))
        Text(t.s(stringResource(R.string.today_warnings)), style = if (t.predator) t.label(14.sp, ink) else t.body(14.sp, ink, FontWeight.Bold))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Warn), contentAlignment = Alignment.Center) {
            Text("$count", style = t.label(12.sp, Color(0xFF1A1004)))
        }
    }
}
