package com.kalai73.lifemapp.widgets.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.presentation.MonthlyScoringUi
import com.kalai73.lifemapp.widgets.core.presentation.ScoreDomainUi

/**
 * The Monthly page's scoring section (scoring-circles.tsx, 10 domains positions 10–19):
 * - Classic: [3, 3, 4] pyramid with row widths 90%, 90%, 100%.
 * - Predator: 2 rows of 5 [5, 5] with alternating zigzag offset (+28.dp for odd columns).
 *
 * Footer contains the shared average badge (across all life domains < 20),
 * the "hide/show all" eye toggle for monthly domains 11–19, and the last-scoring capsule.
 */
@Composable
internal fun MonthlyScoringSection(
    scoring: MonthlyScoringUi,
    average: Double?,
    onToggleHidden: (String) -> Unit,
    onSetAllHidden: (Boolean) -> Unit,
    onRate: (Map<String, Int>) -> Unit,
    onHistory: () -> Unit,
    onUnlock: () -> Unit,
) {
    val t = LocalTok.current
    val haptics = LocalHapticFeedback.current
    var ratingDialogDomains by remember { mutableStateOf<List<ScoreDomainUi>?>(null) }

    ratingDialogDomains?.let { domains ->
        RatingDialog(
            domains = domains,
            onDismiss = { ratingDialogDomains = null },
            onSave = { picks ->
                onRate(picks)
                ratingDialogDomains = null
            },
        )
    }

    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp)
            .framed(t, t.card, edge = t.line, cut = if (t.predator) 8.dp else 24.dp)
            .let { if (t.predator) it.hexCorner(t.accent, opacity = 0.9f) else it }
            .padding(top = 12.dp, bottom = 10.dp),
    ) {
        if (scoring.domains.isNotEmpty()) {
            if (t.predator) {
                PredatorMonthlyGrid(
                    domains = scoring.domains,
                    onRateDomain = { d -> ratingDialogDomains = listOf(d) },
                    onToggleHidden = onToggleHidden,
                    onUnlock = onUnlock,
                )
            } else {
                ClassicMonthlyGrid(
                    domains = scoring.domains,
                    onRateDomain = { d -> ratingDialogDomains = listOf(d) },
                    onToggleHidden = onToggleHidden,
                    onUnlock = onUnlock,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Footer: Average badge + Eye button + Last scoring capsule
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            average?.let { avg ->
                AverageBadge(avg, scoring.locked) {
                    if (scoring.locked) onUnlock()
                    else ratingDialogDomains = scoring.domains
                }
            } ?: Spacer(Modifier.width(1.dp))

            Spacer(Modifier.weight(1f))

            if (scoring.ready) {
                HideAllToggle(scoring.allHidableHidden) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetAllHidden(!scoring.allHidableHidden)
                }
                Spacer(Modifier.width(8.dp))
            }

            ScoringCapsule(
                daysSinceScored = scoring.daysSinceScored,
                urgency = scoring.urgency,
                scoringDue = scoring.scoringDue,
                onScore = {
                    if (scoring.locked) onUnlock()
                    else ratingDialogDomains = scoring.domains
                },
                onHistory = onHistory,
            )
        }
    }
}

/**
 * Predator style: 2 rows of 5 [5, 5].
 * Alternating items use a zigzag vertical offset (+28.dp) so that name labels above hexagons don't collide.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PredatorMonthlyGrid(
    domains: List<ScoreDomainUi>,
    onRateDomain: (ScoreDomainUi) -> Unit,
    onToggleHidden: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val row1 = domains.take(5)
    val row2 = domains.drop(5).take(5)

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(row1, row2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(118.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Top,
            ) {
                row.forEachIndexed { colIndex, d ->
                    val isOdd = colIndex % 2 == 1
                    val align = when (colIndex) {
                        0 -> Alignment.Start
                        row.lastIndex -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }
                    val name = scoreDomainName(d)
                    val rating = d.rating
                    val score = rating?.takeIf { it > 0 } ?: 5
                    val desc = stringResource(R.string.scoring_domain_desc, name, score)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .offset(y = if (isOdd) 28.dp else 0.dp)
                            .semantics { contentDescription = desc }
                            .combinedClickable(
                                onClick = { onRateDomain(d) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!d.unhidable) onToggleHidden(d.id)
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PredatorHex(d, score, name, align)
                    }
                }
            }
        }
    }
}

/**
 * Classic / Material style: 3 rows [3, 3, 4].
 * Row widths: 90%, 90%, 100% for balanced pyramid spread.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClassicMonthlyGrid(
    domains: List<ScoreDomainUi>,
    onRateDomain: (ScoreDomainUi) -> Unit,
    onToggleHidden: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val row1 = domains.take(3)
    val row2 = domains.drop(3).take(3)
    val row3 = domains.drop(6).take(4)

    val rows = listOf(row1 to 0.90f, row2 to 0.90f, row3 to 1.0f)

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEachIndexed { rowIndex, (row, widthFraction) ->
            if (row.isNotEmpty()) {
                if (rowIndex > 0) Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(widthFraction),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { d ->
                        val name = scoreDomainName(d)
                        val rating = d.rating
                        val score = rating?.takeIf { it > 0 } ?: 5
                        val desc = stringResource(R.string.scoring_domain_desc, name, score)

                        Box(
                            modifier = Modifier
                                .semantics { contentDescription = desc }
                                .combinedClickable(
                                    onClick = { onRateDomain(d) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!d.unhidable) onToggleHidden(d.id)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ClassicCircle(d, score, name)
                        }
                    }
                }
            }
        }
    }
}

