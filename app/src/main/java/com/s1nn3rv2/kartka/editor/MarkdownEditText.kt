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
import com.s1nn3rv2.kartka.markdown.Edit
import com.s1nn3rv2.kartka.markdown.Edits
import com.s1nn3rv2.kartka.markdown.StyleBuilder
import com.s1nn3rv2.kartka.markdown.UndoStack
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

    /** Called when undo or redo becomes available or unavailable. */
    var onHistoryChanged: (() -> Unit)? = null

    private val history = UndoStack()
    val canUndo get() = history.canUndo
    val canRedo get() = history.canRedo

    /** Set while the editor changes its own text (undo, redo, list continuation), so those are not re-recorded. */
    private var internalEdit = false
    private var removed: CharSequence = ""
    private var insertedNewlineAt = -1

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setHorizontallyScrolling(false)
        textSize = 16f
        setTextColor(colors.text)
        val pad = style.dp(20f).toInt()
        // Room at the bottom so the last lines can scroll above the floating formatting toolbar.
        setPadding(pad, style.dp(8f).toInt(), pad, style.dp(96f).toInt())
        // Keep our FastEditable instead of copying 75 KB on every setText.
        setEditableFactory(object : Editable.Factory() {
            override fun newEditable(source: CharSequence) = source as? FastEditable ?: FastEditable(source)
        })
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (loading) return
                version++
                removed = s.subSequence(start, start + count).toString()
            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (loading) return
                val inserted = s.subSequence(start, start + count).toString()
                if (!internalEdit) {
                    history.record(start, removed.toString(), inserted, System.currentTimeMillis())
                    insertedNewlineAt = if (inserted == "\n" && removed.isEmpty()) start + 1 else -1
                }
            }
            override fun afterTextChanged(s: Editable) {
                if (loading) return
                if (insertedNewlineAt >= 0) {
                    val at = insertedNewlineAt
                    insertedNewlineAt = -1
                    // Enter on a list item continues the list (or ends it on an empty item). Recorded like any edit.
                    Edits.continueList(s.toString(), at)?.let { post { replaceWith(it) } }
                }
                scheduleParse()
                onEdited?.invoke()
                onHistoryChanged?.invoke()
            }
        })
    }

    /** Applies a toolbar operation to the text and selection. */
    fun applyEdit(op: (Edit) -> Edit) {
        val t = text.toString()
        // A selection made backwards has its start after its end; operations expect them in order.
        val a = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val b = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
        replaceWith(op(Edit(t, a, b)))
    }

    /** Turns the text into [target] with one minimal replacement, so spans, cursor and undo stay accurate. */
    private fun replaceWith(target: Edit) {
        val t = text.toString()
        val n = target.text
        if (n != t) {
            var p = 0
            while (p < t.length && p < n.length && t[p] == n[p]) p++
            var q = 0
            while (q < t.length - p && q < n.length - p && t[t.length - 1 - q] == n[n.length - 1 - q]) q++
            editableText.replace(p, t.length - q, n, p, n.length - q)
        }
        setSelection(target.start.coerceIn(0, length()), target.end.coerceIn(0, length()))
    }

    fun undo() = history.undo()?.let(::applyHistory)
    fun redo() = history.redo()?.let(::applyHistory)

    private fun applyHistory(step: Triple<Int, Int, String>) {
        val (start, length, insert) = step
        internalEdit = true
        try {
            editableText.replace(start, (start + length).coerceAtMost(length()), insert)
            setSelection((start + insert.length).coerceAtMost(length()))
        } finally {
            internalEdit = false
        }
        onHistoryChanged?.invoke()
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
        history.clear()
        onHistoryChanged?.invoke()
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
