package com.kalai73.lifemapp.widgets.widget.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.secure.DeviceLockedException
import com.kalai73.lifemapp.widgets.core.model.AuthState
import com.kalai73.lifemapp.widgets.core.presentation.WidgetPresenter
import com.kalai73.lifemapp.widgets.core.presentation.WidgetUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Widget state from the cache; signed out (or the device not unlocked since boot) renders the sign-in prompt. */
suspend fun LifeMappGraph.widgetUi(): WidgetUi {
    val secretValues = try {
        secrets.read()
    } catch (e: DeviceLockedException) {
        return WidgetUi.SignedOut
    }
    // A session still owing the second login step can't sync: the prompt opens the app, which asks for the code
    val session = secretValues.session
    val authState = if (session != null && session.secondStepFactorId == null) AuthState.SIGNED_IN else AuthState.SIGNED_OUT
    return WidgetPresenter.present(cache.current(), authState, secretValues.dataKey)
}

/**
 * [widgetUi], re-emitted whenever the cache changes or [LifeMappGraph.invalidateWidgets] is called. Widgets must
 * collect this inside `provideContent`: Glance keeps a session alive and `updateAll()` only recomposes it, so a
 * value read once in `provideGlance` goes stale (e.g. stuck on "Loading…" after a failed sync).
 */
fun LifeMappGraph.widgetUiFlow(): Flow<WidgetUi> =
    combine(cache.state, widgetInvalidations) { _, _ -> widgetUi() }.distinctUntilChanged()

/**
 * The widget state, kept current for the whole Glance session. Call inside `provideContent` with the value
 * read in `provideGlance` as [initial], so the first frame needs no extra recomposition.
 */
@Composable
fun liveWidgetUi(graph: LifeMappGraph, initial: WidgetUi): WidgetUi {
    val flow = remember(graph) { graph.widgetUiFlow() }
    val ui by flow.collectAsState(initial)
    return ui
}
