package com.kalai73.lifemapp.widgets.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kalai73.lifemapp.widgets.core.designsystem.MonoFontFamily
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.presentation.ScoreDomainUi
import com.kalai73.lifemapp.widgets.core.presentation.ScoringUi
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.PdHexagonShape
import com.kalai73.lifemapp.widgets.core.designsystem.PredatorColors
import com.kalai73.lifemapp.widgets.core.designsystem.pdCaptionStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The web Today page's scoring section (scoring-circles.tsx, phone layout): a header with the "hide all" toggle,
 * the average badge (life and business averages and scoring clocks) and the history button, then the domains of
 * positions 1–5 as score hexagons in one zigzag row (Predator) or liquid circles in a 2 + 3 pyramid (Classic).
 *
 * Tapping a domain hides or shows it on Today (Integrity and Tenacity stay); a long press rates it. The average
 * badge steps through all five domains.
 */
@Composable
internal fun ScoringSection(
    scoring: ScoringUi,
    average: Double?,
    onToggleHidden: (String) -> Unit,
    onSetAllHidden: (Boolean) -> Unit,
    onRate: (Map<String, Int>) -> Unit,
    onHistory: () -> Unit,
    onUnlock: () -> Unit,
) {
    val t = LocalTok.current
    // The domains of the open rating dialog: one (long press) or all of them (average / capsule)
    var rating by remember { mutableStateOf<List<ScoreDomainUi>?>(null) }
    val openRating = { list: List<ScoreDomainUi> -> if (scoring.locked) onUnlock() else rating = list }
    rating?.let { list ->
        RatingDialog(list, onDismiss = { rating = null }, onSave = { onRate(it); rating = null })
    }

    // A due scoring rings the card in amber (Classic); Predator keeps its frame
    val edge = if (scoring.scoringDue && !t.predator) Warn.copy(alpha = 0.45f) else t.line
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp)
            .framed(t, t.card, edge = edge, cut = if (t.predator) 8.dp else 24.dp)
            .hexCorner(t.accent, if (t.dark) 0.5f else 1f),
    ) {
        ScoringHeader(
            average = average ?: 0.0,
            businessAverage = scoring.businessAverage,
            locked = scoring.locked,
            daysSinceScored = scoring.daysSinceScored,
            showHideAll = scoring.ready && scoring.domains.any { !it.unhidable },
            allHidden = scoring.allHidableHidden,
            onToggleAll = { onSetAllHidden(!scoring.allHidableHidden) },
            onScore = { openRating(scoring.domains) },
            onHistory = onHistory,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = if (t.predator) 16.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Predator: all five in one zigzag row, the odd ones dropped so their names (above the hexagon) sit
            // below the neighbours' names. Classic: two on the first row (72% wide), three on the second.
            val rows = if (t.predator) listOf(scoring.domains.take(5))
            else listOf(scoring.domains.take(2), scoring.domains.drop(2).take(3))
            rows.filter { it.isNotEmpty() }.forEachIndexed { i, row ->
                Row(
                    Modifier.fillMaxWidth(if (i == 0 && !t.predator) 0.72f else 1f),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Top,
                ) {
                    row.forEachIndexed { j, d ->
                        // Outer names pin to the row edges so they stay inside the card
                        val labelAlign = when (j) {
                            0 -> Alignment.Start
                            row.lastIndex -> Alignment.End
                            else -> Alignment.CenterHorizontally
                        }
                        DomainScore(
                            d,
                            Modifier.weight(1f).then(if (t.predator && j % 2 == 1) Modifier.padding(top = 28.dp) else Modifier),
                            labelAlign = labelAlign,
                            onTap = { if (!d.unhidable && scoring.ready) onToggleHidden(d.id) },
                            onLongPress = { openRating(listOf(d)) },
                        )
                    }
                }
            }
        }
    }
}

// ── Domain hexagon / circle ─────────────────────────────────────────────────

/** The score shown: an unrated (or locked) domain reads 5, as the web's `effectiveRating || 5` */
private fun shownScore(d: ScoreDomainUi) = d.rating?.takeIf { it > 0 } ?: 5

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DomainScore(
    d: ScoreDomainUi,
    modifier: Modifier,
    labelAlign: Alignment.Horizontal,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val t = LocalTok.current
    val haptics = LocalHapticFeedback.current
    val name = scoreDomainName(d)
    val score = shownScore(d)
    val desc = stringResource(R.string.scoring_domain_desc, name, score)
    val tapLabel = if (d.unhidable) null else stringResource(if (d.hidden) R.string.scoring_show_domain else R.string.scoring_hide_domain)
    Column(
        modifier
            .combinedClickable(
                onClickLabel = tapLabel,
                onLongClickLabel = stringResource(R.string.scoring_rate),
                onClick = onTap,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress() },
            )
            .semantics { contentDescription = desc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (t.predator) PredatorHex(d, score, name, labelAlign) else ClassicCircle(d, score, name)
    }
}

