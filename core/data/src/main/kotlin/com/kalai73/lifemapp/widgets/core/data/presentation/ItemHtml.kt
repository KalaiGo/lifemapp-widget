package com.kalai73.lifemapp.widgets.core.data.presentation

import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned

/**
 * An item's (subsubdomain's) HTML, as the web's TipTap editor stores it, turned into editable text and back.
 * Android's spans keep bold, italic, underline, strikethrough, text color, links and bullet lists; what they can't
 * hold (numbered lists, headings, highlights, tables, code, images) is simplified once the text is changed and
 * saved, which [hasUnsupportedFormatting] warns about.
 */
object ItemHtml {
    private val unsupported = Regex("""<(ol|h[1-6]|table|mark|pre|code|img|hr)\b|background(-color)?\s*:""", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^>]*>")
    private val direction = Regex(""" dir="(ltr|rtl)"""")
    private val numericEntity = Regex("""&#(\d+);""")
    private val newlineBetweenTags = Regex(""">\s*\n\s*<""")

    fun toSpanned(html: String): Spanned = trimEnd(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT))

    fun toHtml(text: Spanned): String {
        val trimmed = trimEnd(text)
        if (trimmed.isBlank()) return ""
        return Html.toHtml(trimmed, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)
            .replace(direction, "")
            // Html.toHtml writes every non-ASCII character as an entity: keep the text readable (and searchable)
            .replace(numericEntity) { m ->
                val code = m.groupValues[1].toIntOrNull()
                if (code != null && code >= 0xA0 && Character.isValidCodePoint(code)) String(Character.toChars(code)) else m.value
            }
            .replace(newlineBetweenTags, "><")
            .trim()
    }

    /** No text, only tags and spaces: what the web treats as an item without content */
    fun isBlank(html: String?): Boolean =
        html.isNullOrBlank() || html.replace(tag, "").replace("&nbsp;", " ").isBlank()

    /** Formatting the app's editor can't keep: saving changed text simplifies it */
    fun hasUnsupportedFormatting(html: String): Boolean = unsupported.containsMatchIn(html)

    private fun trimEnd(text: Spanned): Spanned {
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        return if (end == text.length) text else SpannableStringBuilder(text, 0, end)
    }
}
