package com.s1nn3rv2.kartka.editor

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import com.s1nn3rv2.kartka.markdown.StyleBuilder
import com.s1nn3rv2.kartka.markdown.StyleRange
import com.s1nn3rv2.kartka.markdown.changedWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edit mode: the raw markdown in a native EditText, with syntax colors. It grows to its full height; the screen
 * wraps it in a ScrollView, which gives normal momentum scrolling.
 */
@SuppressLint("ViewConstructor")
class MarkdownEditText(context: Context, colors: EditorColors) : EditText(context) {
    val style = EditorStyle(resources.displayMetrics.density, colors)

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loading = false
    private var ranges: List<StyleRange> = emptyList()
    private var version = 0
    private var parsedVersion = -1
    private var appliedLength = 0
    private var parseJob: Job? = null

    /** Called after every user edit. */
    var onEdited: (() -> Unit)? = null

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setHorizontallyScrolling(false)
        textSize = 16f
        setTextColor(colors.text)
        val pad = style.dp(20f).toInt()
        setPadding(pad, style.dp(8f).toInt(), pad, style.dp(24f).toInt())
        // Keep our FastEditable instead of copying 75 KB on every setText.
        setEditableFactory(object : Editable.Factory() {
            override fun newEditable(source: CharSequence) = source as? FastEditable ?: FastEditable(source)
        })
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!loading) version++
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (loading) return
                scheduleParse()
                onEdited?.invoke()
            }
        })
    }

    fun setColors(colors: EditorColors) {
        if (colors == style.colors) return
        style.colors = colors
        setTextColor(colors.text)
        invalidate()
    }

    /** Replaces the content, parsed off the main thread so it never shows uncolored. */
    suspend fun load(markdown: String) {
        val parsed = withContext(Dispatchers.Default) { StyleBuilder.build(markdown) }
        // Spans go into a SpannableString first: adding thousands one by one to a SpannableStringBuilder re-sorts
        // its whole span list each time, while copying them in sorts once.
        val spannable = SpannableString(markdown)
        for (r in parsed) spannable.setSpan(createSpan(r, style), r.start, r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        for (level in 1..TAB_STOPS) spannable.setSpan(DocTabStop(level, style), 0, markdown.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        loading = true
        setText(FastEditable(spannable), BufferType.EDITABLE)
        loading = false
        version++
        parsedVersion = version
        appliedLength = markdown.length
        ranges = parsed
    }

    private fun scheduleParse() {
        parseJob?.cancel()
        parseJob = scope.launch {
            delay(80)
            val v = version
            val snapshot = text.toString()
            val previous = ranges
            val delta = snapshot.length - appliedLength
            val (parsed, window) = withContext(Dispatchers.Default) {
                StyleBuilder.build(snapshot).let { it to changedWindow(previous, it, delta) }
            }
            if (v != version) return@launch
            ranges = parsed
            parsedVersion = v
            appliedLength = snapshot.length
            window?.let { applyDiff(it.first, it.last + 1) }
        }
    }

    /**
     * Makes the spans overlapping [from, to) match the latest parse: removes stale ones, adds missing ones, leaves the
     * rest alone. Overlap follows SpannableStringBuilder.getSpans: touching at an edge does not count.
     */
    private fun applyDiff(from: Int, to: Int) {
        if (parsedVersion != version) return
        val e = editableText
        val a = from.coerceIn(0, e.length)
        val b = to.coerceIn(a, e.length)
        val wanted = HashSet<StyleRange>()
        for (r in ranges) {
            if (r.start > b) break // ranges are sorted by start
            if (overlaps(r.start, r.end, a, b)) wanted += r
        }
        for (span in e.getSpans(a, b, MdSpan::class.java)) {
            if (!wanted.remove(span.spec.copy(start = e.getSpanStart(span), end = e.getSpanEnd(span)))) e.removeSpan(span)
        }
        for (r in wanted) e.setSpan(createSpan(r, style), r.start, r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun overlaps(start: Int, end: Int, a: Int, b: Int) =
        start <= b && end >= a && (start == end || a == b || (start != b && end != a))

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}