/**
 * PdHexScore under its name tag (a panel with corner brackets in the domain's hue, allowed to overflow into the
 * neighbouring columns): the hexagon fills from the bottom in the domain's hue.
 */
@Composable
internal fun PredatorHex(
    d: ScoreDomainUi,
    score: Int,
    name: String,
    labelAlign: Alignment.Horizontal,
    labelBelow: Boolean = false,
) {
    val t = LocalTok.current
    val (tagHue, _) = domainHue(d.colorName)
    val label = @Composable {
        Text(
            t.s(name),
            style = t.label(10.sp, if (d.hidden) t.dim else t.text).copy(letterSpacing = 0.08.em),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().wrapContentWidth(labelAlign, unbounded = true)
                .padding(top = if (labelBelow) 4.dp else 0.dp, bottom = if (labelBelow) 0.dp else 4.dp)
                .alpha(if (d.name == null) 0.6f else 1f)
                .framed(t, t.pd.panel, edge = t.line, cut = 0.dp)
                .bracketCorners(if (d.hidden) t.line else tagHue, arm = 5.dp, stroke = 1.dp, tick = 0.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
    if (!labelBelow) label()
    val (hue, onHue) = domainHue(d.colorName)
    // Hidden: half opacity and 35% grayscale, as the web
    val fill = if (d.hidden) hue.desaturate(0.35f) else hue
    val level = hexFillPercent(score) / 100f
    val full = level >= 1f
    val hi = PredatorColors.mix(fill, Color.White, 0.24f)
    val lo = PredatorColors.mix(fill, Color.Black, 0.22f)
    val rim = Brush.linearGradient(listOf(hi, fill, lo))
    val ink = if (full) onHue else t.text
    // Halo keeps the number readable where it crosses the fill line
    val halo = if (full) null else Shadow(t.pd.well, blurRadius = 8f)
    val well = t.pd.well
    Box(
        Modifier.size(HEX_W, HEX_W * 1.1f).alpha(if (d.hidden) 0.5f else 1f).clip(PdHexagonShape)
            .drawBehind {
                // pd-hex-score.tsx in its 100 × 110 viewBox: the well, the reticle watermark, then the fill rising
                // from the bottom with the reticle etched through it and a bright line along its top
                val u = size.width / 100f
                val top = size.height - size.height * level
                drawRect(well)
                drawReticle(fill.copy(alpha = 0.24f), u)
                if (full) {
                    drawRect(Brush.linearGradient(listOf(hi, fill, lo), start = Offset.Zero, end = Offset(size.width, size.height)))
                } else {
                    drawRect(fill, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
                }
                clipRect(top = top) { drawReticle(onHue.copy(alpha = 0.18f), u) }
                if (!full) drawLine(hi.copy(alpha = 0.75f), Offset(0f, top), Offset(size.width, top), 1.75f * u)
            }
            .border(1.75.dp, rim, PdHexagonShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$score",
            style = t.label(22.sp, ink).copy(letterSpacing = 0.sp, shadow = halo),
            modifier = Modifier.offset(y = (-5).dp),
        )
        Text(
            d.position.toString().padStart(2, '0'),
            style = pdCaptionStyle(9.sp, ink, letterSpacing = 0.sp).copy(shadow = halo),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
        )
    }
    if (labelBelow) label()
}

/** PdHexScore's TargetReticle, [u] being one unit of the 100-wide viewBox: a ring at (50, 55) with four arms. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReticle(color: Color, u: Float) {
    drawCircle(color, 23f * u, Offset(50f * u, 55f * u), style = Stroke(5.5f * u))
    val w = 4.5f * u
    drawLine(color, Offset(50f * u, 32f * u), Offset(50f * u, 19f * u), w)
    drawLine(color, Offset(73f * u, 55f * u), Offset(84.5f * u, 55f * u), w)
    drawLine(color, Offset(50f * u, 78f * u), Offset(50f * u, 91f * u), w)
    drawLine(color, Offset(27f * u, 55f * u), Offset(15.5f * u, 55f * u), w)
}

/** hexFillPercent (pd-hex-score.tsx): score/10 with a 15% minimum, full only at 10 */
private fun hexFillPercent(score: Int): Int = when {
    score <= 0 -> 0
    score >= 10 -> 100
    else -> maxOf(15, score * 10)
}

/** Classic: the glass circle whose liquid rises with the score, sized by it, the name chip across its foot. */
@Composable
internal fun ClassicCircle(d: ScoreDomainUi, score: Int, name: String) {
    val t = LocalTok.current
    val scale = 0.6f + (score.coerceIn(1, 10) - 1) * (0.4f / 9f)
    val dim = (64 * scale).dp
    val numeral = if (t.dark) Color.White else Color(0xFF1A1A1A)
    Box(Modifier.padding(bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.size(dim).alpha(if (d.hidden) 0.6f else 1f).clip(CircleShape)
                .drawBehind {
                    drawRect(Brush.verticalGradient(SoftScore.reversed().map { it.copy(alpha = 0.08f) }))
                    val h = size.height * score.coerceIn(0, 10) / 10f
                    drawRect(liquid(score, size.height - h, size.height), topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                    // Specular highlight, top left
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width * 0.35f, size.height * 0.25f), radius = size.width * 0.45f),
                    )
                }
                .border(1.dp, t.accent, CircleShape),
        ) {
            Text(
                d.position.toString().padStart(2, '0'),
                style = t.body((dim.value * 0.28f).sp, t.text, FontWeight.Black),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).alpha(if (t.dark) 0.28f else 0.2f),
            )
            Text(
                "$score",
                style = t.body((28 * scale).sp, numeral, FontWeight.Black).copy(
                    fontFamily = MonoFontFamily,
                    shadow = Shadow(if (numeral == Color.White) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f), Offset(0f, 1f), 2f),
                ),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = (dim.value * 0.1f).dp),
            )
            if (score < 7) {
                // Low-score warning badge, bottom left
                val badge = ((7 - score) * 2 + 12).dp
                Box(
                    Modifier.align(Alignment.BottomStart).size(badge).clip(CircleShape).background(Color.Black.copy(alpha = if (t.dark) 0.6f else 0.4f)),
                    contentAlignment = Alignment.Center,
                ) { Ic(LucideIcons.WARNING, badge - 6.dp, if (t.dark) Color(0xFFFDE047) else Color(0xFFFACC15)) }
            }
        }
        Text(
            name.uppercase(),
            style = t.body(10.sp, t.accent, FontWeight.Bold).copy(letterSpacing = 0.05.em),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(y = 10.dp).alpha(if (d.hidden) 0.7f else 1f)
                .clip(RoundedCornerShape(4.dp)).background(t.card).border(1.dp, t.line, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}

/** getScoreProgressiveFill: soft red at the bottom rising to the score's own color, which holds from 88% up. */
private fun liquid(score: Int, top: Float, bottom: Float): Brush {
    val idx = score.coerceIn(1, 10) - 1
    if (idx == 0) return Brush.verticalGradient(listOf(SoftScore[0], SoftScore[0]), top, bottom)
    val stops = (0..idx).map { i -> (1f - (i.toFloat() / idx) * 0.88f) to SoftScore[i] }.reversed() + (0f to SoftScore[idx])
    return Brush.verticalGradient(*stops.sortedBy { it.first }.toTypedArray(), startY = top, endY = bottom)
}

/** lib/domains/colors.ts SOFT_SCORE_GRADIENT_COLORS, 1 → 10 */
private val SoftScore = listOf(
    0xFFE76F6F, 0xFFED846A, 0xFFF29B66, 0xFFF0AD61, 0xFFE8C263, 0xFFC8CF6E, 0xFFA3CB7C, 0xFF7BC98E, 0xFF4EC08B, 0xFF3BB37E,
).map(::Color)

/** lib/domains/colors.ts getScoreColor (SCORE_COLORS) */
private fun scoreColor(score: Int): Color = Color(
    when {
        score <= 3 -> 0xFFFF4757
        score <= 5 -> 0xFFFFA502
        score <= 7 -> 0xFFFFDD59
        score <= 8 -> 0xFF2ED573
        else -> 0xFF20BF6B
    },
)

// ── Header: hide all, average, history ──────────────────────────────────────

private val HEX_W = 54.dp

/**
 * The scoring header (scoring-circles.tsx, small screens): the "hide all" toggle on the left, the average badge
 * centred and the history button on the right; the side slots share the rest equally so the badge stays centred
 * when the toggle is absent. Classic closes it with a faint rule.
 */
@Composable
internal fun ScoringHeader(
    average: Double,
    businessAverage: Double?,
    locked: Boolean,
    daysSinceScored: Int?,
    showHideAll: Boolean,
    allHidden: Boolean,
    onToggleAll: () -> Unit,
    onScore: () -> Unit,
    onHistory: () -> Unit,
) {
    val t = LocalTok.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (t.predator) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (showHideAll) HideAllToggle(allHidden, onToggleAll)
        }
        AverageBadge(average, businessAverage, locked, daysSinceScored, onScore)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { HistoryButton(onHistory) }
    }
    if (!t.predator) Box(Modifier.fillMaxWidth().height(1.dp).background(t.line.copy(alpha = 0.6f)))
}

