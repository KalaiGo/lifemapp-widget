package com.kalai73.lifemapp.widgets.widget.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.kalai73.lifemapp.widgets.core.designsystem.PdDrawables
import com.kalai73.lifemapp.widgets.core.designsystem.PdPalette
import com.kalai73.lifemapp.widgets.core.designsystem.PdShape
import com.kalai73.lifemapp.widgets.core.designsystem.PredatorColors
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName

/** Predator tokens as day/night color providers, following the device theme choice (see [WidgetColors]). */
class PdColors(scheme: ColorSchemeName, private val colors: WidgetColors) {
    private val light = PredatorColors.palette(scheme, dark = false)
    private val dark = PredatorColors.palette(scheme, dark = true)
    private fun of(pick: (PdPalette) -> Color): ColorProvider = colors.of(day = pick(light), night = pick(dark))

    val panel = of { it.panel }
    val raised = of { it.raised }
    val well = of { it.well }
    val row = of { it.row }
    val edge = of { it.edge }
    val edge2 = of { it.edge2 }
    val text = of { it.text }
    val dim = of { it.dim }
    val faint = of { it.faint }
    val accent = of { it.accent }
    val accentHi = of { it.accentHi }
    val accentTint = of { it.accentTint }
    val ink = of { it.ink }
    val on = of { it.on }
    val destructive = of { it.destructive }

    /** A user color (list, calendar, quick link) at [amount] over the panel, opaque. */
    fun tint(color: Color, amount: Float): ColorProvider =
        colors.of(day = PredatorColors.mix(light.panel, color, amount), night = PredatorColors.mix(dark.panel, color, amount))
}

/** Set by LifeMappWidgetTheme for the Predator style, null for Classic. */
val LocalPd = staticCompositionLocalOf<PdColors?> { null }

/**
 * pd-frame (styleguide §5.3): the shape in the edge color, the same shape 1dp inset in the fill color,
 * optionally accent corners in between, then the content. [modifier] sizes and positions the frame,
 * [contentModifier] pads the content.
 *
 * Only for frames with a fixed or fill size in both dimensions; content-sized frames use [PdWrapFrame].
 */
@Composable
fun PdFrame(
    shape: PdShape,
    edge: ColorProvider,
    fill: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    contentModifier: GlanceModifier = GlanceModifier,
    corners: Pair<Int, ColorProvider>? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Mask(shape.outer, edge)
        corners?.let { (res, color) -> Mask(res, color) }
        Mask(shape.inner, fill)
        Box(modifier = GlanceModifier.fillMaxSize().then(contentModifier), contentAlignment = contentAlignment) { content() }
    }
}

/**
 * The pd-frame layers alone, as children of a Box the caller sizes (fill or fixed) and whose other child is the
 * content: one view level less than [PdFrame]. Used for the widget panel.
 */
@Composable
fun PdFrameLayers(shape: PdShape, edge: ColorProvider, fill: ColorProvider, corners: Pair<Int, ColorProvider>? = null) {
    Mask(shape.outer, edge)
    corners?.let { (res, color) -> Mask(res, color) }
    Mask(shape.inner, fill)
}

/**
 * pd-frame for a frame sized by its content (wrap width and/or height): rows, banners, chips, buttons.
 *
 * [PdFrame] can't do this: its layers are `fillMaxSize` images, and Glance turns every wrap-size container
 * with a fill child into a fill one (NormalizeCompositionTree.normalizeSizes), so the frame and all its
 * ancestors would stretch to fill the widget. Background images are added after that step, so they don't.
 *
 * [content] emits the single element inside the frame and must apply the modifier it gets (the fill layer).
 * Costs one view level more than [PdFrame] counting that element: Glance wraps each background in a Box.
 */
@Composable
fun PdWrapFrame(
    shape: PdShape,
    edge: ColorProvider,
    fill: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable (GlanceModifier) -> Unit,
) {
    Box(modifier = modifier.background(ImageProvider(shape.outer), ContentScale.FillBounds, ColorFilter.tint(edge))) {
        content(GlanceModifier.background(ImageProvider(shape.inner), ContentScale.FillBounds, ColorFilter.tint(fill)))
    }
}

@Composable
private fun Mask(res: Int, color: ColorProvider) {
    Image(
        provider = ImageProvider(res),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.fillMaxSize(),
    )
}

/** Pointy-top hexagon (icon wells, scores): width × 1.1 width. */
@Composable
fun PdHex(
    width: Dp,
    edge: ColorProvider,
    fill: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    PdFrame(
        PdShape.HEX, edge, fill,
        modifier = modifier.size(width, width * 1.1f),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Hex icon well (styleguide §7): accent edge, raised fill, icon in ink. */
@Composable
fun PdIconWell(icon: Int, description: String?, width: Dp = 26.dp, iconSize: Dp = 13.dp, tint: ColorProvider? = null, edge: ColorProvider? = null) {
    val pd = LocalPd.current ?: return
    PdHex(width, edge = edge ?: pd.accent, fill = pd.raised) {
        Icon(icon, description, size = iconSize, tint = tint ?: pd.ink)
    }
}

/** A color bar leaning −12° (task list / calendar color). */
@Composable
fun PdBar(color: ColorProvider, height: Dp = 16.dp) {
    Image(
        provider = ImageProvider(PdDrawables.BAR),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.size(height * 0.4f, height),
    )
}

/** A 1px divider leaning −18°. */
@Composable
fun PdDivider(height: Dp = 20.dp) {
    val pd = LocalPd.current ?: return
    Image(
        provider = ImageProvider(PdDrawables.DIVIDER),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(pd.edge),
        modifier = GlanceModifier.size(height * 0.375f, height),
    )
}

/** pd-rule: a 56 × 2dp accent segment on a 1dp edge line, under section headers. */
@Composable
fun PdRule(modifier: GlanceModifier = GlanceModifier) {
    val pd = LocalPd.current ?: return
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(GlanceModifier.width(40.dp).height(2.dp).background(pd.accent)) {}
        Box(GlanceModifier.defaultWeight().height(1.dp).background(pd.edge)) {}
    }
}
