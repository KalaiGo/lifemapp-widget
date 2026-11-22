package com.kalai73.lifemapp.widgets.core.presentation

import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The BUSINESS MAPP page (the web's /business): the Monthly page's header, then Integrity (position 1) and the
 * business domains (positions 20–29) with their own scoring section. As on the web, business domains are scored
 * apart from the life domains: the badge shows the business average, and the scoring clock is the business one.
 * Drawn by the Monthly screen, so it comes as a [MonthlyUi] with [MonthlyUi.business] set.
 */
object BusinessPresenter {
    /** Positions of the BUSINESS MAPP domains (domains-list.tsx, viewMode 'business') */
    val POSITIONS = 20..29

    fun present(
        state: CachedState,
        authState: AuthState,
        dataKey: String?,
        now: Long = System.currentTimeMillis(),
        deviceZone: ZoneId = ZoneId.systemDefault(),
    ): MonthlyUi {
        val base = MonthlyPresenter.present(state, authState, dataKey, now, deviceZone).copy(business = true)
        val snapshot = state.snapshot ?: return base
        if (!base.hasData) return base
        val key = dataKey.takeIf { base.encryption == EncryptionState.UNLOCKED }
        val zone = runCatching { ZoneId.of(snapshot.settings?.timezone ?: deviceZone.id) }.getOrDefault(deviceZone)
        val today = LocalDate.now(java.time.Clock.fixed(Instant.ofEpochMilli(now), deviceZone))

        val cards = snapshot.monthlyDomains.filter { it.position in POSITIONS }
            .distinctBy { it.position }.sortedBy { it.position }
            .map { WidgetPresenter.integrity(it, key) }
        val scoring = MonthlyPresenter.scoring(state.scoring, cards, base.encryption, today, zone)?.copy(
            daysSinceScored = state.scoring?.businessLastCompleted?.let { WidgetPresenter.daysSince(it, today, zone) },
            // The badge's main number is already the business average: no second line
            businessAverage = null,
        )
        val average = WidgetPresenter.businessAverage(state.scoring, key) ?: cardsAverage(cards)
        val hiddenIds = scoring?.domains?.filter { it.hidden }?.map { it.id }?.toSet().orEmpty()

        return base.copy(
            averageRating = average,
            trayectory = null,
            monthlyDomains = cards.filterNot { it.domainId in hiddenIds },
            hiddenMonthlyDomains = cards.filter { it.domainId in hiddenIds },
            scoring = scoring,
        )
    }

    /** The business average from the cards themselves, when the scoring state was never read */
    private fun cardsAverage(cards: List<IntegrityUi>): Double? {
        val ratings = cards.mapNotNull { it.rating }
        return if (ratings.isEmpty()) null else (ratings.average() * 10).roundToInt() / 10.0
    }
}