/**
 * How long ago a section was scored (scoring-circles.tsx buildFreshness): fresh today, warn from day 15 or when
 * never scored, due from day 22 or when never scored.
 */
private class Freshness(val days: Int?) {
    private val urgency = when {
        days == null -> 3
        days == 0 -> 0
        days <= 14 -> 1
        days <= 21 -> 2
        else -> 3
    }
    val fresh get() = days == 0
    val warn get() = !fresh && urgency >= 2
    val due get() = days == null || days >= 22
}

/** "Not scored", "Today", "3d ago" */
@Composable
private fun Freshness.short(): String = when {
    days == null -> stringResource(R.string.scoring_not_scored)
    days == 0 -> stringResource(R.string.scoring_today)
    else -> stringResource(R.string.scoring_days_ago_short, days)
}

/** "Not scored", "Scored Today", "Scored 3 days ago" */
@Composable
private fun Freshness.full(): String = when {
    days == null -> stringResource(R.string.scoring_not_scored)
    days == 0 -> stringResource(R.string.scoring_scored) + " " + stringResource(R.string.scoring_today)
    else -> stringResource(R.string.scoring_scored) + " " + pluralStringResource(R.plurals.scoring_days_ago, days, days)
}

private val emeraldInk @Composable get() = if (LocalTok.current.dark) Color(0xFF34D399) else Color(0xFF059669)

