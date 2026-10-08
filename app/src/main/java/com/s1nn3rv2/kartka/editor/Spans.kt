package com.s1nn3rv2.kartka.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TabStopSpan
import com.s1nn3rv2.kartka.markdown.Kind
import com.s1nn3rv2.kartka.markdown.StyleRange

/** Colors pulled from the Material theme; spans read them at draw time so theme changes need no re-parse. */
data class EditorColors(
    val text: Int,
    val dim: Int,
    val primary: Int,
    val tertiary: Int,
    val codeBackground: Int,
    val inlineCodeBackground: Int,
)

/** Shared, mutable drawing context for all spans of one editor. */
class EditorStyle(val density: Float, var colors: EditorColors) {
    var baseTextSize = 16f * density
    val tabWidth get() = baseTextSize * 1.4f
    fun dp(v: Float) = v * density
}

/*
 * Edit mode only colors the source; nothing is hidden or drawn over characters. Avoid ReplacementSpan: Android
 * redraws every text block that contains one on every frame.
 */

/** Spans whose moving with the text needs no re-layout; [FastEditable] skips their shift notifications while editing. */
interface PassiveSpan

/** A span created from a parse result. The editor diffs these against each new parse. */
interface MdSpan : PassiveSpan {
    val spec: StyleRange
}

fun createSpan(r: StyleRange, style: EditorStyle): MdSpan = when (r.kind) {
    Kind.MARKER -> ColorSpan(r, style) { it.dim }
    Kind.HEADING -> HeadingSpan(r, style)
    Kind.STRONG -> BoldSpan(r)
    Kind.EMPH -> ItalicSpan(r)
    Kind.STRIKE -> StrikeSpan(r)
    Kind.INLINE_CODE -> InlineCodeSpan(r, style)
    Kind.LINK -> ColorSpan(r, style) { it.primary }
    Kind.CODE_BLOCK -> CodeBlockSpan(r, style)
    Kind.QUOTE -> ColorSpan(r, style) { it.dim }
    Kind.LIST_MARKER -> ColorSpan(r, style) { it.primary }
    Kind.TASK_DONE -> TaskDoneSpan(r, style)
}

class ColorSpan(override val spec: StyleRange, private val style: EditorStyle, private val color: (EditorColors) -> Int) :
    CharacterStyle(), MdSpan {
    override fun updateDrawState(tp: TextPaint) { tp.color = color(style.colors) }
}

class HeadingSpan(override val spec: StyleRange, private val style: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = Typeface.create(tp.typeface, Typeface.BOLD) }
    override fun updateDrawState(tp: TextPaint) {
        updateMeasureState(tp)
        tp.color = style.colors.primary
    }
}

class BoldSpan(override val spec: StyleRange) : StyleSpan(Typeface.BOLD), MdSpan
class ItalicSpan(override val spec: StyleRange) : StyleSpan(Typeface.ITALIC), MdSpan
class StrikeSpan(override val spec: StyleRange) : StrikethroughSpan(), MdSpan

class InlineCodeSpan(override val spec: StyleRange, private val style: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = Typeface.MONOSPACE }
    override fun updateDrawState(tp: TextPaint) {
        updateMeasureState(tp)
        tp.color = style.colors.tertiary
        tp.bgColor = style.colors.inlineCodeBackground
    }
}

/** Monospace text on a rounded background; corners only on the block's first and last line. */
class CodeBlockSpan(override val spec: StyleRange, private val style: EditorStyle) : MetricAffectingSpan(), LineBackgroundSpan, MdSpan {
    private val rect = RectF()
    private val path = Path()
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = Typeface.MONOSPACE }
    override fun updateDrawState(tp: TextPaint) = updateMeasureState(tp)
    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) {
        val spanned = text as? Spanned ?: return
        val r = style.dp(8f)
        val t = if (start <= spanned.getSpanStart(this)) r else 0f
        val b = if (end >= spanned.getSpanEnd(this)) r else 0f
        val old = paint.color
        paint.color = style.colors.codeBackground
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        path.reset()
        path.addRoundRect(rect, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
        canvas.drawPath(path, paint)
        paint.color = old
    }
}

class TaskDoneSpan(override val spec: StyleRange, private val style: EditorStyle) : CharacterStyle(), MdSpan {
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
