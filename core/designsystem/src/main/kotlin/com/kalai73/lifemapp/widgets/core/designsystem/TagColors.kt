package com.kalai73.lifemapp.widgets.core.designsystem

import androidx.compose.ui.graphics.Color
import com.kalai73.lifemapp.widgets.core.model.CustomTag

/**
 * Accent colors of context tags, matching Tailwind classes in `lifeMappWeb/frontend/src/components/domains/tag-styles.ts`.
 */
object TagColors {
    data class Style(val lightText: Color, val darkText: Color, val ring: Color, val swatch: Color) {
        fun text(dark: Boolean): Color = if (dark) darkText else lightText
    }

    val WORK = Style(
        lightText = Color(0xFF0284C7), // sky-600
        darkText = Color(0xFF38BDF8),  // sky-400
        ring = Color(0x990EA5E9),      // sky-500/60
        swatch = Color(0xFF0EA5E9),
    )

    val FAMILY = Style(
        lightText = Color(0xFFE11D48), // rose-600
        darkText = Color(0xFFFB7185),  // rose-400
        ring = Color(0x99F43F5E),      // rose-500/60
        swatch = Color(0xFFF43F5E),
    )

    private val CUSTOM: Map<String, Style> = mapOf(
        "emerald" to Style(Color(0xFF059669), Color(0xFF34D399), Color(0x9910B981), Color(0xFF10B981)),
        "amber" to Style(Color(0xFFD97706), Color(0xFFFBBF24), Color(0x99F59E0B), Color(0xFFF59E0B)),
        "violet" to Style(Color(0xFF7C3AED), Color(0xFFA78BFA), Color(0x998B5CF6), Color(0xFF8B5CF6)),
        "teal" to Style(Color(0xFF0D9488), Color(0xFF2DD4BF), Color(0x9914B8A6), Color(0xFF14B8A6)),
        "orange" to Style(Color(0xFFEA580C), Color(0xFFFB923C), Color(0x99F97316), Color(0xFFF97316)),
        "fuchsia" to Style(Color(0xFFC026D3), Color(0xFFE879F9), Color(0x99D946EF), Color(0xFFD946EF)),
        "lime" to Style(Color(0xFF65A30D), Color(0xFFA3E635), Color(0x9984CC16), Color(0xFF84CC16)),
        "indigo" to Style(Color(0xFF4F46E5), Color(0xFF818CF8), Color(0x996366F1), Color(0xFF6366F1)),
        "slate" to Style(Color(0xFF475569), Color(0xFFCBD5E1), Color(0x9964748B), Color(0xFF64748B)),
    )

    fun custom(colorName: String): Style = CUSTOM[colorName] ?: CUSTOM.getValue(CustomTag.DEFAULT_COLOR)

    fun textColor(tag: String, customTags: List<CustomTag>, dark: Boolean, primaryColor: Color): Color = when (tag) {
        "me" -> primaryColor
        "work" -> WORK.text(dark)
        "family" -> FAMILY.text(dark)
        else -> customTags.firstOrNull { it.id == tag }?.let { custom(it.color).text(dark) } ?: primaryColor
    }

    fun ringColor(tag: String, customTags: List<CustomTag>, defaultBorder: Color): Color = when (tag) {
        "me", CustomTag.TAG_ALL -> defaultBorder
        "work" -> WORK.ring
        "family" -> FAMILY.ring
        else -> customTags.firstOrNull { it.id == tag }?.let { custom(it.color).ring } ?: defaultBorder
    }

    fun icon(tag: String): Int = when (tag) {
        CustomTag.TAG_ALL -> LucideIcons.LAYERS
        "me" -> LucideIcons.USER
        "work" -> LucideIcons.BRIEFCASE
        "family" -> LucideIcons.USERS
        else -> LucideIcons.TAG
    }
}