/** The warning ink: Predator's --pd-warn-ink, Classic's amber-600 / amber-400 */
private val amberInk @Composable get() = LocalTok.current.let { t ->
    if (t.predator) (if (t.dark) Color(0xFFFBBF4D) else Color(0xFFA04D05)) else if (t.dark) Color(0xFFFBBF24) else Color(0xFFD97706)
}

/**
 * The average badge: the life average (always one decimal, "7.0") and, under it, the business average when there
 * is one, with how long ago each was scored: life above, business below. The web keeps the business scoring time
 * in the browser only, so the app reads it as never scored. Tap: score every domain.
 */
@Composable
internal fun AverageBadge(average: Double, businessAverage: Double?, locked: Boolean, daysSinceScored: Int?, onClick: () -> Unit) {
    val t = LocalTok.current
    val life = Freshness(daysSinceScored)
    val showBusiness = !locked && businessAverage != null
    val business = if (showBusiness) Freshness(null) else null
    val avgText = "%.1f".format(java.util.Locale.ROOT, average)
    val businessText = businessAverage?.let { "%.1f".format(java.util.Locale.ROOT, it) }.orEmpty()
    val scoreAll = stringResource(R.string.scoring_score_all)
    val freshText = life.full() + (business?.let { " · " + it.full() } ?: "")
    val desc = if (locked) "$scoreAll. $freshText" else stringResource(R.string.scoring_average, avgText) + ". $freshText"
    val modifier = Modifier.clickable(onClickLabel = scoreAll, onClick = onClick).semantics { contentDescription = desc }
    if (t.predator) PredatorAverageBadge(avgText, businessText, locked, showBusiness, life, business, modifier)
    else ClassicAverageBadge(average, avgText, businessText, locked, showBusiness, life, business, modifier)
}

private val BADGE_W = 116.dp
private val BADGE_H_BASE = 54.dp
private val BADGE_BAND_H = 12.dp

/**
 * Predator: a wide target frame (bold 14dp accent corner brackets over a dashed accent outline) with a reticle
 * watermark behind the numbers, 12dp taller for each scoring-time band. A due scoring turns it amber.
 */
