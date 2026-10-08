package com.s1nn3rv2.kartka.editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TabStopSpan
import com.s1nn3rv2.kartka.markdown.Kind
import com.s1nn3rv2.kartka.markdown.StyleRange
import com.s1nn3rv2.kartka.markdown.splitTableRow
import com.s1nn3rv2.kartka.markdown.stripInline

/** Colors pulled from the Material theme; spans read them at draw time so theme changes need no re-parse. */
data class EditorColors(
    val text: Int,
    val dim: Int,
    val primary: Int,
    val onPrimary: Int,
    val outline: Int,
    val outlineVariant: Int,
    val codeBackground: Int,
    val quoteBackground: Int,
    val inlineCodeBackground: Int,
    val tableHeaderBackground: Int,
)

/** Shared, mutable drawing context for all spans of one editor. */
class EditorStyle(val density: Float, var colors: EditorColors, val layout: () -> Layout?) {
    var baseTextSize = 16f * density
    var availableWidth = 0
    val tabWidth get() = baseTextSize * 1.4f
    fun dp(v: Float) = v * density

    /** Horizontal position of [offset] in the editor's layout, for spans that draw over their own characters. */
    fun x(offset: Int) = layout()?.getPrimaryHorizontal(offset)
}

/*
 * Performance rule: avoid ReplacementSpan for anything common. Android redraws every text block that contains one on
 * every frame (about 70 ms per keystroke in a 75 KB note instead of 2 ms). Hidden text is shrunk instead, and bullets
 * and checkboxes are drawn behind their own invisible characters.
 */
private const val BULLET_SCALE = 1.8f
private const val TASK_SCALE = 1.3f

/** Spans whose moving with the text needs no re-layout; [FastEditable] skips their shift notifications while editing. */
interface PassiveSpan

/** A span created from a parse result. The editor diffs these against each new parse. */
interface MdSpan : PassiveSpan {
    val spec: StyleRange
    val revealed: Boolean
}

/** Returns null when the range should show as plain text (revealed tables and rules). */
fun createSpan(r: StyleRange, revealed: Boolean, style: EditorStyle): MdSpan? = when (r.kind) {
    Kind.MARKER -> if (revealed) DimSpan(r, style) else HiddenSpan(r)
    Kind.HR -> if (revealed) null else HrSpan(r, style)
    Kind.TABLE_ROW -> if (revealed) null else TableRowSpan(r, style)
    Kind.TABLE_DELIMITER -> if (revealed) null else CollapsedLineSpan(r)
    Kind.HEADING -> HeadingSpan(r, style)
    Kind.STRONG -> BoldSpan(r)
    Kind.EMPH -> ItalicSpan(r)
    Kind.STRIKE -> StrikeSpan(r)
    Kind.INLINE_CODE -> InlineCodeSpan(r, style)
    Kind.LINK, Kind.IMAGE -> LinkSpan(r, style)
    Kind.CODE_BLOCK -> CodeBlockSpan(r, style)
    Kind.CODE_INFO -> CodeInfoSpan(r, style)
    Kind.QUOTE -> QuoteSpan(r, style)
    Kind.BULLET -> BulletSpan(r, style)
    Kind.ORDERED_MARKER -> OrderedMarkerSpan(r, style)
    Kind.TASK -> TaskSpan(r, style)
    Kind.TASK_DONE -> TaskDoneSpan(r, style)
    Kind.LIST_ITEM -> ListItemSpan(r, style)
}

/** Hanging indent: wrapped lines of a list item line up with the text after the bullet. */
class ListItemSpan(override val spec: StyleRange, private val style: EditorStyle) : LeadingMarginSpan, MdSpan {
    override val revealed get() = false
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var cachedSize = -1f
    private var margin = 0

