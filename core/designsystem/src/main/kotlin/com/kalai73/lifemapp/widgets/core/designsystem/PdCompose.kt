package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Top-left and bottom-right corners chamfered at 45° (styleguide §5.2).
 * Polygon: (c, 0) (w, 0) (w, h - c) (w - c, h) (0, h) (0, c)
 */
fun pdChamferShape(cut: Dp = 12.dp): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Parallelogram leaning by [slant] (styleguide §5.2).
 * Polygon: (s, 0) (w, 0) (w - s, h) (0, h)
 */
fun pdParallelogramShape(slant: Dp = 8.dp): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { slant.toPx() }.coerceAtMost(size.width / 4f)
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - s, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Pointy-top hexagon (styleguide §5.2).
 * Polygon: (w/2, 0) (w, h/4) (w, 3h/4) (w/2, h) (0, 3h/4) (0, h/4)
 */
val PdHexagonShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h / 4f)
            lineTo(w, 3f * h / 4f)
            lineTo(w / 2f, h)
            lineTo(0f, 3f * h / 4f)
            lineTo(0f, h / 4f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Accent corner lines on the cut diagonals of a chamfer (styleguide §5.2).
 */
fun Modifier.pdChamferCorners(
    cut: Dp = 12.dp,
    accentColor: Color,
    strokeWidth: Dp = 2.dp,
    cornerExtension: Dp = 6.dp,
): Modifier = drawBehind {
    val c = cut.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
    val ext = cornerExtension.toPx()
    val sw = strokeWidth.toPx()
    val halfSw = sw / 2f

    // Top-left corner diagonal and adjoining arms
    val pathTl = Path().apply {
        moveTo(halfSw, c + ext)
        lineTo(halfSw, c)
        lineTo(c, halfSw)
        lineTo(c + ext, halfSw)
    }
    drawPath(pathTl, color = accentColor, style = Stroke(width = sw))

    // Bottom-right corner diagonal and adjoining arms
    val pathBr = Path().apply {
        moveTo(size.width - halfSw, size.height - c - ext)
        lineTo(size.width - halfSw, size.height - c)
        lineTo(size.width - c, size.height - halfSw)
        lineTo(size.width - c - ext, size.height - halfSw)
    }
    drawPath(pathBr, color = accentColor, style = Stroke(width = sw))
}

/**
 * Predator panel styling: chamfered, raised fill, 1dp edge, accent corners.
 */
fun Modifier.pdPanel(
    palette: PdPalette,
    cut: Dp = 12.dp,
): Modifier {
    val shape = pdChamferShape(cut)
    return this
        .clip(shape)
        .background(palette.raised, shape)
        .border(1.dp, palette.edge, shape)
        .pdChamferCorners(cut = cut, accentColor = palette.accent, strokeWidth = 2.dp, cornerExtension = 6.dp)
}

/** Predator display face, same as the web's `--font-predator`: Chakra Petch (bundled, OFL). */
val CondensedFontFamily: FontFamily = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.chakra_petch_semi_bold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_semi_bold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
    Font(R.font.chakra_petch_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

/** Data/numeral face, same as the web's `--font-mono`: JetBrains Mono (bundled, OFL). */
val MonoFontFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semi_bold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_extra_bold, FontWeight.ExtraBold),
)

/** pd-label: short fixed labels (buttons, tabs, headers). Condensed bold italic uppercase with tracking. */
fun pdLabelStyle(
    size: TextUnit = 13.sp,
    color: Color = Color.Unspecified,
    weight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = 0.12.em,
): TextStyle = TextStyle(
    fontFamily = CondensedFontFamily,
    fontWeight = weight,
    fontStyle = FontStyle.Italic,
    fontSize = size,
    letterSpacing = letterSpacing,
    color = color,
)

/** Data captions (dates, times, status info). Monospace uppercase with tracking. */
fun pdCaptionStyle(
    size: TextUnit = 11.sp,
    color: Color = Color.Unspecified,
    letterSpacing: TextUnit = 0.14.em,
): TextStyle = TextStyle(
    fontFamily = MonoFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    letterSpacing = letterSpacing,
    color = color,
)