@Composable
private fun PredatorAverageBadge(
    avgText: String,
    businessText: String,
    locked: Boolean,
    showBusiness: Boolean,
    life: Freshness,
    business: Freshness?,
    modifier: Modifier,
) {
    val t = LocalTok.current
    val accent = if (life.due) Warn else t.accent
    val panel = t.pd.panel
    val hasBottom = business != null
    val h = BADGE_H_BASE + BADGE_BAND_H + if (hasBottom) BADGE_BAND_H else 0.dp
    val halo = Shadow(t.pd.well, blurRadius = 8f)
    Box(
        modifier.size(BADGE_W, h).drawBehind {
            val w = size.width
            val ht = size.height
            drawRect(panel)
            // Dashed outline, then the solid corner brackets over it
            val s = 1.dp.toPx()
            drawRect(
                accent.copy(alpha = 0.6f), topLeft = Offset(s / 2, s / 2), size = Size(w - s, ht - s),
                style = Stroke(s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
            val arm = 14.dp.toPx()
            val bw = 2.dp.toPx()
            val o = bw / 2
            fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(accent, Offset(x1, y1), Offset(x2, y2), bw)
            l(0f, o, arm, o); l(o, 0f, o, arm)
            l(w, o, w - arm, o); l(w - o, 0f, w - o, arm)
            l(0f, ht - o, arm, ht - o); l(o, ht, o, ht - arm)
            l(w, ht - o, w - arm, ht - o); l(w - o, ht, w - o, ht - arm)
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (locked) {
                    Ic(LucideIcons.LOCK, 24.dp, t.text)
                } else {
                    Ic(LucideIcons.USER, 14.dp, t.ink)
                    Spacer(Modifier.width(4.dp))
                    val size = if (showBusiness) (if (avgText.length >= 4) 19.sp else 22.sp) else if (avgText.length >= 4) 23.sp else 27.sp
                    Text(avgText, style = t.label(size, t.text).copy(letterSpacing = 0.sp, shadow = halo, lineHeight = size))
                    Text(
                        "/10", style = pdCaptionStyle(10.sp, t.dim, letterSpacing = 0.sp).copy(shadow = halo),
                        modifier = Modifier.align(Alignment.Bottom).padding(start = 2.dp, bottom = 3.dp),
                    )
                }
            }
            if (showBusiness) {
                Box(
                    Modifier.padding(vertical = 2.dp).size(40.dp, 1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, t.accent.copy(alpha = 0.55f), Color.Transparent))),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic(LucideIcons.BRIEFCASE, 14.dp, t.ink)
                    Spacer(Modifier.width(4.dp))
                    Text(businessText, style = t.label(17.sp, t.ink).copy(letterSpacing = 0.sp, shadow = halo, lineHeight = 17.sp))
                    Text(
                        "/10", style = pdCaptionStyle(9.sp, t.ink.copy(alpha = 0.75f), letterSpacing = 0.sp).copy(shadow = halo),
                        modifier = Modifier.align(Alignment.Bottom).padding(start = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
        BadgeBand(life, LucideIcons.USER, Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
        business?.let { BadgeBand(it, LucideIcons.BRIEFCASE, Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)) }
    }
}

/** A scoring-time band of the Predator badge: a check when scored today, else the section's icon, and the time. */
@Composable
private fun BadgeBand(f: Freshness, icon: Int, modifier: Modifier) {
    val t = LocalTok.current
    val ink = if (f.warn) amberInk else t.ink
    Row(modifier.height(BADGE_BAND_H), verticalAlignment = Alignment.CenterVertically) {
        Ic(if (f.fresh) LucideIcons.CHECK else icon, 10.dp, ink)
        Spacer(Modifier.width(4.dp))
        Text(f.short().uppercase(), style = pdCaptionStyle(9.sp, ink).copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/**
 * Classic: a rounded glass capsule with a faint liquid fill in the average's score color, the business average
 * (sky) on a second line, and a clock line above (life) and below (business).
 */
@Composable
private fun ClassicAverageBadge(
    average: Double,
    avgText: String,
    businessText: String,
    locked: Boolean,
    showBusiness: Boolean,
    life: Freshness,
    business: Freshness?,
    modifier: Modifier,
) {
    val t = LocalTok.current
    val color = scoreColor(average.roundToInt())
    val shape = RoundedCornerShape(16.dp)
    val mono = MonoFontFamily
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FreshnessLine(life)
        Box(
            Modifier.height(if (showBusiness) 44.dp else 36.dp).defaultMinSize(minWidth = 72.dp).clip(shape).background(t.card)
                .drawBehind {
                    if (!locked) {
                        val h = size.height * (average.coerceIn(0.0, 10.0) / 10.0).toFloat()
                        drawRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.03f), color.copy(alpha = 0.07f)), startY = size.height - h, endY = size.height), topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                    }
                }
                .border(1.dp, t.accent.copy(alpha = if (life.due) 0.4f else 0.3f), shape)
                .then(modifier)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (locked) {
                Ic(LucideIcons.LOCK, 16.dp, t.text)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(LucideIcons.USER, 12.dp, t.dim)
                        Spacer(Modifier.width(4.dp))
                        Text(avgText, style = t.body(18.sp, t.text, FontWeight.Black).copy(fontFamily = mono, lineHeight = 18.sp))
                        Text("/10", style = t.body(10.sp, t.dim, FontWeight.SemiBold).copy(fontFamily = mono), modifier = Modifier.align(Alignment.Bottom).padding(start = 2.dp))
                    }
                    if (showBusiness) {
                        val sky = if (t.dark) Color(0xFF7DD3FC) else Color(0xFF0369A1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ic(LucideIcons.BRIEFCASE, 14.dp, sky)
                            Spacer(Modifier.width(4.dp))
                            Text(businessText, style = t.body(16.sp, sky, FontWeight.Bold).copy(fontFamily = mono, lineHeight = 16.sp))
                            Text("/10", style = t.body(10.sp, sky.copy(alpha = 0.75f), FontWeight.SemiBold).copy(fontFamily = mono), modifier = Modifier.align(Alignment.Bottom).padding(start = 2.dp))
                        }
                    }
                }
            }
        }
        business?.let { FreshnessLine(it) }
    }
}

/** Classic clock line: a status icon (check, alert, ping dot or clock) and how long ago, in the tone's color. */
@Composable
private fun FreshnessLine(f: Freshness) {
    val t = LocalTok.current
    val amber = amberInk
    val emerald = emeraldInk
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            f.fresh -> Ic(LucideIcons.CHECK, 10.dp, emerald)
            f.days == null -> Ic(LucideIcons.CIRCLE_ALERT, 10.dp, amber)
            f.warn -> PulsingDot(Color(0xFFF59E0B))
            else -> Ic(LucideIcons.CLOCK, 10.dp, t.accent)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            f.short(),
            style = t.body(10.sp, if (f.fresh) emerald else if (f.warn) amber else t.dim, FontWeight.SemiBold).copy(fontFamily = MonoFontFamily),
            maxLines = 1,
        )
    }
}

