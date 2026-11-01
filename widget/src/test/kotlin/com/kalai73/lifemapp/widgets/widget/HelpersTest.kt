package com.kalai73.lifemapp.widgets.widget

import androidx.compose.ui.graphics.Color
import com.kalai73.lifemapp.widgets.core.designsystem.LifeMappColors
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.designsystem.R
import com.kalai73.lifemapp.widgets.core.weblauncher.WebLauncher
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler
import com.kalai73.lifemapp.widgets.core.data.api.VoiceFailure
import com.kalai73.lifemapp.widgets.core.data.api.VoiceIntent
import com.kalai73.lifemapp.widgets.core.data.api.VoiceItemResult
import com.kalai73.lifemapp.widgets.widget.voice.VoiceInputActivity
import com.kalai73.lifemapp.widgets.widget.voice.VoiceOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class HelpersTest {
    @Test
    fun `lucide names resolve to bundled drawables, with aliases and fallbacks`() {
        assertEquals("shopping-cart", LucideIcons.kebab("ShoppingCart"))
        assertEquals("share-2", LucideIcons.kebab("Share2"))
        assertEquals("check-circle-2", LucideIcons.kebab("CheckCircle2"))
        assertEquals(R.drawable.lm_ic_shopping_cart, LucideIcons.resolve("ShoppingCart"))
        assertEquals(R.drawable.lm_ic_house, LucideIcons.resolve("Home"))
        assertEquals(R.drawable.lm_ic_circle_check, LucideIcons.resolve("CheckCircle2"))
        assertEquals(R.drawable.lm_ic_music, LucideIcons.resolve("music"))
        assertNull(LucideIcons.resolve("NoSuchIcon"))
        assertNull(LucideIcons.resolve(""))
        assertTrue(LucideIcons.isEmoji("☕"))
        assertFalse(LucideIcons.isEmoji("Music"))
    }

    @Test
    fun `weather icons map visual crossing ids`() {
        assertEquals(R.drawable.lm_ic_sun, LucideIcons.weather("clear-day"))
        assertEquals(R.drawable.lm_ic_cloud_rain, LucideIcons.weather("showers-night"))
        assertEquals(R.drawable.lm_ic_cloud, LucideIcons.weather(null))
    }

    @Test
    fun `web colors parse`() {
        assertEquals(Color(0xFF0D9488), LifeMappColors.parseHex("#0d9488"))
        assertEquals(Color(0xFFFF0000), LifeMappColors.parseHex("#f00"))
        assertNull(LifeMappColors.parseHex("teal"))
        assertNull(LifeMappColors.parseHex(null))
        // The default scheme's primary, hsl(213 88% 66%)
        val primary = LifeMappColors.hsl(213f, 88f, 66f)
        assertEquals(listOf(92, 161, 245), listOf(primary.red, primary.green, primary.blue).map { kotlin.math.round(it * 255).toInt() })
    }

    @Test
    fun `next midnight is a minute after the local day ends`() {
        val zone = ZoneId.of("Europe/Madrid")
        val now = ZonedDateTime.of(2026, 10, 24, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 25, 0, 1, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, SyncScheduler.nextMidnight(now, zone))
    }

    @Test
    fun `quick link urls keep app schemes and get https otherwise`() {
        assertEquals("https://example.com", WebLauncher.normalizeUrl("example.com"))
        assertEquals("whatsapp://send?phone=1", WebLauncher.normalizeUrl("whatsapp://send?phone=1"))
        assertEquals("spotify:track:1", WebLauncher.normalizeUrl(" spotify:track:1 "))
        assertEquals("https://a.b/c", WebLauncher.normalizeUrl("https://a.b/c"))
    }

    @Test
    fun `voice input reports what was added and the most useful reason for the rest`() {
        fun ok(kind: VoiceIntent) = VoiceItemResult(true, kind)
        fun failed(kind: VoiceIntent, reason: VoiceFailure?) = VoiceItemResult(false, kind, reason)

        assertEquals(VoiceOutcome(1, 0, VoiceIntent.REMINDER, null), VoiceOutcome.of(listOf(ok(VoiceIntent.REMINDER))))
        assertEquals(VoiceOutcome(2, 0, null, null), VoiceOutcome.of(listOf(ok(VoiceIntent.TASK), ok(VoiceIntent.REMINDER))))
        assertEquals(
            VoiceOutcome(1, 2, VoiceIntent.TASK, VoiceFailure.LOCKED),
            VoiceOutcome.of(listOf(ok(VoiceIntent.TASK), failed(VoiceIntent.REMINDER, VoiceFailure.LIMIT), failed(VoiceIntent.TASK, VoiceFailure.LOCKED))),
        )
        assertEquals(VoiceOutcome(0, 1, null, VoiceFailure.FAILED), VoiceOutcome.of(listOf(failed(VoiceIntent.TASK, null))))
        assertEquals(VoiceOutcome(0, 0, null, VoiceFailure.FAILED), VoiceOutcome.of(emptyList()))
    }

    @Test
    fun `voice input listens in the LIFE MAPP language when it differs from the phone's`() {
        assertNull(VoiceInputActivity.recognizerLanguage("es", Locale.forLanguageTag("es-MX")))
        assertNull(VoiceInputActivity.recognizerLanguage(null, Locale.US))
        assertEquals("es", VoiceInputActivity.recognizerLanguage("es", Locale.US))
        assertEquals("de", VoiceInputActivity.recognizerLanguage("de-CH", Locale.US))
    }
}
