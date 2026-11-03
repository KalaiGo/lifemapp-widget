package com.kalai73.lifemapp.widgets.core.presentation

import com.kalai73.lifemapp.widgets.core.crypto.Aes
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.Domains
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.ScoringDomain
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.SnapshotSettings
import com.kalai73.lifemapp.widgets.core.model.Topic
import com.kalai73.lifemapp.widgets.core.model.TopicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Plain JVM tests for MonthlyPresenter. */
class MonthlyPresenterTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = ZonedDateTime.of(2026, 11, 3, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun signedOut_returnsSignedOutState() {
        val ui = MonthlyPresenter.present(CachedState(), AuthState.SIGNED_OUT, null, now, zone)
        assertEquals(AuthState.SIGNED_OUT, ui.authState)
        assertFalse(ui.hasData)
        assertNull(ui.integrity)
        assertNull(ui.trayectory)
        assertTrue(ui.monthlyDomains.isEmpty())
    }

    @Test
    fun domainScopeAndEmbeddedTenacityInversion() {
        val integrity = Integrity(domainId = "d1", position = 1, name = "Integrity")
        val serenity = Integrity(domainId = "d2", position = 2, name = "Serenity")
        val tenacity = Integrity(
            domainId = "d5",
            position = 5,
            name = "Tenacity",
            topics = listOf(Topic(id = "top5", name = "Habits")),
        )
        val trayectory = Integrity(
            domainId = "d10",
            position = 10,
            name = "Trayectory",
            topics = listOf(Topic(id = "top10", name = "Goals")),
        )
        val finances = Integrity(domainId = "d11", position = 11, name = "Finances")
        val career = Integrity(domainId = "d12", position = 12, name = "Career")
        val company = Integrity(domainId = "d20", position = 20, name = "Company Mapp")

        val state = CachedState(
            snapshot = Snapshot(
                settings = SnapshotSettings(username = "Tester"),
                integrity = integrity,
                todayDomains = listOf(serenity, tenacity),
                monthlyDomains = listOf(trayectory, finances, career, company),
            ),
            scoring = Scoring(
                hiddenDomainIds = emptyList(), // Nothing hidden
            ),
        )

        val ui = MonthlyPresenter.present(state, AuthState.SIGNED_IN, null, now, zone)

        // 1. Integrity is at root
        assertNotNull(ui.integrity)
        assertEquals("Integrity", ui.integrity?.name)
        assertEquals(1, ui.integrity?.position)

        // 2. Trayectory embeds Tenacity
        assertNotNull(ui.trayectory)
        assertEquals("Trayectory", ui.trayectory?.domain?.name)
        assertEquals(10, ui.trayectory?.domain?.position)
        assertEquals(1, ui.trayectory?.domain?.topics?.size)
        assertEquals("Goals", ui.trayectory?.domain?.topics?.first()?.name)

        assertNotNull(ui.trayectory?.embeddedTenacity)
        assertEquals("Tenacity", ui.trayectory?.embeddedTenacity?.name)
        assertEquals(5, ui.trayectory?.embeddedTenacity?.position)
        assertEquals("Habits", ui.trayectory?.embeddedTenacity?.topics?.first()?.name)

        // 3. Monthly domains list contains only 11 and 12 (not 2 Serenity, not 5 Tenacity, not 20 Company)
        val monthlyPositions = ui.monthlyDomains.map { it.position }
        assertEquals(listOf(11, 12), monthlyPositions)
    }

    @Test
    fun unhidableRule_trayectoryNeverHidden() {
        val trayectory = Integrity(domainId = "d10", position = 10, name = "Trayectory")
        val finances = Integrity(domainId = "d11", position = 11, name = "Finances")

        // Case A: hiddenDomainIds is null -> non-permanent domains default to hidden, unhidable stay visible
        val stateDefaultHidden = CachedState(
            snapshot = Snapshot(
                monthlyDomains = listOf(trayectory, finances),
            ),
            scoring = Scoring(hiddenDomainIds = null),
        )
        val uiA = MonthlyPresenter.present(stateDefaultHidden, AuthState.SIGNED_IN, null, now, zone)
        val trayectoryScoreA = uiA.scoring?.domains?.firstOrNull { it.id == "d10" }
        val financesScoreA = uiA.scoring?.domains?.firstOrNull { it.id == "d11" }

        assertNotNull(trayectoryScoreA)
        assertTrue(trayectoryScoreA!!.unhidable)
        assertFalse(trayectoryScoreA.hidden)

        assertNotNull(financesScoreA)
        assertFalse(financesScoreA!!.unhidable)
        assertTrue(financesScoreA.hidden)
        // Finances is in hidden list
        assertTrue(uiA.hiddenMonthlyDomains.any { it.domainId == "d11" })
        assertFalse(uiA.monthlyDomains.any { it.domainId == "d11" })

        // Case B: hiddenDomainIds explicitly includes d10
        val stateExplicitHidden = CachedState(
            snapshot = Snapshot(
                monthlyDomains = listOf(trayectory, finances),
            ),
            scoring = Scoring(hiddenDomainIds = listOf("d10", "d11")),
        )
        val uiB = MonthlyPresenter.present(stateExplicitHidden, AuthState.SIGNED_IN, null, now, zone)
        val trayectoryScoreB = uiB.scoring?.domains?.firstOrNull { it.id == "d10" }
        assertNotNull(trayectoryScoreB)
        assertTrue(trayectoryScoreB!!.unhidable)
        assertFalse(trayectoryScoreB.hidden) // Unhidable overrides explicit list
    }

    @Test
    fun scoringCircles_includesPositions10To19() {
        val domains = (10..19).map { pos ->
            Integrity(domainId = "d$pos", position = pos, name = "Domain $pos", rating = (pos % 10).toDouble())
        }
        val state = CachedState(
            snapshot = Snapshot(
                monthlyDomains = domains,
                domains = Domains(ratings = listOf(7, 8, 9)),
            ),
            scoring = Scoring(
                domains = domains.map { ScoringDomain(id = it.domainId, position = it.position, name = it.name) },
                hiddenDomainIds = emptyList(),
            ),
        )

        val ui = MonthlyPresenter.present(state, AuthState.SIGNED_IN, null, now, zone)
        assertNotNull(ui.scoring)
        assertEquals(10, ui.scoring?.domains?.size)
        assertEquals(10, ui.scoring?.domains?.first()?.position)
        assertEquals(19, ui.scoring?.domains?.last()?.position)
        assertEquals(8.0, ui.averageRating)
    }

    @Test
    fun decryption_decryptsMonthlyDomainsAndItems() {
        val dataKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
        fun enc(text: String): String = Aes.encrypt(text, dataKey).let { """{"encrypted":"${it.encrypted}","iv":"${it.iv}","tag":"${it.tag}"}""" }


        val encName = enc("Secret Career")
        val encInfo = enc("Confidential Info")
        val encTopic = enc("Top Secret Topic")
        val encContent = enc("<p>Promoted to Principal</p>")

        val career = Integrity(
            domainId = "d12",
            position = 12,
            encryptedName = encName,
            encryptedInfoBox = encInfo,
            topics = listOf(
                Topic(
                    id = "t1",
                    encryptedName = encTopic,
                    items = listOf(
                        TopicItem(id = "i1", encryptedContent = encContent),
                    ),
                ),
            ),
        )

        val state = CachedState(
            snapshot = Snapshot(
                settings = SnapshotSettings(encryptionEnabled = true),
                monthlyDomains = listOf(career),
            ),
            scoring = Scoring(hiddenDomainIds = emptyList()),
        )

        // When locked (no key)
        val uiLocked = MonthlyPresenter.present(state, AuthState.SIGNED_IN, null, now, zone)
        val lockedDomain = uiLocked.monthlyDomains.first()
        assertNull(lockedDomain.name)
        assertNull(lockedDomain.infoBox)
        assertNull(lockedDomain.topics.first().name)
        assertNull(lockedDomain.topics.first().items.first().content)
        assertTrue(lockedDomain.topics.first().items.first().hasContent) // marked as having encrypted content

        // When unlocked (with key)
        val uiUnlocked = MonthlyPresenter.present(state, AuthState.SIGNED_IN, dataKey, now, zone)
        val unlockedDomain = uiUnlocked.monthlyDomains.first()
        assertEquals("Secret Career", unlockedDomain.name)
        assertEquals("Confidential Info", unlockedDomain.infoBox)
        assertEquals("Top Secret Topic", unlockedDomain.topics.first().name)
        assertEquals("<p>Promoted to Principal</p>", unlockedDomain.topics.first().items.first().content)
    }
}
