package com.kalai73.lifemapp.widgets.core.presentation

import com.kalai73.lifemapp.widgets.core.crypto.EncryptedField
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.model.CachedState
import com.kalai73.lifemapp.widgets.core.model.EncryptionState
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.ScoringDomain
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

object MonthlyPresenter {
    private val SCORE_TO_COLOR = listOf("red", "red", "orange", "orange", "amber", "yellow", "lime", "green", "emerald", "emerald")

    fun present(
        state: CachedState,
        authState: AuthState,
        dataKey: String?,
        now: Long = System.currentTimeMillis(),
        deviceZone: ZoneId = ZoneId.systemDefault(),
    ): MonthlyUi {
        val snapshot = state.snapshot
        if (authState == AuthState.SIGNED_OUT) return MonthlyUi.SignedOut.copy(themeMode = state.themeMode)
        val settings = snapshot?.settings
        val zone = runCatching { ZoneId.of(settings?.timezone ?: deviceZone.id) }.getOrDefault(deviceZone)
        val language = DateLabels.baseLanguage(settings?.language)
        val today = LocalDate.now(java.time.Clock.fixed(Instant.ofEpochMilli(now), deviceZone))
        val encryption = when {
            settings?.encryptionEnabled != true -> EncryptionState.DISABLED
            dataKey != null -> EncryptionState.UNLOCKED
            else -> EncryptionState.LOCKED
        }
        val key = dataKey.takeIf { encryption == EncryptionState.UNLOCKED }

        if (snapshot == null) {
            return MonthlyUi.SignedOut.copy(
                authState = authState,
                themeMode = state.themeMode,
                today = today,
                todayLabel = DateLabels.dayAndDate(today, DateLabels.DATE_FORMAT, language),
                syncing = state.isSyncing(now),
                error = state.lastError,
            )
        }

        // Ratings for all life domains (position < 20)
        val ratings = snapshot.domains.ratings + (key?.let { k ->
            snapshot.domains.encryptedRatings.mapNotNull { EncryptedField.decrypt(it, k)?.toIntOrNull() }
        } ?: emptyList())
        val averageRating = if (ratings.isEmpty()) null else (ratings.average() * 10).roundToInt() / 10.0

        val unit = if (snapshot.weather?.unit == "fahrenheit") "°F" else "°C"
        val weather = snapshot.weather?.let { w ->
            WeatherUi(
                "${w.high}$unit", "${w.low}$unit", w.condition, w.icon, w.location,
                forecast = w.forecast.mapNotNull { d ->
                    val date = runCatching { LocalDate.parse(d.date) }.getOrNull() ?: return@mapNotNull null
                    if (!date.isAfter(today)) return@mapNotNull null
                    ForecastDayUi(DateLabels.shortDayName(date, language), "${d.high}°", "${d.low}°", d.condition, d.icon)
                }.take(5),
            )
        }

        // Domain sources:
        // Integrity: position 1
        // Tenacity: position 5 (for embedding in Trayectory 10)
        // Trayectory: position 10
        // Monthly domains: positions 11..19
        val allDomains = (listOfNotNull(snapshot.integrity) + snapshot.todayDomains + snapshot.monthlyDomains)
            .distinctBy { it.position }

        val integrityRaw = allDomains.firstOrNull { it.position == 1 }
        val tenacityRaw = allDomains.firstOrNull { it.position == 5 }
        val trayectoryRaw = allDomains.firstOrNull { it.position == 10 }
        val otherMonthlyRaw = allDomains.filter { it.position in 11..19 }

        val integrityUi = integrityRaw?.let { WidgetPresenter.integrity(it, key) }
        val tenacityUi = tenacityRaw?.let { WidgetPresenter.integrity(it, key) }
        val trayectoryUiCard = trayectoryRaw?.let { WidgetPresenter.integrity(it, key) }

        val trayectoryUi = trayectoryUiCard?.let {
            TrayectoryUi(
                domain = it,
                embeddedTenacity = tenacityUi,
            )
        }

        // Monthly scoring: positions 10..19
        val scoringMonthlyRaw = (listOfNotNull(trayectoryRaw) + otherMonthlyRaw).sortedBy { it.position }
        val scoringMonthlyUi = scoringMonthlyRaw.map { WidgetPresenter.integrity(it, key) }
        val scoring = scoring(state.scoring, scoringMonthlyUi, encryption, today, zone)

        val hiddenIds = scoring?.domains?.filter { it.hidden }?.map { it.id }?.toSet().orEmpty()

        val otherMonthlyUi = otherMonthlyRaw.map { WidgetPresenter.integrity(it, key) }
        val visibleMonthly = otherMonthlyUi.filterNot { it.domainId in hiddenIds }
        val hiddenMonthly = otherMonthlyUi.filter { it.domainId in hiddenIds }

        return MonthlyUi(
            authState = authState,
            encryption = encryption,
            hasData = true,
            colorScheme = state.colorScheme,
            uiStyle = UiStyle.from(settings?.uiStyle),
            themeMode = state.themeMode,
            language = language,
            username = settings?.username,
            todayLabel = DateLabels.dayAndDate(today, DateLabels.DATE_FORMAT, language),
            today = today,
            eventsCount = snapshot.counts.events,
            tasksCount = snapshot.counts.tasks,
            mailCount = snapshot.counts.mail,
            mailConnected = snapshot.counts.mailConnected,
            mailUrl = snapshot.mail.inboxUrl,
            averageRating = averageRating,
            weather = weather,
            integrity = integrityUi,
            trayectory = trayectoryUi,
            monthlyDomains = visibleMonthly,
            hiddenMonthlyDomains = hiddenMonthly,
            scoring = scoring,
            updatedAt = state.lastSuccessAt.takeIf { it > 0 }?.let { DateLabels.time(Instant.ofEpochMilli(it), zone, settings?.timeFormat) },
            stale = state.lastSuccessAt > 0 && now - state.lastSuccessAt > WidgetPresenter.STALE_AFTER_MS,
            syncing = state.isSyncing(now),
            error = state.lastError,
        )
    }

    internal fun scoring(state: Scoring?, cards: List<IntegrityUi>, encryption: EncryptionState, today: LocalDate, zone: ZoneId): MonthlyScoringUi? {
        if (cards.isEmpty()) return null
        val domains = cards.sortedBy { it.position }.map { card ->
            val stored = state?.domains?.firstOrNull { it.id == card.domainId }
            val unhidable = ScoringDomain.isUnhidable(card.position, stored?.name ?: card.name)
            val hidden = state != null && !unhidable && (state.hiddenDomainIds?.contains(card.domainId) ?: true)
            val custom = stored?.color?.takeIf { it in WidgetPresenter.DOMAIN_COLOR_NAMES }
            val rating = card.rating
            ScoreDomainUi(
                id = card.domainId,
                position = card.position,
                name = card.name,
                rating = rating,
                hidden = hidden,
                unhidable = unhidable,
                colorName = custom ?: rating?.takeIf { it > 0 }?.let { SCORE_TO_COLOR[(it - 1).coerceIn(0, 9)] },
            )
        }
        return MonthlyScoringUi(
            domains = domains,
            daysSinceScored = state?.lastCompleted?.let { WidgetPresenter.daysSince(it, today, zone) },
            locked = encryption == EncryptionState.LOCKED,
            ready = state != null,
        )
    }
}

