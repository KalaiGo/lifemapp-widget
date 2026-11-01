package com.kalai73.lifemapp.widgets.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.widget.EditText

/** Character marks of the web editor's toolbar that Android's Html.toHtml writes back as tags. */
enum class Mark {
    BOLD, ITALIC, UNDERLINE, STRIKE;

    fun create(): CharacterStyle = when (this) {
        BOLD -> StyleSpan(Typeface.BOLD)
        ITALIC -> StyleSpan(Typeface.ITALIC)
        UNDERLINE -> UnderlineSpan()
        STRIKE -> StrikethroughSpan()
    }

    fun matches(span: Any): Boolean = when (this) {
        BOLD -> span is StyleSpan && span.style == Typeface.BOLD
        ITALIC -> span is StyleSpan && span.style == Typeface.ITALIC
        UNDERLINE -> span is UnderlineSpan
        STRIKE -> span is StrikethroughSpan
    }
}

/**
 * An EditText that behaves like the web's TipTap editor for what an item's HTML can hold: marks toggle on the
 * selection, or, with just a cursor, on what is typed next; bullet lists toggle per paragraph; text color, links and
 * clear formatting apply to the selection; undo / redo step through the edits. [onStateChange] fires whenever the
 * toolbar's state (active marks, undo / redo) may have changed.
 */
@SuppressLint("AppCompatCustomView")
class RichEditText(context: Context) : EditText(context) {
    var onStateChange: () -> Unit = {}
    /** Set once the user changed the text or its formatting */
    var changed = false
        private set

