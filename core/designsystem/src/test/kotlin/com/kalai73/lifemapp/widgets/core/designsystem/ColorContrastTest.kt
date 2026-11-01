package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.ui.graphics.Color
import com.kalai73.lifemapp.widgets.core.model.ColorSchemeName
import com.kalai73.lifemapp.widgets.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {
    private val lightPanel = PredatorColors.palette(ColorSchemeName.DEFAULT, dark = false).row
    private val darkPanel = PredatorColors.palette(ColorSchemeName.DEFAULT, dark = true).row

    @Test
    fun `ratio matches WCAG for black on white`() {
        assertEquals(21f, ColorContrast.ratio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, ColorContrast.ratio(Color.White, Color.White), 0.01f)
    }

    @Test
    fun `colors that already read are left alone`() {
        val blue = Color(0xFF1D4ED8)
        assertEquals(blue, ColorContrast.ensure(blue, lightPanel))
    }

    @Test
    fun `a yellow calendar is darkened on the light panel and kept on the dark one`() {
        val yellow = Color(0xFFEAB308)
        val onLight = ColorContrast.ensure(yellow, lightPanel)
        assertTrue(ColorContrast.ratio(onLight, lightPanel) >= ColorContrast.TEXT)
        assertTrue(onLight.red < yellow.red)
        assertEquals(yellow, ColorContrast.ensure(yellow, darkPanel))
    }

    @Test
    fun `a navy list is lightened on the dark panel`() {
        val navy = Color(0xFF1E3A8A)
        val onDark = ColorContrast.ensure(navy, darkPanel, ColorContrast.GRAPHIC)
        assertTrue(ColorContrast.ratio(onDark, darkPanel) >= ColorContrast.GRAPHIC)
        assertTrue(onDark.blue > navy.blue)
    }

    @Test
    fun `accent inks and destructive read on their panels`() {
        for (scheme in ColorSchemeName.entries) for (dark in listOf(false, true)) {
            val p = PredatorColors.palette(scheme, dark)
            assertTrue("$scheme dark=$dark ink", ColorContrast.ratio(p.ink, p.panel) >= 4.4f)
            assertTrue("$scheme dark=$dark text", ColorContrast.ratio(p.text, p.panel) >= 7f)
            assertTrue("$scheme dark=$dark dim", ColorContrast.ratio(p.dim, p.panel) >= 4.5f)
            assertTrue("$scheme dark=$dark destructive", ColorContrast.ratio(p.destructive, p.panel) >= 4.5f)
        }
    }

    @Test
    fun `on-fill picks the readable text color`() {
        assertEquals(Color.White, ColorContrast.onFill(Color(0xFF1E9E52).copy(red = 0.05f, green = 0.3f, blue = 0.15f)))
        assertEquals(Color(0xFF1A1004), ColorContrast.onFill(Color(0xFFEAB308)))
    }

    @Test
    fun `theme mode resolves against the system setting`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }
}
