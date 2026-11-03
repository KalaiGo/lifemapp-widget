package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.data.api.ScoringApi
import com.kalai73.lifemapp.widgets.core.data.api.StoredRating
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.model.Domains
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Scoring
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The Today screen's scoring section (the web's ScoringCircles): which domains the Today page hides, and rating
 * them. Changes land in the cache first, so the screen redraws at once.
 */
class ScoringRepository(
    private val api: ScoringApi,
    private val cache: CacheStore,
    private val userId: suspend () -> String?,
    private val dataKey: suspend () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The section's state from the server; the snapshot sync calls this. */
    suspend fun load(): Scoring? = userId()?.let { api.read(it) }

    /**
     * Hides ([changes] value true) or shows domains on the Today page, as the web's toggleDomainHidden and its
     * "Hide all / Show all" button do. A list never set (null: every hidable domain hidden) is materialized first.
     * Integrity, Tenacity and Trayectory stay shown. The list is this device's own: it lives in the cache only and
     * is never read from or written to the web's preferences.hidden_domain_ids.
     */
    suspend fun setHidden(changes: Map<String, Boolean>, onLocalChange: suspend () -> Unit = {}) {
        val before = cache.current().scoring ?: return
        val unhidable = before.domains.filter { it.unhidable }.map { it.id }.toSet()
        val current = before.hiddenDomainIds ?: before.domains.filterNot { it.unhidable }.map { it.id }
        val hide = changes.filter { it.value && it.key !in unhidable }.keys
        val show = changes.filterValues { !it }.keys
        val updated = (current.filterNot { it in show } + hide.filterNot { it in current }).distinct()
        if (updated == before.hiddenDomainIds) return
        cache.update { it.copy(scoring = it.scoring?.copy(hiddenDomainIds = updated)) }
        onLocalChange()
    }

    /**
     * Saves new [ratings] (domain id → 1–10) as the web's rating modal does: one bulk request with every life
     * domain (positions below 20), the others keeping their rating, so the score history records a whole run;
     * with encryption on each rating is encrypted and the column holds 0. Then records the scoring time.
     * A sibling whose stored rating this device can't read is left out rather than overwritten.
     *
     * @return false when the account is encrypted and this device isn't unlocked
     */
    suspend fun rate(ratings: Map<String, Int>, encrypted: Boolean): Boolean {
        if (ratings.isEmpty()) return true
        val user = userId() ?: throw NotSignedInException()
        val key = if (encrypted) dataKey() ?: return false else null
        val stored = api.lifeDomainRatings(user)
        val kept = mutableListOf<StoredRating>()
        val entries = stored.mapNotNull { d ->
            val value = ratings[d.id]?.coerceIn(1, 10) ?: readable(d, key) ?: run { kept += d; return@mapNotNull null }
            if (key != null) ScoringApi.RatingEntry(d.id, 0, EncryptedField.encrypt(value.toString(), key))
            else ScoringApi.RatingEntry(d.id, value)
        }
        if (entries.none { it.domainId in ratings }) return true
        api.saveRatings(entries)
        val now = Instant.ofEpochMilli(clock()).toString()
        // The ratings are saved; a failed timestamp only leaves the "Scored" capsule behind until the next scoring
        runCatching { api.setScoringCompleted(user, now) }

        val byId = entries.associateBy { it.domainId }
        fun card(card: Integrity): Integrity {
            val entry = byId[card.domainId] ?: return card
            return card.copy(rating = entry.rating.toDouble(), encryptedRating = entry.encryptedRating)
        }
        val plain = entries.filter { it.encryptedRating == null }.map { it.rating } +
            kept.filter { it.encryptedRating == null }.map { it.rating.roundToInt() }
        val encryptedRatings = entries.mapNotNull { it.encryptedRating } + kept.mapNotNull { it.encryptedRating }
        cache.update { state ->
            val snapshot = state.snapshot
            state.copy(
                scoring = (state.scoring ?: Scoring()).copy(lastCompleted = now),
                snapshot = snapshot?.copy(
                    integrity = snapshot.integrity?.let(::card),
                    todayDomains = snapshot.todayDomains.map(::card),
                    domains = Domains(
                        averageRating = if (plain.isEmpty()) null else (plain.average() * 10).roundToInt() / 10.0,
                        ratings = plain,
                        encryptedRatings = encryptedRatings,
                    ),
                ),
            )
        }
        return true
    }

    /** A stored rating this device can read: plain, or encrypted with [key]; null otherwise. */
    private fun readable(d: StoredRating, key: String?): Int? = when {
        d.encryptedRating == null -> d.rating.roundToInt()
        key == null -> null
        else -> EncryptedField.decrypt(d.encryptedRating, key)?.toDoubleOrNull()?.roundToInt()
    }?.coerceIn(0, 10)
}