/** The eye button: shows every hidable domain when all are hidden, else hides them all. */
@Composable
internal fun HideAllToggle(allHidden: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (allHidden) R.string.scoring_show_all else R.string.scoring_hide_all)
    UtilityButton(if (allHidden) LucideIcons.EYE else LucideIcons.EYE_OFF, label, onClick)
}

/** Opens the scoring history. */
@Composable
internal fun HistoryButton(onClick: () -> Unit) =
    UtilityButton(LucideIcons.HISTORY, stringResource(R.string.scoring_history), onClick)

/**
 * A view-utility button, 40dp: Predator's target frame (short 5dp accent brackets over a faint outline), Classic's
 * outlined circle.
 */
@Composable
private fun UtilityButton(icon: Int, label: String, onClick: () -> Unit) {
    val t = LocalTok.current
    val shape = if (t.predator) RectangleShape else CircleShape
    Box(
        Modifier.size(40.dp).clip(shape)
            .let { if (t.predator) it.background(t.pd.panel) else it }
            .border(1.dp, if (t.predator) t.pd.edge else t.dim.copy(alpha = 0.3f), shape)
            .let { if (t.predator) it.bracketCorners(t.accent, arm = 5.dp, stroke = 1.5.dp, tick = 0.dp) else it }
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ic(icon, 16.dp, t.dim, label)
    }
}

private fun hexPath(cx: Float, cy: Float, r: Float) = Path().apply {
    for (i in 0 until 6) {
        val a = (PI / 3 * i - PI / 2).toFloat()
        val x = cx + r * cos(a)
        val y = cy + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The web's ping dot: a solid dot with a fading ring growing out of it. */
@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "ping")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Restart), label = "ping")
    Box(Modifier.size(11.dp).drawBehind {
        val r = 4.dp.toPx()
        drawCircle(color, radius = r * (1f + progress), alpha = 0.75f * (1f - progress))
        drawCircle(color, radius = r)
    })
}

// ── Rating dialog ───────────────────────────────────────────────────────────

/**
 * Rating, as the web's DomainRatingModal (one domain: tapping a score saves and closes) or, for every domain, a
 * step-through with Back / Next and Save at the end. Scores 1–10 in two rows of five, with the score's description
 * and the domain's question.
 */
