package com.kalai73.lifemapp.widgets.core.presentation

import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.ScoringDomain
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Plain JVM tests for BusinessPresenter (BUSINESS MAPP, positions 20–29). */
class BusinessPresenterTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = ZonedDateTime.of(2026, 11, 22, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val integrity = Integrity(domainId = "d1", position = 1, name = "Integrity", rating = 9.0)
    private val trayectory = Integrity(domainId = "d10", position = 10, name = "Trayectory", rating = 2.0)
    private val prosperity = Integrity(domainId = "d15", position = 15, name = "Prosperity", rating = 3.0)
    private val delivery = Integrity(domainId = "d20", position = 20, name = "Delivery", rating = 6.0)
    private val solvency = Integrity(domainId = "d21", position = 21, name = "Solvency", rating = 8.0)

    private fun state(scoring: Scoring) = CachedState(
        snapshot = Snapshot(
            integrity = integrity,
            monthlyDomains = listOf(trayectory, prosperity, solvency, delivery),
        ),
        scoring = scoring,
    )

    private fun scoring(hidden: List<String>?, lastLife: String? = null, lastBusiness: String? = null) = Scoring(
        hiddenDomainIds = hidden,
        lastCompleted = lastLife,
        businessLastCompleted = lastBusiness,
        domains = listOf(
            ScoringDomain("d1", 1, "Integrity", rating = 9.0),
            ScoringDomain("d10", 10, "Trayectory", rating = 2.0),
            ScoringDomain("d15", 15, "Prosperity", rating = 3.0),
            ScoringDomain("d20", 20, "Delivery", rating = 6.0),
            ScoringDomain("d21", 21, "Solvency", rating = 8.0),
        ),
    )

    @Test
    fun showsIntegrityAndBusinessDomainsOnly() {
        val ui = BusinessPresenter.present(state(scoring(hidden = emptyList())), AuthState.SIGNED_IN, null, now, zone)

        assertTrue(ui.business)
        assertEquals("d1", ui.integrity?.domainId)
        assertNull(ui.trayectory)
        assertEquals(listOf(20, 21), ui.monthlyDomains.map { it.position })
        assertEquals(listOf(20, 21), ui.scoring?.domains?.map { it.position })
    }

    @Test
    fun averageAndClockAreTheBusinessOnes() {
        val ui = BusinessPresenter.present(
            state(scoring(hidden = emptyList(), lastLife = "2026-11-22T08:00:00Z", lastBusiness = "2026-11-12T08:00:00Z")),
            AuthState.SIGNED_IN, null, now, zone,
        )

        // (6 + 8) / 2, not mixed with the life domains
        assertEquals(7.0, ui.averageRating!!, 0.0)
        assertNull(ui.scoring?.businessAverage)
        assertEquals(10, ui.scoring?.daysSinceScored)
    }

    @Test
    fun neverScoredBusinessIsDueEvenWhenLifeWasScored() {
        val ui = BusinessPresenter.present(
            state(scoring(hidden = emptyList(), lastLife = "2026-11-22T08:00:00Z")),
            AuthState.SIGNED_IN, null, now, zone,
        )
        assertNull(ui.scoring?.daysSinceScored)
        assertTrue(ui.scoring!!.scoringDue)
    }

    @Test
    fun hiddenBusinessDomainsMoveToTheHiddenList() {
        val ui = BusinessPresenter.present(state(scoring(hidden = listOf("d21"))), AuthState.SIGNED_IN, null, now, zone)

        assertEquals(listOf("d20"), ui.monthlyDomains.map { it.domainId })
        assertEquals(listOf("d21"), ui.hiddenMonthlyDomains.map { it.domainId })
    }

    @Test
    fun noBusinessDomainsGivesAnEmptyPage() {
        val state = CachedState(snapshot = Snapshot(integrity = integrity, monthlyDomains = listOf(trayectory)), scoring = scoring(emptyList()))
        val ui = BusinessPresenter.present(state, AuthState.SIGNED_IN, null, now, zone)

        assertTrue(ui.hasData)
        assertTrue(ui.monthlyDomains.isEmpty() && ui.hiddenMonthlyDomains.isEmpty())
        assertNull(ui.scoring)
    }
}