    /** Width of the prefix (tabs, bullet, checkbox) as the spans above draw it. */
    override fun getLeadingMargin(first: Boolean): Int {
        if (first) return 0
        if (cachedSize != style.baseTextSize) {
            cachedSize = style.baseTextSize
            paint.textSize = style.baseTextSize
            val prefix = spec.data.orEmpty()
            val markerAt = prefix.indexOfFirst { it != ' ' && it != '\t' }
            val taskAt = prefix.indexOf('[', markerAt + 1)
            var x = 0f
            prefix.forEachIndexed { i, c ->
                x = when {
                    c == '\t' -> (kotlin.math.floor(x / style.tabWidth) + 1) * style.tabWidth
                    i == markerAt && c in "-*+" -> x + paint.measureText(c.toString()) * BULLET_SCALE
                    taskAt >= 0 && i in taskAt..taskAt + 2 -> x + paint.measureText(c.toString()) * TASK_SCALE
                    else -> x + paint.measureText(c.toString())
                }
            }
            margin = x.toInt()
        }
        return margin
    }
    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) = Unit
}

/** Shrinks text to nothing: practically zero width, no height, invisible. */
private fun hide(tp: TextPaint) {
    tp.textSize = 0.01f
    tp.color = Color.TRANSPARENT
}

class HiddenSpan(override val spec: StyleRange) : MetricAffectingSpan(), MdSpan {
    override val revealed get() = false
    override fun updateMeasureState(tp: TextPaint) = hide(tp)
    override fun updateDrawState(tp: TextPaint) = hide(tp)
}

class DimSpan(override val spec: StyleRange, private val style: EditorStyle) : CharacterStyle(), MdSpan {
    override val revealed get() = true
    override fun updateDrawState(tp: TextPaint) { tp.color = style.colors.dim }
}

private val headingScale = floatArrayOf(1.7f, 1.45f, 1.25f, 1.12f, 1f, 0.95f)

class HeadingSpan(override val spec: StyleRange, private val style: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override val revealed get() = false
    private fun apply(tp: TextPaint) {
        tp.textSize = style.baseTextSize * headingScale[(spec.level - 1).coerceIn(0, 5)]
        tp.typeface = Typeface.create(tp.typeface, Typeface.BOLD)
    }
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    override fun updateDrawState(tp: TextPaint) = apply(tp)
}

class BoldSpan(override val spec: StyleRange) : StyleSpan(Typeface.BOLD), MdSpan { override val revealed get() = false }
class ItalicSpan(override val spec: StyleRange) : StyleSpan(Typeface.ITALIC), MdSpan { override val revealed get() = false }
class StrikeSpan(override val spec: StyleRange) : StrikethroughSpan(), MdSpan { override val revealed get() = false }

class InlineCodeSpan(override val spec: StyleRange, private val style: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override val revealed get() = false
    override fun updateMeasureState(tp: TextPaint) {
        tp.typeface = Typeface.MONOSPACE
        tp.textSize *= 0.9f
    }
    override fun updateDrawState(tp: TextPaint) {
        updateMeasureState(tp)
        tp.bgColor = style.colors.inlineCodeBackground
    }
}

class LinkSpan(override val spec: StyleRange, private val style: EditorStyle) : CharacterStyle(), MdSpan {
    override val revealed get() = false
    override fun updateDrawState(tp: TextPaint) { tp.color = style.colors.primary }
}

/** Draws a rounded background behind the whole range; corners only on its first and last line. */
private fun drawBlockBackground(
    span: Any, canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, bottom: Int,
    text: CharSequence, start: Int, end: Int, color: Int, radius: Float, rect: RectF, path: Path,
) {
    val spanned = text as? Spanned ?: return
    val first = start <= spanned.getSpanStart(span)
    val last = end >= spanned.getSpanEnd(span)
    val old = paint.color
    paint.color = color
    rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    val t = if (first) radius else 0f
    val b = if (last) radius else 0f
    path.reset()
    path.addRoundRect(rect, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
    canvas.drawPath(path, paint)
    paint.color = old
}

class CodeBlockSpan(override val spec: StyleRange, private val style: EditorStyle) :
    MetricAffectingSpan(), LineBackgroundSpan, LeadingMarginSpan, MdSpan {
    override val revealed get() = false
    private val rect = RectF()
    private val path = Path()
    override fun updateMeasureState(tp: TextPaint) {
        tp.typeface = Typeface.MONOSPACE
        tp.textSize = style.baseTextSize * 0.88f
    }
    override fun updateDrawState(tp: TextPaint) = updateMeasureState(tp)
    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) = drawBlockBackground(this, canvas, paint, left, right, top, bottom, text, start, end, style.colors.codeBackground, style.dp(12f), rect, path)
    override fun getLeadingMargin(first: Boolean) = style.dp(12f).toInt()
    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) = Unit
}

