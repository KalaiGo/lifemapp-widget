package com.kalai73.lifemapp.widgets.core.data

import android.content.Context
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.api.createHttpClient
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.SupabaseAuthApi
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.repository.EncryptionRepository
import com.kalai73.lifemapp.widgets.core.data.repository.ItemRepository
import com.kalai73.lifemapp.widgets.core.data.repository.ScoringRepository
import com.kalai73.lifemapp.widgets.core.data.repository.SnapshotRepository
import com.kalai73.lifemapp.widgets.core.data.repository.TaskRepository
import com.kalai73.lifemapp.widgets.core.data.repository.VoiceRepository
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manual dependency graph, created once per process (Application.onCreate, or lazily from a widget
 * receiver / worker when the system starts the process for one of those).
 */
class LifeMappGraph private constructor(context: Context) {
    val appContext: Context = context.applicationContext
    val config = LifeMappConfig()
    val http = createHttpClient()
    val secrets = SecretStore(appContext)
    private val authApi = SupabaseAuthApi(http, config)

    val auth: AuthRepository = AuthRepository(authApi, secrets)
    val api = LifeMappApi(http, config, auth)
    val cache = CacheStore(appContext)
    val encryption = EncryptionRepository(api.account, secrets)

    /** The signed-in user's id; null when signed out or before the first unlock after a reboot */
    private suspend fun userId(): String? = runCatching { secrets.read().session?.userId }.getOrNull()
    private suspend fun dataKey(): String? = encryption.dataKey()

    val scoring = ScoringRepository(api.scoring, cache, userId = ::userId, dataKey = ::dataKey)
    val snapshots = SnapshotRepository(
        api.snapshots,
        cache,
        scoring = { scoring.load() },
        monthlyDomains = { userId()?.let { api.domains.fetchDomains(it) } },
    )
    val tasks = TaskRepository(api.tasks, cache, userId = ::userId, dataKey = ::dataKey)
    val items = ItemRepository(api.items, cache, dataKey = ::dataKey, userId = ::userId)
    val voice = VoiceRepository(api.voice, dataKey = ::dataKey)

    /** Set by the widget module: re-renders every placed widget from the cache. */
    @Volatile var refreshWidgets: suspend () -> Unit = {}

    private val _widgetInvalidations = MutableStateFlow(0)

    /** Bumped by [invalidateWidgets]: running widget sessions re-read the secrets when it changes */
    val widgetInvalidations: StateFlow<Int> = _widgetInvalidations.asStateFlow()

    /** Makes running widget sessions re-render: secrets (sign-in, unlock) aren't observable on their own. */
    fun invalidateWidgets() = _widgetInvalidations.update { it + 1 }

    /** Signs out, wipes the cache and the data key, and re-renders the widgets as signed out. */
    suspend fun signOut() {
        auth.signOut()
        cache.clear()
        refreshWidgets()
    }

    companion object {
        @Volatile private var instance: LifeMappGraph? = null

        fun get(context: Context): LifeMappGraph =
            instance ?: synchronized(this) { instance ?: LifeMappGraph(context).also { instance = it } }
    }
}
