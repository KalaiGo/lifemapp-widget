package com.kalai73.lifemapp.widgets.core.model

import kotlinx.serialization.Serializable

/**
 * What the Today screen's scoring section needs beyond the snapshot, read by the app straight from Supabase
 * (lm_user_settings and lm_life_domains), as the web's ScoringCircles reads it from its stores.
 */
@Serializable
data class Scoring(
    /**
     * Domains hidden from the Today page (preferences.hidden_domain_ids). Null when never set: the web then hides
     * every domain that can be hidden (isDomainHidden in scoring-circles.tsx).
     */
    val hiddenDomainIds: List<String>? = null,
    /** monthly_scoring_last_completed: an ISO instant, or "YYYY-MM" from older rows; null when never scored */
    val lastCompleted: String? = null,
    /** Every domain of the user, to materialize the hidden list when it was never set (as the web's toggle does) */
    val domains: List<ScoringDomain> = emptyList(),
    /** The events card's days to show (preferences.days_ahead, 1–14 on the web); null when never set */
    val eventsDaysAhead: Int? = null,
    /** The events card's event cap (preferences.max_events, 1–20 on the web); null when never set */
    val eventsMaxEvents: Int? = null,
)

@Serializable
data class ScoringDomain(
    val id: String,
    val position: Int = 0,
    /** Plain name, or "[encrypted]" */
    val name: String? = null,
    /** Custom color name (one of the 20 Tailwind families), null when not set */
    val color: String? = null,
) {
    /** Integrity, Tenacity and Trayectory are always shown (scoring-circles.tsx#isUnhidableDomain) */
    val unhidable: Boolean get() = isUnhidable(position, name)

    companion object {
        fun isUnhidable(position: Int, name: String?): Boolean {
            if (position == 1 || position == 5 || position == 10) return true
            val n = name.orEmpty().lowercase()
            return listOf("integrity", "tenacity", "trayectory", "trajectory").any { it in n }
        }
    }
}