class CodeInfoSpan(override val spec: StyleRange, private val style: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override val revealed get() = false
    override fun updateMeasureState(tp: TextPaint) { tp.textSize = style.baseTextSize * 0.75f }
    override fun updateDrawState(tp: TextPaint) {
        updateMeasureState(tp)
        tp.color = style.colors.dim
    }
}

/** Quotes get a tinted rounded background and an inset; deliberately no left-side bar. */
class QuoteSpan(override val spec: StyleRange, private val style: EditorStyle) : LineBackgroundSpan, LeadingMarginSpan, MdSpan {
    override val revealed get() = false
    private val rect = RectF()
    private val path = Path()
    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) = drawBlockBackground(this, canvas, paint, left, right, top, bottom, text, start, end, style.colors.quoteBackground, style.dp(12f), rect, path)
    override fun getLeadingMargin(first: Boolean) = style.dp(12f).toInt()
    override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) = Unit
}

/** The `-` stays (widened, invisible) and a dot is drawn where it sits. */
class BulletSpan(override val spec: StyleRange, private val style: EditorStyle) : MetricAffectingSpan(), LineBackgroundSpan, MdSpan {
    override val revealed get() = false
    override fun updateMeasureState(tp: TextPaint) { tp.textScaleX = BULLET_SCALE }
    override fun updateDrawState(tp: TextPaint) {
        tp.textScaleX = BULLET_SCALE
        tp.color = Color.TRANSPARENT
    }
    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) {
        val spanned = text as? Spanned ?: return
        val s = spanned.getSpanStart(this)
        if (s < start || s >= end) return
        val x0 = style.x(s) ?: return
        val x1 = style.x(spanned.getSpanEnd(this)) ?: return
        val old = paint.color
        val oldStyle = paint.style
        paint.color = style.colors.dim
        val r = style.baseTextSize * 0.15f
        val cx = (x0 + x1) / 2
        val cy = baseline - style.baseTextSize * 0.3f
        if (spec.level % 2 == 1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = style.dp(1.5f)
        } else {
            paint.style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = old
        paint.style = oldStyle
    }
}

class OrderedMarkerSpan(override val spec: StyleRange, private val style: EditorStyle) : CharacterStyle(), MdSpan {
    override val revealed get() = false
    override fun updateDrawState(tp: TextPaint) { tp.color = style.colors.primary }
}

