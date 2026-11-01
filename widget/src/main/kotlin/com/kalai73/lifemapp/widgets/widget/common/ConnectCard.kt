package com.kalai73.lifemapp.widgets.widget.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape

/**
 * A service that isn't connected yet (calendar, mail): icon and message, then a primary button that opens the
 * web settings. Predator: accent-tint chamfered card, parallelogram button (styleguide §6 Button).
 */
@Composable
fun ConnectCard(icon: Int, message: String, button: String, onClick: Action) {
    val style = LocalWidgetStyle.current
    val pd = LocalPd.current
    val content: @Composable (GlanceModifier) -> Unit = { modifier ->
        Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pd != null) PdIconWell(icon, null, width = 22.dp, iconSize = 11.dp)
                else Icon(icon, null, size = 16.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    message,
                    style = if (pd != null) captionStyle(size = 11.sp, color = pd.text) else textStyle(size = 12.sp),
                    maxLines = 2,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            val label = style.label(button)
            if (pd != null) {
                PdWrapFrame(PdShape.PARA, edge = pd.accentHi, fill = pd.accent, modifier = GlanceModifier.clickable(onClick)) { frame ->
                    Text(label, style = labelStyle(size = 11.sp, color = pd.on), modifier = frame.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            } else {
                Text(
                    label,
                    style = textStyle(size = 12.sp, weight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary),
                    modifier = GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(style.innerRadius)
                        .clickable(onClick).padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
        }
    }
    Box(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        if (pd != null) {
            PdWrapFrame(PdShape.CHAMFER_SM, edge = pd.edge, fill = pd.accentTint, modifier = GlanceModifier.fillMaxWidth()) { frame -> content(frame) }
        } else {
            content(GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(style.innerRadius))
        }
    }
}