@Composable
internal fun RatingDialog(domains: List<ScoreDomainUi>, onDismiss: () -> Unit, onSave: (Map<String, Int>) -> Unit) {
    val t = LocalTok.current
    val single = domains.size == 1
    var step by remember { mutableIntStateOf(0) }
    val picks = remember { mutableStateMapOf<String, Int>().apply { domains.forEach { d -> d.rating?.takeIf { it > 0 }?.let { put(d.id, it) } } } }
    val d = domains[step.coerceIn(domains.indices)]
    val selected = picks[d.id]
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().framed(t, t.card, edge = t.line, cut = if (t.predator) 10.dp else 24.dp).padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    if (!single) {
                        Text(
                            t.s(stringResource(R.string.rating_title_all)) + "  ·  " + stringResource(R.string.rating_step, step + 1, domains.size),
                            style = t.caption(10.sp), maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        t.s(scoreDomainName(d)),
                        style = if (t.predator) t.label(20.sp) else t.body(19.sp, t.ink, FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.item_cancel), onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Ic(LucideIcons.CLOSE, 18.dp, t.dim, stringResource(R.string.item_cancel))
                }
            }
            question(d.position)?.let { q ->
                Spacer(Modifier.height(8.dp))
                Text(stringResource(q), style = t.body(13.sp, t.dim))
            }
            Spacer(Modifier.height(14.dp))
            // The chosen score, large, with its description in the score's color
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(selected?.toString() ?: "–", style = if (t.predator) t.label(40.sp, t.text).copy(letterSpacing = 0.sp) else t.body(38.sp, t.text, FontWeight.Black))
                Text("/10", style = t.caption(12.sp), modifier = Modifier.padding(start = 3.dp, bottom = 8.dp))
            }
            Text(
                selected?.let { stringResource(SCORE_DESCRIPTIONS[it - 1]) }.orEmpty(),
                style = (if (t.predator) t.label(13.sp, selected?.let { SoftScore[it - 1] } ?: t.dim) else t.body(13.sp, selected?.let { SoftScore[it - 1] } ?: t.dim, FontWeight.Bold))
                    .copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )
            Spacer(Modifier.height(10.dp))
            for (row in 0..1) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in row * 5 + 1..row * 5 + 5) {
                        RatingButton(n, selected == n, Modifier.weight(1f)) {
                            picks[d.id] = n
                            if (single) onSave(mapOf(d.id to n))
                        }
                    }
                }
            }
            if (!single) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step > 0) DialogButton(stringResource(R.string.rating_back), primary = false, Modifier.weight(1f)) { step-- }
                    else Spacer(Modifier.weight(1f))
                    if (step < domains.lastIndex) DialogButton(stringResource(R.string.rating_next), primary = true, Modifier.weight(1f)) { step++ }
                    else DialogButton(stringResource(R.string.item_save), primary = true, Modifier.weight(1f), enabled = picks.isNotEmpty()) { onSave(picks.toMap()) }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(n: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTok.current
    val shape = t.shape(5.dp)
    val color = SoftScore[n - 1]
    Box(
        modifier.height(44.dp).clip(shape)
            .background(if (selected) color else if (t.predator) t.pd.well else t.well, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) PredatorColors.mix(color, Color.Black, 0.25f) else t.line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = (if (t.predator) t.label(17.sp, if (selected) Color(0xFF1A1A1A) else t.text) else t.body(16.sp, if (selected) Color(0xFF1A1A1A) else t.text, FontWeight.Bold)).copy(letterSpacing = 0.sp))
    }
}

