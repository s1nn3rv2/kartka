package com.s1nn3rv2.kartka.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.widget.EditText
import com.s1nn3rv2.kartka.markdown.Kind
import com.s1nn3rv2.kartka.markdown.MarkdownStyles
import com.s1nn3rv2.kartka.markdown.OutlineOps
import com.s1nn3rv2.kartka.markdown.StyleBuilder
import com.s1nn3rv2.kartka.markdown.StyleRange
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
 * The note editor: a native EditText whose markdown source is styled with spans to look rendered (live preview).
 * The text itself is always the exact markdown, so selection, copy and saving work on the source.
 */
@SuppressLint("ViewConstructor")
class MarkdownEditText(context: Context, colors: EditorColors) : EditText(context) {
    val style = EditorStyle(resources.displayMetrics.density, colors)

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ready = false
    private var loading = false
    private var styles: MarkdownStyles? = null
    private var version = 0
    private var parsedVersion = -1
    private var parseJob: Job? = null
    private var activeStart = -1
    private var activeEnd = -1
    private var pendingTask: TaskSpan? = null

    /** Called after every user edit. */
    var onEdited: (() -> Unit)? = null

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setHorizontallyScrolling(false)
        isVerticalScrollBarEnabled = true
        textSize = 16f
        setTextColor(colors.text)
        val pad = style.dp(20f).toInt()
        setPadding(pad, style.dp(8f).toInt(), pad, style.dp(24f).toInt())
        // Keep our SpannableStringBuilder instead of copying 75 KB on every setText.
        setEditableFactory(object : Editable.Factory() {
            override fun newEditable(source: CharSequence) = source as? Editable ?: super.newEditable(source)
        })
        addTextChangedListener(object : TextWatcher {
            // Bump the version before the text changes: selection callbacks fire mid-edit and must see the old parse as stale.
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
        ready = true
    }

    fun setColors(colors: EditorColors) {
        if (colors == style.colors) return
        style.colors = colors
        setTextColor(colors.text)
        invalidate()
    }

    /** Replaces the content. Parsing happens off the main thread before the text is shown, so it never flashes unstyled. */
    suspend fun load(markdown: String) {
        val parsed = withContext(Dispatchers.Default) { StyleBuilder.build(markdown) }
        val ssb = SpannableStringBuilder(markdown)
        activeStart = -1
        activeEnd = -1
        for (r in parsed.ranges) createSpan(r, false, style)?.let { ssb.setSpan(it, r.start, r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        loading = true
        setText(ssb, BufferType.EDITABLE)
        loading = false
        version++
        parsedVersion = version
        styles = parsed
    }

    fun headings() = styles?.headings.orEmpty()

    private fun scheduleParse() {
        parseJob?.cancel()
        parseJob = scope.launch {
            delay(80)
            val v = version
            val snapshot = text.toString()
            val parsed = withContext(Dispatchers.Default) { StyleBuilder.build(snapshot) }
            if (v != version) return@launch
            styles = parsed
            parsedVersion = v
            updateActive()
            applyDiff()
        }
    }

    /** Lines touched by the selection, widened to whole code fences and tables. Empty when not focused. */
    private fun updateActive(): Boolean {
        val s = styles
        var a = -1
        var b = -1
        if (hasFocus() && s != null && parsedVersion == version && selectionStart >= 0) {
            val t = text
            a = OutlineOps.lineStart(t, selectionStart)
            b = OutlineOps.lineEnd(t, selectionEnd)
            for (block in s.blocks) {
                if (block.first <= b && block.last + 1 >= a) {
                    a = minOf(a, block.first)
                    b = maxOf(b, block.last + 1)
                }
            }
        }
        if (a == activeStart && b == activeEnd) return false
        activeStart = a
        activeEnd = b
        return true
    }

    private fun isRevealed(r: StyleRange) = r.kind.revealable && activeStart >= 0 && r.start <= activeEnd && r.end >= activeStart

    /** Makes the spans match the latest parse: removes stale ones, adds missing ones, leaves the rest alone. */
    private fun applyDiff() {
        val s = styles ?: return
        if (parsedVersion != version) return
        val e = editableText
        val wanted = HashMap<Pair<StyleRange, Boolean>, StyleRange>(s.ranges.size * 2)
        for (r in s.ranges) {
            val revealed = isRevealed(r)
            if (revealed && r.kind != Kind.MARKER) continue
            wanted[r to revealed] = r
        }
        for (span in e.getSpans(0, e.length, MdSpan::class.java)) {
            val key = span.spec.copy(start = e.getSpanStart(span), end = e.getSpanEnd(span)) to span.revealed
            if (wanted.remove(key) == null) e.removeSpan(span)
        }
        for ((key, r) in wanted) {
            createSpan(r, key.second, style)?.let { e.setSpan(it, r.start, r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return
        unfoldAt(selStart)
        if (updateActive()) applyDiff()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (updateActive()) applyDiff()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Table rows and rules size themselves to the text width, so it must be known before layout.
        style.availableWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pendingTask = taskAt(event.x, event.y)
            MotionEvent.ACTION_UP -> pendingTask?.let { span ->
                pendingTask = null
                toggleTask(span)
                return true
            }
            MotionEvent.ACTION_CANCEL -> pendingTask = null
        }
        return if (pendingTask != null) true else super.onTouchEvent(event)
    }

    private fun taskAt(x: Float, y: Float): TaskSpan? {
        val layout = layout ?: return null
        val line = layout.getLineForVertical((y - totalPaddingTop + scrollY).toInt())
        val px = x - totalPaddingLeft + scrollX
        val e = editableText
        return e.getSpans(layout.getLineStart(line), layout.getLineEnd(line), TaskSpan::class.java).firstOrNull { span ->
            val left = layout.getPrimaryHorizontal(e.getSpanStart(span))
            val right = layout.getPrimaryHorizontal(e.getSpanEnd(span))
            px in (left - style.dp(8f))..(right + style.dp(8f))
        }
    }

    private fun toggleTask(span: TaskSpan) {
        val e = editableText
        val at = e.getSpanStart(span) + 1
        if (at <= 0 || at >= e.length) return
        e.replace(at, at + 1, if (e[at] == ' ') "x" else " ")
    }

    /** Folds the heading or bullet on the cursor line, or unfolds it if already folded. */
    fun toggleFold() {
        val e = editableText
        val lineStart = OutlineOps.lineStart(e, selectionStart)
        val lineEnd = OutlineOps.lineEnd(e, selectionStart)
        e.getSpans(lineStart, lineEnd, FoldHeaderSpan::class.java).firstOrNull()?.let { return unfold(it.fold) }
        val region = OutlineOps.foldRegion(e, selectionStart) ?: return
        val fold = Fold()
        fun put(span: Any, start: Int, end: Int) {
            fold.spans += span
            e.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        put(FoldHeaderSpan(fold, style), lineStart, lineEnd.coerceAtLeast(lineStart + 1).coerceAtMost(e.length))
        var i = region.first
        while (i <= region.last + 1) {
            val le = OutlineOps.lineEnd(e, i)
            if (le > i) put(FoldGlyphSpan(fold), i, le)
            put(FoldLineSpan(fold), i, (le + 1).coerceAtMost(e.length))
            if (le >= region.last + 1) break
            i = le + 1
        }
    }

    private fun unfold(fold: Fold) {
        val e = editableText
        fold.spans.forEach(e::removeSpan)
    }

    /** The cursor landed on a hidden line (arrow keys, search, undo): open that fold. */
    private fun unfoldAt(offset: Int) {
        val e = editableText ?: return
        for (span in e.getSpans(offset, offset, FoldLineSpan::class.java)) {
            if (e.getSpanStart(span) <= offset && offset < e.getSpanEnd(span)) unfold(span.fold)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}