/** Screen titles: condensed bold uppercase with tracking. */
fun pdTitleStyle(
    size: TextUnit = 20.sp,
    color: Color = Color.Unspecified,
    weight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = 0.08.em,
): TextStyle = TextStyle(
    fontFamily = CondensedFontFamily,
    fontWeight = weight,
    fontStyle = FontStyle.Italic,
    fontSize = size,
    letterSpacing = letterSpacing,
    color = color,
)

/** Primary button: parallelogram shape, accent gradient fill, pd-label text. */
@Composable
fun PdButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    slant: Dp = 8.dp,
    palette: PdPalette = LocalPdPalette.current,
    content: @Composable () -> Unit,
) {
    val shape = remember(slant) { pdParallelogramShape(slant) }
    val brush = remember(palette) {
        Brush.horizontalGradient(listOf(palette.accentHi, palette.accent, palette.accentLo))
    }
    val bgModifier = if (enabled && !busy) {
        Modifier.background(brush, shape)
    } else {
        Modifier.background(palette.accent.copy(alpha = 0.4f), shape)
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
            .clip(shape)
            .then(bgModifier)
            .clickable(
                enabled = enabled && !busy,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = palette.on,
                strokeWidth = 2.dp,
            )
        } else {
            CompositionLocalProvider(
                LocalContentColor provides palette.on,
                LocalTextStyle provides pdLabelStyle(size = 14.sp, color = palette.on),
            ) {
                content()
            }
        }
    }
}

/** Secondary / Outlined button: parallelogram shape, framed raised fill, 1dp edge, pd-label text. */
@Composable
fun PdOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    slant: Dp = 8.dp,
    palette: PdPalette = LocalPdPalette.current,
    content: @Composable () -> Unit,
) {
    val shape = remember(slant) { pdParallelogramShape(slant) }
    val borderColor = if (enabled) palette.edge2 else palette.edge.copy(alpha = 0.5f)
    val textColor = if (enabled) palette.text else palette.dim

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
            .clip(shape)
            .background(palette.raised, shape)
            .border(1.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides textColor,
            LocalTextStyle provides pdLabelStyle(size = 14.sp, color = textColor),
        ) {
            content()
        }
    }
}

/** Destructive button: parallelogram shape, tinted fill, destructive border, destructive pd-label text. */
@Composable
fun PdDestructiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    palette: PdPalette = LocalPdPalette.current,
    content: @Composable () -> Unit,
) {
    val shape = remember { pdParallelogramShape(8.dp) }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
            .clip(shape)
            .background(palette.destructive.copy(alpha = 0.12f), shape)
            .border(1.dp, palette.destructive, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides palette.destructive,
            LocalTextStyle provides pdLabelStyle(size = 14.sp, color = palette.destructive),
        ) {
            content()
        }
    }
}

/** Text button: pd-label text in pd.ink with hover/focus state. */
@Composable
fun PdTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    palette: PdPalette = LocalPdPalette.current,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (enabled) palette.ink else palette.dim
        CompositionLocalProvider(
            LocalContentColor provides color,
            LocalTextStyle provides pdLabelStyle(size = 13.sp, color = color),
        ) {
            content()
        }
    }
}

/** Hex icon well: pointy-top hexagon, raised fill, accent edge, ink icon (styleguide §7). */
@Composable
fun PdHexIconWell(
    iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    palette: PdPalette = LocalPdPalette.current,
) {
    val shape = PdHexagonShape
    Box(
        modifier = modifier
            .size(size, size * 1.1f)
            .clip(shape)
            .background(palette.raised, shape)
            .border(1.5.dp, palette.accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = palette.ink,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** Section header: pd-label uppercase italic title with the 48dp × 2dp accent rule (styleguide §5.3). */
@Composable
fun PdSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    palette: PdPalette = LocalPdPalette.current,
) {
    Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            style = pdLabelStyle(size = 14.sp, color = palette.ink, letterSpacing = 0.14.em),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(48.dp)
                .height(2.dp)
                .background(palette.accent)
        )
    }
}

/** Parallelogram filter chip for options/tag selection. */
@Composable
fun PdFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    palette: PdPalette = LocalPdPalette.current,
) {
    val shape = remember { pdParallelogramShape(6.dp) }
    val bg = if (selected) palette.accentTint else palette.raised
    val border = if (selected) palette.accent else palette.edge
    val textCol = if (selected) palette.ink else palette.dim

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = pdLabelStyle(size = 12.sp, color = textCol, letterSpacing = 0.1.em),
        )
    }
}
