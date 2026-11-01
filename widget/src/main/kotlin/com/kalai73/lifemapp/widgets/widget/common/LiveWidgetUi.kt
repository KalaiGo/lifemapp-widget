package com.kalai73.lifemapp.widgets.widget.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.presentation.WidgetUi

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
