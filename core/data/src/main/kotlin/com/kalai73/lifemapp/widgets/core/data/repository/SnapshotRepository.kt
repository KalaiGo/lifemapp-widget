package com.kalai73.lifemapp.widgets.core.data.repository

import com.kalai73.lifemapp.widgets.core.data.api.SnapshotApi
import com.kalai73.lifemapp.widgets.core.data.api.ServerException
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.secure.DeviceLockedException
import com.kalai73.lifemapp.widgets.core.model.CustomTag
import com.kalai73.lifemapp.widgets.core.model.Integrity
import com.kalai73.lifemapp.widgets.core.model.Scoring
import com.kalai73.lifemapp.widgets.core.model.Sections
import com.kalai73.lifemapp.widgets.core.model.Snapshot
import com.kalai73.lifemapp.widgets.core.model.SyncError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.TimeZone

sealed interface SyncResult {
    data object Success : SyncResult
    /** Network or server trouble; worth retrying */
    data class Retry(val error: SyncError) : SyncResult
    /** Signed out or session revoked; retrying won't help */
    data object SignedOut : SyncResult
}

class SnapshotRepository(
    private val api: SnapshotApi,
    private val cache: CacheStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timezone: () -> String = { TimeZone.getDefault().id },
    /** The Today screen's scoring section state, read alongside the snapshot (not part of the web's payload) */
    private val scoring: suspend () -> Scoring? = { null },
    /** The Monthly screen's domains (10–19, and 5 for embed), read alongside the snapshot */
    private val monthlyDomains: suspend () -> List<Integrity>? = { null },
    /** The user's custom context tags, read when the snapshot didn't include them (older server) */
    private val customTags: suspend () -> List<CustomTag>? = { null },
) {
    suspend fun markSyncing() {
        val now = clock()
        cache.update { it.copy(syncing = true, syncStartedAt = now) }
    }

    /** Clears the spinner, even from a cancelled coroutine (a replaced or stopped worker). */
    suspend fun clearSyncing() = withContext(NonCancellable) {
        cache.update { it.copy(syncing = false) }
    }

    suspend fun refresh(daysAhead: Int = 7): SyncResult {
        val startedAt = clock()
        cache.update { it.copy(syncing = true, syncStartedAt = startedAt, lastAttemptAt = startedAt) }
        return try {
            val fresh = api.snapshot(timezone(), daysAhead)
            // A failed read keeps the cached copy, like a failed snapshot section
            val freshScoring = try {
                scoring()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure(e)
                null
            }
            val freshMonthly = try {
                monthlyDomains()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure(e)
                null
            }
            val freshCustomTags = fresh.settings.customTags.takeIf { it.isNotEmpty() } ?: try {
                customTags()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure(e)
                null
            }
            val withMonthly = if (freshMonthly != null) {
                mergeDomainTags(fresh.copy(monthlyDomains = freshMonthly), freshMonthly)
            } else fresh
            cache.update { old ->
                old.copy(
                    snapshot = merge(old.snapshot, withMonthly),
                    // The hidden domains and the business scoring time are this device's own: a sync never replaces them
                    scoring = freshScoring?.copy(
                        hiddenDomainIds = old.scoring?.hiddenDomainIds,
                        businessLastCompleted = old.scoring?.businessLastCompleted,
                    ) ?: old.scoring,
                    customTags = freshCustomTags ?: old.customTags,
                    lastSuccessAt = clock(),
                    lastError = null,
                    syncing = false,
                    // A completion the server has applied no longer lists the task; drop stale entries too
                    pendingCompletions = old.pendingCompletions.filter { (id, at) ->
                        fresh.tasks.any { it.id == id } && clock() - at < PENDING_TTL_MS
                    },
                )
            }
            SyncResult.Success
        } catch (e: CancellationException) {
            clearSyncing()
            throw e
        } catch (e: NotSignedInException) {
            cache.update { it.copy(syncing = false, lastError = SyncError.UNAUTHORIZED) }
            SyncResult.SignedOut
        } catch (e: DeviceLockedException) {
            // Can't even read our secrets before the first unlock; nothing else to record
            clearSyncing()
            SyncResult.Retry(SyncError.LOCKED_DEVICE)
        } catch (e: ServerException) {
            logFailure(e)
            cache.update { it.copy(syncing = false, lastError = SyncError.SERVER) }
            SyncResult.Retry(SyncError.SERVER)
        } catch (e: IOException) {
            logFailure(e)
            cache.update { it.copy(syncing = false, lastError = SyncError.NETWORK) }
            SyncResult.Retry(SyncError.NETWORK)
        } catch (e: Exception) {
            // Unparseable payload and the like
            logFailure(e)
            cache.update { it.copy(syncing = false, lastError = SyncError.SERVER) }
            SyncResult.Retry(SyncError.SERVER)
        }
    }

    private fun logFailure(e: Exception) {
        // Messages only: the payload may hold personal data
        runCatching { android.util.Log.w("LifeMappSync", "Snapshot sync failed: ${e::class.simpleName}: ${e.message}") }
    }

    companion object {
        /** A checked task stays hidden this long even if the server still lists it (slow providers, retries) */
        const val PENDING_TTL_MS = 10 * 60 * 1000L

        /**
         * Backfills topic/item tags on [Snapshot.integrity] and [Snapshot.todayDomains] from [domains]
         * (fetched via DomainsApi) when an older server's widget snapshot didn't include them.
         */
        internal fun mergeDomainTags(snapshot: Snapshot, domains: List<Integrity>): Snapshot {
            val topicTags = domains.flatMap { it.topics }.associate { it.id to it.tags }
            val itemTags = domains.flatMap { it.topics }.flatMap { it.items }.associate { it.id to it.tags }
            fun Integrity.backfill(): Integrity = copy(
                topics = topics.map { topic ->
                    topic.copy(
                        tags = topic.tags.ifEmpty { topicTags[topic.id].orEmpty() },
                        items = topic.items.map { item ->
                            item.copy(tags = item.tags.ifEmpty { itemTags[item.id].orEmpty() })
                        },
                    )
                },
            )
            return snapshot.copy(
                integrity = snapshot.integrity?.backfill(),
                todayDomains = snapshot.todayDomains.map { it.backfill() },
            )
        }

        /** Sections the server failed to load keep their previous values. */
        fun merge(old: Snapshot?, fresh: Snapshot): Snapshot {
            if (old == null) return fresh
            var merged = fresh
            val failed = fresh.failedSections.toSet()
            if (Sections.EVENTS in failed) {
                merged = merged.copy(events = old.events, counts = merged.counts.copy(events = old.counts.events))
            }
            if (Sections.TASKS in failed) {
                merged = merged.copy(tasks = old.tasks, counts = merged.counts.copy(tasks = old.counts.tasks))
            }
            if (Sections.MAIL in failed) {
                merged = merged.copy(
                    mail = old.mail,
                    counts = merged.counts.copy(mail = old.counts.mail, mailConnected = old.counts.mailConnected),
                )
            }
            if (Sections.DOMAINS in failed) merged = merged.copy(domains = old.domains)
            if (Sections.WEATHER in failed) merged = merged.copy(weather = old.weather)
            if (Sections.INTEGRITY in failed) merged = merged.copy(integrity = old.integrity)
            if (Sections.TODAY_DOMAINS in failed) merged = merged.copy(todayDomains = old.todayDomains)
            if (Sections.MONTHLY_DOMAINS in failed || (fresh.monthlyDomains.isEmpty() && old.monthlyDomains.isNotEmpty())) {
                merged = merged.copy(monthlyDomains = old.monthlyDomains)
            }
            return merged
        }
    }
}