    private data class Snapshot(val text: CharSequence, val selStart: Int, val selEnd: Int)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var lastTyping = 0L
    private var restoring = false
    /** Marks switched on with just a cursor, applied to the next typed text */
    private val pending = mutableSetOf<Mark>()
    private var pendingAt = -1
    private var inserted: IntRange? = null

    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (restoring) return
                // Typing in one go is one undo step, as in TipTap
                val now = SystemClock.uptimeMillis()
                if (now - lastTyping > TYPING_GROUP_MS) record()
                lastTyping = now
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                inserted = if (count > 0) start until start + count else null
            }

            override fun afterTextChanged(s: Editable) {
                if (restoring) return
                val range = inserted
                if (range != null && pending.isNotEmpty() && range.first == pendingAt) {
                    pending.forEach { s.setSpan(it.create(), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_INCLUSIVE) }
                    pending.clear()
                }
                changed = true
                onStateChange()
            }
        })
    }

    /**
     * Puts the item's text in without it counting as an edit or an undo step, showing its beginning: the cursor at
     * the start and the view scrolled to the top, also after the first layout and the first focus (Android places
     * the cursor and scrolls to it then, which otherwise can leave the end of a long text in view).
     */
    fun load(text: CharSequence) {
        restoring = true
        setText(text)
        restoring = false
        undoStack.clear(); redoStack.clear(); changed = false
        showStart()
        startPending = true
        addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(v: android.view.View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                if (b - t <= 0) return
                removeOnLayoutChangeListener(this)
                showStart()
                post { showStart() }
            }
        })
    }

    /** Set by [load] until the first focus, which shows the start again */
    private var startPending = false

    private fun showStart() {
        if (length() > 0) setSelection(0)
        scrollTo(0, 0)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        @Suppress("SENSELESS_COMPARISON") // also called while the super constructor runs
        if (focused && pending != null && startPending) {
            startPending = false
            showStart()
            post { showStart() }
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // Also called from the super constructor, before this class's fields are set
        @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL")
        if (pending == null) return
        if (selStart != pendingAt || selEnd != pendingAt) pending.clear()
        onStateChange()
    }

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun undo() = step(from = undoStack, to = redoStack)
    fun redo() = step(from = redoStack, to = undoStack)

    private fun step(from: ArrayDeque<Snapshot>, to: ArrayDeque<Snapshot>) {
        val target = from.removeLastOrNull() ?: return
        to.addLast(current())
        restoring = true
        setText(target.text)
        setSelection(target.selStart.coerceIn(0, length()), target.selEnd.coerceIn(0, length()))
        restoring = false
        lastTyping = 0
        changed = true
        onStateChange()
    }

    private fun current() = Snapshot(SpannableStringBuilder(text), selectionStart, selectionEnd)

    private fun record() {
        undoStack.addLast(current())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    /** A formatting change: an undo step of its own, and an edit */
    private inline fun edit(block: () -> Unit) {
        record()
        lastTyping = 0
        block()
        changed = true
        onStateChange()
    }

    private val selStart get() = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
    private val selEnd get() = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)

    fun isActive(mark: Mark): Boolean {
        if (selStart == selEnd && mark in pending) return true
        return covered(selStart, selEnd, mark::matches)
    }

    fun toggle(mark: Mark) {
        val start = selStart
        val end = selEnd
        if (start == end) {
            // With just a cursor, the mark applies to (or stops for) what is typed next
            if (mark in pending) pending.remove(mark)
            else if (covered(start, end, mark::matches)) splitAt(start, mark)
            else { pending += mark; pendingAt = start }
            onStateChange()
            return
        }
        edit {
            if (covered(start, end, mark::matches)) clear(start, end, mark::matches)
            else text.setSpan(mark.create(), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
    }

    /** [color] as 0xAARRGGBB, or null for the default color */
    fun setColor(color: Int?) {
        if (selStart == selEnd) return
        edit {
            val start = selStart
            val end = selEnd
            clear(start, end) { it is ForegroundColorSpan }
            if (color != null) text.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
    }

    val hasSelection get() = selStart != selEnd

    /** Links the selection to [url]; with just a cursor, inserts the address itself as the link. */
    fun setLink(url: String, start: Int = selStart, end: Int = selEnd) {
        edit {
            val from = start.coerceIn(0, length())
            var to = end.coerceIn(0, length())
            if (from == to) {
                text.insert(from, url)
                to = from + url.length
            }
            clear(from, to) { it is URLSpan }
            text.setSpan(URLSpan(url), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSelection(to)
        }
    }

    fun isBulletList(): Boolean = paragraphs().all { (s, e) -> text.getSpans(s, e, BulletSpan::class.java).any { text.getSpanStart(it) <= s } }

    /** Bullets the selected paragraphs, or un-bullets them when they all have one. */
    fun toggleBulletList() {
        edit {
            val all = isBulletList()
            paragraphs().forEach { (s, e) ->
                text.getSpans(s, e, BulletSpan::class.java).forEach(text::removeSpan)
                if (!all) text.setSpan(BulletSpan(BULLET_GAP), s, e, Spanned.SPAN_PARAGRAPH)
            }
        }
    }

    /** As the web's clear formatting: every mark, color and link in the selection, and bullets of its paragraphs. */
    fun clearFormatting() {
        edit {
            val start = selStart
            val end = selEnd
            if (start != end) clear(start, end) { true }
            paragraphs().forEach { (s, e) -> text.getSpans(s, e, BulletSpan::class.java).forEach(text::removeSpan) }
            pending.clear()
        }
    }

    /** Start and end (after its newline) of each paragraph the selection touches */
    private fun paragraphs(): List<Pair<Int, Int>> {
        val t = text
        var s = selStart
        while (s > 0 && t[s - 1] != '\n') s--
        val result = mutableListOf<Pair<Int, Int>>()
        do {
            var e = s
            while (e < t.length && t[e] != '\n') e++
            if (e < t.length) e++ // the paragraph span takes its newline
            result += s to e
            s = e
        } while (s < selEnd && s < t.length)
        return result
    }

    /** Every character of [start]..[end] (or, for a cursor, the character before it) has a matching span. */
    private fun covered(start: Int, end: Int, match: (Any) -> Boolean): Boolean {
        val t = text
        if (start == end) {
            if (start == 0) return false
            return t.getSpans(start - 1, start, CharacterStyle::class.java).any { match(it) && t.getSpanEnd(it) >= start }
        }
        val spans = t.getSpans(start, end, CharacterStyle::class.java).filter(match)
        return (start until end).all { i -> t[i] == '\n' || spans.any { t.getSpanStart(it) <= i && t.getSpanEnd(it) > i } }
    }

    /** Removes matching spans from [start]..[end], keeping them on the parts outside it. */
    private fun clear(start: Int, end: Int, match: (Any) -> Boolean) {
        val t = text
        t.getSpans(start, end, CharacterStyle::class.java).filter(match).forEach { span ->
            val s = t.getSpanStart(span)
            val e = t.getSpanEnd(span)
            val flags = t.getSpanFlags(span)
            t.removeSpan(span)
            if (s < start) t.setSpan(span, s, start, flags)
            if (e > end) copyOf(span)?.let { t.setSpan(it, end, e, flags) }
        }
    }

    /** A new span like [span], for the part of a split span after the cleared range (one span can't cover two) */
    private fun copyOf(span: CharacterStyle): CharacterStyle? = when (span) {
        is StyleSpan -> StyleSpan(span.style)
        is UnderlineSpan -> UnderlineSpan()
        is StrikethroughSpan -> StrikethroughSpan()
        is ForegroundColorSpan -> ForegroundColorSpan(span.foregroundColor)
        is URLSpan -> URLSpan(span.url)
        is RelativeSizeSpan -> RelativeSizeSpan(span.sizeChange)
        is BackgroundColorSpan -> BackgroundColorSpan(span.backgroundColor)
        is SubscriptSpan -> SubscriptSpan()
        is SuperscriptSpan -> SuperscriptSpan()
        is TypefaceSpan -> TypefaceSpan(span.family)
        else -> null
    }

    /** Ends [mark] at the cursor, so typing there goes on without it */
    private fun splitAt(pos: Int, mark: Mark) {
        val t = text
        t.getSpans(pos - 1, pos, CharacterStyle::class.java).filter(mark::matches).forEach { span ->
            val s = t.getSpanStart(span)
            val e = t.getSpanEnd(span)
            t.removeSpan(span)
            t.setSpan(mark.create(), s, pos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (e > pos) t.setSpan(mark.create(), pos, e, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
    }

    private companion object {
        const val TYPING_GROUP_MS = 800L
        const val MAX_HISTORY = 100
        const val BULLET_GAP = 24
    }
}
