package com.kalai73.lifemapp.widgets.core.data.content

import android.text.Html
import android.text.TextPaint
import android.graphics.Typeface
import android.text.style.AlignmentSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.SpannableStringBuilder
import android.text.Spanned

/** A heading paragraph, [level] 1 to 3: larger and bold */
class HeadingSpan(val level: Int) : RelativeSizeSpan(ItemHtml.HEADING_SIZES[level - 1]) {
    override fun updateDrawState(ds: TextPaint) { super.updateDrawState(ds); ds.isFakeBoldText = true }
    override fun updateMeasureState(ds: TextPaint) { super.updateMeasureState(ds); ds.isFakeBoldText = true }
}

/**
 * An item's (subsubdomain's) HTML, as the web's TipTap editor stores it, turned into editable text and back.
 * Android's spans keep bold, italic, underline, strikethrough, text color, links, bullet lists, headings
 * 1 to 3, highlights and alignment; what they can't hold (numbered lists, tables, code, images) is simplified once the text is changed and
 * saved, which [hasUnsupportedFormatting] warns about.
 */
object ItemHtml {
    private val unsupported = Regex("""<(ol|h[4-6]|table|pre|code|img|hr)\b""", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^>]*>")
    private val direction = Regex(""" dir="(ltr|rtl)"""")
    private val numericEntity = Regex("""&#(\d+);""")
    private val newlineBetweenTags = Regex(""">\s*\n\s*<""")

    /** The sizes android.text.Html gives h1, h2 and h3 */
    internal val HEADING_SIZES = floatArrayOf(1.5f, 1.4f, 1.3f)
    private const val DEFAULT_HIGHLIGHT = "#fef08a"
    /** Private-use characters marking a heading paragraph (plus its level) while Html.toHtml runs */
    private const val MARKER = 0xE000
    private val markOpen = Regex("""<mark\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val markClose = Regex("""</mark\s*>""", RegexOption.IGNORE_CASE)
    private val markColor = Regex("""(?:data-color=["']|background-color:)\s*([^"';]+)""", RegexOption.IGNORE_CASE)
    private val rgb = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+).*""")
    private val align = Regex("""text-align:\s*(left|right|start|end)\b""", RegexOption.IGNORE_CASE)
    private val spanTag = Regex("""<span\b[^>]*>|</span>""")
    private val background = Regex("""background-color:\s*(#[0-9a-fA-F]{6})""")
    private val paragraph = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)

    fun toSpanned(html: String): Spanned {
        val text = SpannableStringBuilder(trimEnd(Html.fromHtml(toAndroid(html), Html.FROM_HTML_MODE_COMPACT)))
        text.getSpans(0, text.length, RelativeSizeSpan::class.java).forEach { span ->
            val level = HEADING_SIZES.indexOfFirst { it == span.sizeChange } + 1
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (level == 0 || start >= end) return@forEach
            text.getSpans(start, end, StyleSpan::class.java)
                .firstOrNull { it.style == Typeface.BOLD && text.getSpanStart(it) == start && text.getSpanEnd(it) == end }
                ?.let(text::removeSpan)
            text.removeSpan(span)
            val (from, to) = paragraphBounds(text, start, end)
            text.setSpan(HeadingSpan(level), from, to, Spanned.SPAN_PARAGRAPH)
        }
        // Html.toHtml only writes alignments that are paragraph spans
        text.getSpans(0, text.length, AlignmentSpan::class.java).forEach { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            text.removeSpan(span)
            if (start >= end) return@forEach
            val (from, to) = paragraphBounds(text, start, end)
            text.setSpan(AlignmentSpan.Standard(span.alignment), from, to, Spanned.SPAN_PARAGRAPH)
        }
        return text
    }

    private fun paragraphBounds(text: CharSequence, start: Int, end: Int): Pair<Int, Int> {
        var from = start
        while (from > 0 && text[from - 1] != '\n') from--
        var to = end
        while (to < text.length && text[to - 1] != '\n') to++
        return from to to
    }

    /** TipTap's highlights and left / right alignment as android.text.Html reads them */
    private fun toAndroid(html: String): String = html
        .replace(markOpen) { m -> "<span style=\"background-color:${hex(markColor.find(m.groupValues[1])?.groupValues?.get(1))}\">" }
        .replace(markClose, "</span>")
        .let { swapAlign(it, mapOf("left" to "start", "right" to "end")) }

    private fun hex(color: String?): String {
        val value = color?.trim() ?: return DEFAULT_HIGHLIGHT
        rgb.matchEntire(value)?.let { m ->
            return "#" + m.groupValues.drop(1).joinToString("") { (it.toIntOrNull() ?: 0).coerceIn(0, 255).toString(16).padStart(2, '0') }
        }
        return if (Regex("#[0-9a-fA-F]{6}").matches(value)) value else DEFAULT_HIGHLIGHT
    }

    private fun swapAlign(html: String, names: Map<String, String>): String = html.replace(tag) { t ->
        t.value.replace(align) { m -> "text-align: ${names[m.groupValues[1].lowercase()] ?: m.groupValues[1]}" }
    }

    /** Puts a level marker at the end of each heading paragraph and takes the heading spans off */
    private fun markHeadings(source: Spanned): Spanned {
        val text = SpannableStringBuilder(source)
        val headings = text.getSpans(0, text.length, HeadingSpan::class.java)
        if (headings.isEmpty()) return source
        val marks = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf("\n", start).let { if (it < 0) text.length else it }
            if (end > start) headings.firstOrNull { text.getSpanStart(it) < end && text.getSpanEnd(it) > start }?.let { marks += end to it.level }
            start = end + 1
        }
        headings.forEach(text::removeSpan)
        marks.asReversed().forEach { (at, level) -> text.insert(at, (MARKER + level).toChar().toString()) }
        return text
    }

    private fun writeHeadings(html: String): String = html.replace(paragraph) { m ->
        val level = (1..3).firstOrNull { (MARKER + it).toChar() in m.groupValues[2] } ?: return@replace m.value
        "<h$level${m.groupValues[1]}>${m.groupValues[2].replace((MARKER + level).toChar().toString(), "")}</h$level>"
    }

    /** Background color spans as the highlight marks TipTap reads */
    private fun writeMarks(html: String): String {
        val open = ArrayDeque<Boolean>()
        return html.replace(spanTag) { m ->
            if (m.value == "</span>") {
                if (open.removeLastOrNull() == true) "</mark>" else m.value
            } else {
                val color = background.find(m.value)?.groupValues?.get(1)?.lowercase()
                open.addLast(color != null)
                if (color == null) m.value else "<mark data-color=\"$color\" style=\"background-color: $color; color: inherit\">"
            }
        }
    }

    fun toHtml(text: Spanned): String {
        val trimmed = trimEnd(text)
        if (trimmed.isBlank()) return ""
        return Html.toHtml(markHeadings(trimmed), Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)
            .replace(direction, "")
            // Html.toHtml writes every non-ASCII character as an entity: keep the text readable (and searchable)
            .replace(numericEntity) { m ->
                val code = m.groupValues[1].toIntOrNull()
                if (code != null && code >= 0xA0 && Character.isValidCodePoint(code)) String(Character.toChars(code)) else m.value
            }
            .replace(newlineBetweenTags, "><")
            .let(::writeHeadings)
            .let(::writeMarks)
            .let { swapAlign(it, mapOf("start" to "left", "end" to "right")) }
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
