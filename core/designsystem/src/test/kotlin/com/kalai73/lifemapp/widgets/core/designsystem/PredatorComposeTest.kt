package com.kalai73.lifemapp.widgets.core.designsystem

import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PredatorComposeTest {

    @Test
    fun `default uiStyle is predator`() {
        assertEquals(UiStyle.PREDATOR, UiStyle.from(null))
        assertEquals(UiStyle.PREDATOR, UiStyle.from("unknown"))
        assertEquals(UiStyle.CLASSIC, UiStyle.from("classic"))
    }

    @Test
    fun `predator palette assigns warm panel and accent ink`() {
        val darkPalette = PredatorColors.palette(ColorSchemeName.DEFAULT, dark = true)
        val darkScheme = PredatorColors.scheme(darkPalette)
        assertEquals(darkPalette.panel, darkScheme.background)
        assertEquals(darkPalette.raised, darkScheme.surface)
        assertEquals(darkPalette.ink, darkScheme.primary)

        val lightPalette = PredatorColors.palette(ColorSchemeName.DEFAULT, dark = false)
        val lightScheme = PredatorColors.scheme(lightPalette)
        assertEquals(lightPalette.panel, lightScheme.background)
        assertEquals(lightPalette.raised, lightScheme.surface)
        assertEquals(lightPalette.ink, lightScheme.primary)
    }

    @Test
    fun `predator text styles use monospace and condensed font families`() {
        val caption = pdCaptionStyle()
        assertEquals(MonoFontFamily, caption.fontFamily)

        val label = pdLabelStyle()
        assertNotNull(label.fontFamily)
        assertEquals(CondensedFontFamily, label.fontFamily)

        val title = pdTitleStyle()
        assertNotNull(title.fontFamily)
        assertEquals(CondensedFontFamily, title.fontFamily)
    }

    @Test
    fun `shapes are instantiated without error`() {
        val chamfer = pdChamferShape()
        assertNotNull(chamfer)
        val para = pdParallelogramShape()
        assertNotNull(para)
        val hex = PdHexagonShape
        assertNotNull(hex)
    }
}
