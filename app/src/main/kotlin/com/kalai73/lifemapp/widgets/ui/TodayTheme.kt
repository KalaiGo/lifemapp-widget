package com.kalai73.lifemapp.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LocalPdPalette
import com.kalai73.lifemapp.widgets.core.designsystem.LocalUiStyle
import com.kalai73.lifemapp.widgets.core.designsystem.PdHexagonShape
import com.kalai73.lifemapp.widgets.core.designsystem.PdPalette
import com.kalai73.lifemapp.widgets.core.designsystem.PredatorColors
import com.kalai73.lifemapp.widgets.core.designsystem.pdCaptionStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdChamferShape
import com.kalai73.lifemapp.widgets.core.designsystem.pdLabelStyle
import com.kalai73.lifemapp.widgets.core.designsystem.pdParallelogramShape
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

// ── Style tokens ────────────────────────────────────────────────────────────

/** The widgets' tokens for the current UI style: Predator palette, or the Material scheme for Classic. */
internal class Tok(val predator: Boolean, val pd: PdPalette, val dark: Boolean, private val cs: androidx.compose.material3.ColorScheme) {
    val page: Color = if (predator) pd.panel else cs.background
    val ink: Color = if (predator) pd.ink else cs.primary
    val text: Color = if (predator) pd.text else cs.onBackground
    val dim: Color = if (predator) pd.dim else cs.onSurfaceVariant
    val faint: Color = if (predator) pd.faint else cs.outline
    val line: Color = if (predator) pd.edge else cs.outlineVariant
    val accent: Color = if (predator) pd.accent else cs.primary
    val onAccent: Color = if (predator) pd.on else cs.onPrimary
    val card: Color = if (predator) pd.raised else cs.surface
    val row: Color = if (predator) pd.row else Color.Transparent
    val well: Color = if (predator) pd.well else cs.surfaceVariant
    val destructive: Color = if (predator) pd.destructive else cs.error
    val tint: Color = if (predator) pd.accentTint else cs.secondaryContainer
    val onTint: Color = if (predator) pd.ink else cs.primary

    fun s(text: String) = if (predator) text.uppercase() else text

    /** Short fixed labels: condensed bold italic caps (Predator), bold (Classic). */
    fun label(size: TextUnit, color: Color = ink): TextStyle =
        if (predator) pdLabelStyle(size = size, color = color) else TextStyle(fontSize = size, fontWeight = FontWeight.Bold, color = color)

    /** Data captions: monospace caps (Predator). */
    fun caption(size: TextUnit, color: Color = dim): TextStyle =
        if (predator) pdCaptionStyle(size = size, color = color) else TextStyle(fontSize = (size.value + 1f).sp, color = color)

    /** Hero captions ("HELLO,", "TODAY IS", "AND YOU HAVE:"): monospace caps (Predator), bold (Classic), as on the widget. */
    fun heroCaption(size: TextUnit, color: Color = ink): TextStyle =
        if (predator) pdCaptionStyle(size = size, color = color) else TextStyle(fontSize = (size.value + 1f).sp, fontWeight = FontWeight.Bold, color = color)

    /** User content: default face in both styles. */
    fun body(size: TextUnit, color: Color = text, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = size, fontWeight = weight, color = color)

    fun shape(cut: Dp = 6.dp): Shape = if (predator) pdChamferShape(cut) else RoundedCornerShape(if (cut > 8.dp) 20.dp else 12.dp)

    /** A user color at [amount] over the page, opaque (the widgets' pd.tint). */
    fun tinted(color: Color, amount: Float): Color = PredatorColors.mix(page, color, amount)

    fun priority(p: Int): Color? = LifeMappColors.priority(p, dark)
}

internal val LocalTok = compositionLocalOf<Tok> { error("Tok") }

@Composable
internal fun rememberTokens(): Tok {
    val predator = LocalUiStyle.current == UiStyle.PREDATOR
    val pd = LocalPdPalette.current
    return Tok(predator, pd, pd.isDark, MaterialTheme.colorScheme)
}

/** The widgets' pd-frame: shape filled with [fill], 1dp [edge]. */
internal fun Modifier.framed(t: Tok, fill: Color, edge: Color = fill, cut: Dp = 6.dp, shape: Shape = t.shape(cut)): Modifier {
    return clip(shape).background(fill, shape).border(1.dp, edge, shape)
}

/** L-shaped brackets in the four corners plus short ticks at the middle of the top and bottom edges. */
internal fun Modifier.bracketCorners(color: Color, arm: Dp = 9.dp, stroke: Dp = 1.5.dp, tick: Dp = 5.dp): Modifier = drawBehind {
    val a = arm.toPx(); val s = stroke.toPx(); val h = s / 2f; val k = tick.toPx()
    val w = size.width; val ht = size.height
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = s)
    // corners
    line(0f, h, a, h); line(h, 0f, h, a)
    line(w, h, w - a, h); line(w - h, 0f, w - h, a)
    line(0f, ht - h, a, ht - h); line(h, ht, h, ht - a)
    line(w, ht - h, w - a, ht - h); line(w - h, ht, w - h, ht - a)
    // center ticks
    line(w / 2f, 0f, w / 2f, k)
    line(w / 2f, ht, w / 2f, ht - k)
}

@Composable
internal fun Hex(size: Dp, edge: Color, fill: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(size, size * 1.1f).clip(PdHexagonShape).background(fill, PdHexagonShape).border(1.5.dp, edge, PdHexagonShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Numbered marker: hex with accent edge (Predator), tinted circle (Classic). */
@Composable
internal fun NumberBadge(n: Int, size: Dp = 22.dp, faded: Boolean = false) {
    val t = LocalTok.current
    if (t.predator) {
        Hex(size, edge = if (faded) t.pd.edge2 else t.accent, fill = t.pd.panel) {
            Text("$n", style = t.label(9.sp, if (faded) t.faint else t.ink))
        }
    } else {
        Box(Modifier.size(size - 4.dp).clip(CircleShape).background(t.tint), contentAlignment = Alignment.Center) {
            Text("$n", style = t.body(10.sp, if (faded) t.dim else t.onTint, FontWeight.Bold))
        }
    }
}

/** The color bar leaning like the widgets' (task list / calendar color). */
@Composable
internal fun Bar(color: Color, height: Dp = 26.dp) {
    val t = LocalTok.current
    val shape: Shape = if (t.predator) pdParallelogramShape(2.dp) else RoundedCornerShape(2.dp)
    Box(Modifier.size(if (t.predator) 8.dp else 4.dp, height).clip(shape).background(color, shape))
}

@Composable
internal fun Ic(res: Int, size: Dp = 14.dp, tint: Color = LocalTok.current.ink, desc: String? = null) {
    Icon(painterResource(res), desc, Modifier.size(size), tint = tint)
}