/** Draws `[ ]` / `[x]` as a checkbox behind its own invisible characters. The editor toggles it on tap. */
class TaskSpan(override val spec: StyleRange, private val style: EditorStyle) : MetricAffectingSpan(), LineBackgroundSpan, MdSpan {
    override val revealed get() = false
    private val rect = RectF()
    private val path = Path()
    override fun updateMeasureState(tp: TextPaint) { tp.textScaleX = TASK_SCALE }
    override fun updateDrawState(tp: TextPaint) {
        tp.textScaleX = TASK_SCALE
        tp.color = Color.TRANSPARENT
    }
    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) {
        val spanned = text as? Spanned ?: return
        val st = spanned.getSpanStart(this)
        if (st < start || st >= end) return
        val x0 = style.x(st) ?: return
        val x1 = style.x(spanned.getSpanEnd(this)) ?: return
        val s = style.baseTextSize * 0.9f
        val cx = (x0 + x1) / 2
        val cy = baseline - style.baseTextSize * 0.33f
        rect.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
        val old = paint.color
        val oldStyle = paint.style
        val radius = s * 0.25f
        if (spec.level == 1) {
            paint.style = Paint.Style.FILL
            paint.color = style.colors.primary
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = style.dp(2f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = style.colors.onPrimary
            path.reset()
            path.moveTo(rect.left + s * 0.26f, rect.top + s * 0.52f)
            path.lineTo(rect.left + s * 0.43f, rect.top + s * 0.69f)
            path.lineTo(rect.left + s * 0.75f, rect.top + s * 0.34f)
            canvas.drawPath(path, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = style.dp(2f)
            paint.color = style.colors.outline
            rect.inset(style.dp(1f), style.dp(1f))
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        paint.color = old
        paint.style = oldStyle
    }
}

class TaskDoneSpan(override val spec: StyleRange, private val style: EditorStyle) : CharacterStyle(), MdSpan {
    override val revealed get() = false
    override fun updateDrawState(tp: TextPaint) {
        tp.color = style.colors.dim
        tp.isStrikeThruText = true
    }
}

/**
 * One document-wide tab stop, so nested tabs line up at a readable fixed width in any font (Android's default stops
 * are 20 px apart). The editor adds [TAB_STOPS] of these over the whole text instead of one per indented line.
 */
class DocTabStop(private val level: Int, private val style: EditorStyle) : TabStopSpan, PassiveSpan {
    override fun getTabStop() = (level * style.tabWidth).toInt()
}

const val TAB_STOPS = 12

class HrSpan(override val spec: StyleRange, private val style: EditorStyle) : ReplacementSpan(), MdSpan {
    override val revealed get() = false
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?) = style.availableWidth
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val old = paint.color
        paint.color = style.colors.outlineVariant
        val cy = (top + bottom) / 2f
        canvas.drawRect(x, cy - style.dp(0.5f), x + style.availableWidth, cy + style.dp(0.5f), paint)
        paint.color = old
    }
}

/** Zero width and zero height: hides a whole line (the `|---|` row of a table). */
class CollapsedLineSpan(override val spec: StyleRange) : MetricAffectingSpan(), LineHeightSpan, MdSpan {
    override val revealed get() = false
    override fun updateMeasureState(tp: TextPaint) = hide(tp)
    override fun updateDrawState(tp: TextPaint) = hide(tp)
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) = zeroHeight(fm)
}

internal fun zeroHeight(fm: Paint.FontMetricsInt) {
    fm.ascent = 0
    fm.descent = 0
    fm.top = 0
    fm.bottom = 0
}

/**
 * Draws one table row as a grid of wrapped cells. Column widths come from [StyleRange.data] (weights shared by
 * every row of the table), so all rows line up. The source text stays untouched, so selecting and copying across a
 * table yields the original markdown.
 */
class TableRowSpan(override val spec: StyleRange, private val style: EditorStyle) : ReplacementSpan(), MdSpan {
    override val revealed get() = false
    /** Per column: (longest word, longest cell) in characters. */
    private val columns = spec.data.orEmpty().split(',').map { c ->
        c.split(':').let { (it.getOrNull(0)?.toIntOrNull() ?: 1) to (it.getOrNull(1)?.toIntOrNull() ?: 1) }
    }
    private var widths = FloatArray(0)
    private val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cachedWidth = -1
    private var cachedText: String? = null
    private var layouts: List<StaticLayout> = emptyList()
    private var rowHeight = 0

    private fun measure(text: CharSequence, start: Int, end: Int) {
        val row = text.subSequence(start, end).toString()
        val width = style.availableWidth
        if (width == cachedWidth && row == cachedText) return
        cachedWidth = width
        cachedText = row
        cellPaint.textSize = style.baseTextSize * 0.92f
        cellPaint.typeface = if (spec.level == 1) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val cells = splitTableRow(row)
        val pad = style.dp(8f).toInt()
        widths = columnWidths(width.toFloat(), cellPaint.measureText("n") * 1.1f, 2f * pad)
        layouts = widths.mapIndexed { i, w ->
            val colWidth = (w.toInt() - 2 * pad).coerceAtLeast(1)
            val cell = stripInline(cells.getOrNull(i).orEmpty())
            StaticLayout.Builder.obtain(cell, 0, cell.length, cellPaint, colWidth).setIncludePad(false).build()
        }
        rowHeight = (layouts.maxOfOrNull { it.height } ?: 0) + 2 * pad
    }