@Composable
private fun DialogButton(label: String, primary: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalTok.current
    val shape: androidx.compose.ui.graphics.Shape = if (t.predator) pdParallelogramShape(8.dp) else RoundedCornerShape(12.dp)
    Box(
        modifier.alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(if (primary) t.accent else t.tint, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(t.s(label), style = if (t.predator) t.label(13.sp, if (primary) t.onAccent else t.onTint) else t.body(14.sp, if (primary) t.onAccent else t.onTint, FontWeight.Bold), maxLines = 1)
    }
}

/** utils/scoring.ts SCORE_DESCRIPTIONS: 1 Rock Bottom … 10 Flourishing */
private val SCORE_DESCRIPTIONS = listOf(
    R.string.rating_desc_1, R.string.rating_desc_2, R.string.rating_desc_3, R.string.rating_desc_4, R.string.rating_desc_5,
    R.string.rating_desc_6, R.string.rating_desc_7, R.string.rating_desc_8, R.string.rating_desc_9, R.string.rating_desc_10,
)

/** The domain's rating question by position (domains.json info.question.*) */
internal fun question(position: Int): Int? = when (position) {
    1 -> R.string.rating_question_1
    2 -> R.string.rating_question_2
    3 -> R.string.rating_question_3
    4 -> R.string.rating_question_4
    5 -> R.string.rating_question_5
    10 -> R.string.rating_question_10
    11 -> R.string.rating_question_11
    12 -> R.string.rating_question_12
    13 -> R.string.rating_question_13
    14 -> R.string.rating_question_14
    15 -> R.string.rating_question_15
    16 -> R.string.rating_question_16
    17 -> R.string.rating_question_17
    18 -> R.string.rating_question_18
    19 -> R.string.rating_question_19
    else -> null
}

/** The domain's own name; its default by position when it has none or is encrypted and locked. */
@Composable
internal fun scoreDomainName(d: ScoreDomainUi): String = d.name?.takeIf { it.isNotBlank() } ?: when (d.position) {
    1 -> stringResource(R.string.today_integrity)
    2 -> "Serenity"
    3 -> "Vitality"
    4 -> "Stability"
    5 -> "Tenacity"
    10 -> "Trayectory"
    11 -> "Resiliency"
    12 -> "Community"
    13 -> "Mastery"
    14 -> "Felicity"
    15 -> "Prosperity"
    16 -> "Residency"
    17 -> "Mobility"
    18 -> "Efficacy"
    19 -> "Property"
    20 -> "Delivery"
    21 -> "Solvency"
    22 -> "Visibility"
    23 -> "Fidelity"
    24 -> "Capacity"
    25 -> "Strategy"
    26 -> "Security"
    27 -> "Clarity"
    28 -> "Discovery"
    29 -> "Utility"
    else -> stringResource(R.string.today_locked)
}

// ── Colors ──────────────────────────────────────────────────────────────────

/** A Predator domain hue (predator-domain-colors.css): the accent fill and the text on it, light and dark. */
private class Hue(val light: Long, val lightOn: Long, val dark: Long, val darkOn: Long)

private val DomainHues = mapOf(
    "red" to Hue(0xFFDC2626, 0xFFFFFFFF, 0xFFF87171, 0xFF450A0A),
    "orange" to Hue(0xFFC2410C, 0xFFFFFFFF, 0xFFFB923C, 0xFF431407),
    "amber" to Hue(0xFFD97706, 0xFF451A03, 0xFFFBBF24, 0xFF451A03),
    "yellow" to Hue(0xFFCA8A04, 0xFF422006, 0xFFFACC15, 0xFF422006),
    "lime" to Hue(0xFF65A30D, 0xFF1A2E05, 0xFFA3E635, 0xFF1A2E05),
    "green" to Hue(0xFF16A34A, 0xFF052E16, 0xFF4ADE80, 0xFF052E16),
    "emerald" to Hue(0xFF047857, 0xFFFFFFFF, 0xFF34D399, 0xFF022C22),
    "teal" to Hue(0xFF0F766E, 0xFFFFFFFF, 0xFF2DD4BF, 0xFF042F2E),
    "cyan" to Hue(0xFF0E7490, 0xFFFFFFFF, 0xFF22D3EE, 0xFF083344),
    "sky" to Hue(0xFF0369A1, 0xFFFFFFFF, 0xFF38BDF8, 0xFF082F49),
    "blue" to Hue(0xFF2563EB, 0xFFFFFFFF, 0xFF60A5FA, 0xFF172554),
    "indigo" to Hue(0xFF4F46E5, 0xFFFFFFFF, 0xFF818CF8, 0xFF1E1B4B),
    "violet" to Hue(0xFF7C3AED, 0xFFFFFFFF, 0xFFA78BFA, 0xFF2E1065),
    "purple" to Hue(0xFF9333EA, 0xFFFFFFFF, 0xFFC084FC, 0xFF3B0764),
    "fuchsia" to Hue(0xFFC026D3, 0xFFFFFFFF, 0xFFE879F9, 0xFF4A044E),
    "pink" to Hue(0xFFDB2777, 0xFFFFFFFF, 0xFFF472B6, 0xFF500724),
    "rose" to Hue(0xFFE11D48, 0xFFFFFFFF, 0xFFFB7185, 0xFF4C0519),
    "stone" to Hue(0xFF57534E, 0xFFFFFFFF, 0xFFA8A29E, 0xFF0C0A09),
    "slate" to Hue(0xFF475569, 0xFFFFFFFF, 0xFF94A3B8, 0xFF020617),
    "zinc" to Hue(0xFF52525B, 0xFFFFFFFF, 0xFFA1A1AA, 0xFF09090B),
)

/** The domain's hue and the text color on it; the scheme accent when it has none. */
@Composable
private fun domainHue(name: String?): Pair<Color, Color> {
    val t = LocalTok.current
    val hue = DomainHues[name] ?: return t.accent to t.onAccent
    return if (t.dark) Color(hue.dark) to Color(hue.darkOn) else Color(hue.light) to Color(hue.lightOn)
}

/** Moves the color [amount] of the way to its gray (CSS grayscale) */
private fun Color.desaturate(amount: Float): Color {
    val l = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return Color(red + (l - red) * amount, green + (l - green) * amount, blue + (l - blue) * amount, alpha)
}

/**
 * The web's HexCorner: a honeycomb in the top-right corner fading out along the diagonal to the bottom-left
 * (160 × 84 at grid radius 10), drawn under the content.
 */
internal fun Modifier.hexCorner(color: Color, opacity: Float, width: Dp = 160.dp, height: Dp = 84.dp, grid: Dp = 10.dp): Modifier = drawBehind {
    val w = width.toPx()
    val h = height.toPx()
    val r = grid.toPx()
    val colStep = sqrt(3f) * r
    val rowStep = 1.5f * r
    val left = size.width - w
    var row = 0
    while (row * rowStep <= h + r) {
        val offset = if (row % 2 != 0) colStep / 2f else 0f
        var col = 0
        while (col * colStep <= w + r) {
            val cx = w - col * colStep - offset
            val cy = row * rowStep
            val falloff = (w - cx) / w + cy / h
            if (falloff < 1f) {
                val strength = (1f - falloff).pow(1.6f)
                val cell = hexPath(left + cx, cy, r * 0.84f)
                drawPath(cell, color, alpha = strength * 0.22f * opacity)
                drawPath(cell, color, alpha = strength * 0.6f * opacity, style = Stroke(r * 0.12f))
            }
            col++
        }
        row++
    }
}
