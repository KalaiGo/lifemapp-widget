package com.kalai73.lifemapp.widgets.core.data

import android.content.Context
import com.kalai73.lifemapp.widgets.core.data.api.LifeMappApi
import com.kalai73.lifemapp.widgets.core.data.api.createHttpClient
import com.kalai73.lifemapp.widgets.core.data.auth.AuthRepository
import com.kalai73.lifemapp.widgets.core.data.auth.SupabaseAuthApi
import com.kalai73.lifemapp.widgets.core.data.cache.CacheStore
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetPresenter
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi
import com.kalai73.lifemapp.widgets.core.data.repository.EncryptionRepository
import com.kalai73.lifemapp.widgets.core.data.repository.SnapshotRepository
import com.kalai73.lifemapp.widgets.core.data.repository.TaskRepository
import com.kalai73.lifemapp.widgets.core.data.repository.VoiceRepository
import com.kalai73.lifemapp.widgets.core.data.secure.DeviceLockedException
import com.kalai73.lifemapp.widgets.core.data.secure.SecretStore
import com.kalai73.lifemapp.widgets.core.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // The whitelist check needs the API, which needs auth: resolved lazily
    val auth: AuthRepository = AuthRepository(authApi, secrets, isWhitelisted = { email -> api.isWhitelisted(email) })
    val api = LifeMappApi(http, config, auth)
    val cache = CacheStore(appContext)
    val snapshots = SnapshotRepository(api, cache)
    val tasks = TaskRepository(api, cache)
    val encryption = EncryptionRepository(api, secrets)
    val voice = VoiceRepository(api, dataKey = { encryption.dataKey() })

    /** Set by the widget module: re-renders every placed widget from the cache. */
    @Volatile var refreshWidgets: suspend () -> Unit = {}

    /** Widget state from the cache; signed out (or the device not unlocked since boot) renders the sign-in prompt. */
    suspend fun widgetUi(): WidgetUi {
        val secretValues = try {
            secrets.read()
        } catch (e: DeviceLockedException) {
            return WidgetUi.SignedOut
        }
        val authState = if (secretValues.session != null) AuthState.SIGNED_IN else AuthState.SIGNED_OUT
        return WidgetPresenter.present(cache.current(), authState, secretValues.dataKey)
    }

    private val widgetInvalidations = MutableStateFlow(0)

    /** Makes running widget sessions re-read [widgetUi]: secrets (sign-in, unlock) aren't observable on their own. */
    fun invalidateWidgets() = widgetInvalidations.update { it + 1 }

    /**
     * [widgetUi], re-emitted whenever the cache changes or [invalidateWidgets] is called. Widgets must collect
     * this inside `provideContent`: Glance keeps a session alive and `updateAll()` only recomposes it, so a
     * value read once in `provideGlance` goes stale (e.g. stuck on "Loading…" after a failed sync).
     */
    fun widgetUiFlow(): Flow<WidgetUi> =
        combine(cache.state, widgetInvalidations) { _, _ -> widgetUi() }.distinctUntilChanged()

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