    /** Every column first gets room for its longest word, then the rest is shared by how much longer its cells are. */
    private fun columnWidths(total: Float, charWidth: Float, padding: Float): FloatArray {
        val min = FloatArray(columns.size) { columns[it].first * charWidth + padding }
        val extra = FloatArray(columns.size) { ((columns[it].second - columns[it].first) * charWidth).coerceAtLeast(0f) }
        val minSum = min.sum()
        if (minSum >= total) return FloatArray(min.size) { min[it] * total / minSum }
        val extraSum = extra.sum()
        val spare = total - minSum
        return FloatArray(min.size) {
            min[it] + if (extraSum <= spare) extra[it] + (spare - extraSum) / min.size else extra[it] * spare / extraSum
        }
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        measure(text, start, end)
        if (fm != null) {
            fm.ascent = -rowHeight
            fm.top = -rowHeight
            fm.descent = 0
            fm.bottom = 0
        }
        return style.availableWidth
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        measure(text, start, end)
        val pad = style.dp(8f)
        // The line also holds the newline's descent below our row; fill the whole line so rows touch.
        val rowTop = top.toFloat()
        val rowBottom = bottom.toFloat()
        if (spec.level == 1) {
            linePaint.style = Paint.Style.FILL
            linePaint.color = style.colors.tableHeaderBackground
            canvas.drawRect(x, rowTop, x + style.availableWidth, rowBottom, linePaint)
        }
        cellPaint.color = style.colors.text
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = style.dp(1f)
        linePaint.color = style.colors.outlineVariant
        var cx = x
        widths.forEachIndexed { i, colWidth ->
            canvas.drawRect(cx, rowTop, cx + colWidth, rowBottom, linePaint)
            canvas.save()
            canvas.translate(cx + pad, rowTop + pad)
            layouts.getOrNull(i)?.draw(canvas)
            canvas.restore()
            cx += colWidth
        }
    }
}

/** Folding: zero-height lines whose glyphs are hidden. Not parse-driven, so not an [MdSpan]. */
class Fold {
    val spans = ArrayList<Any>()
}

class FoldLineSpan(val fold: Fold) : LineHeightSpan, PassiveSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) = zeroHeight(fm)
}

class FoldGlyphSpan(val fold: Fold) : MetricAffectingSpan(), PassiveSpan {
    override fun updateMeasureState(tp: TextPaint) = hide(tp)
    override fun updateDrawState(tp: TextPaint) = hide(tp)
}

/** Marks a folded header line with a small pill right after its text. */
class FoldHeaderSpan(val fold: Fold, private val style: EditorStyle) : LineBackgroundSpan, PassiveSpan {
    private val rect = RectF()
    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) {
        val spanned = text as? Spanned ?: return
        val spanEnd = spanned.getSpanEnd(this)
        if (spanEnd < start || spanEnd > end) return // only on the header's last line
        val x = (style.x(spanEnd) ?: return) + style.dp(8f)
        val old = paint.color
        val oldSize = paint.textSize
        paint.textSize = style.baseTextSize * 0.8f
        val label = "⋯"
        val w = paint.measureText(label) + style.dp(16f)
        val h = style.baseTextSize * 1.1f
        val cy = baseline - style.baseTextSize * 0.33f
        rect.set(x, cy - h / 2, x + w, cy + h / 2)
        paint.color = style.colors.tableHeaderBackground
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        paint.color = style.colors.text
        canvas.drawText(label, rect.left + style.dp(8f), cy + paint.textSize * 0.3f, paint)
        paint.color = old
        paint.textSize = oldSize
    }
}
