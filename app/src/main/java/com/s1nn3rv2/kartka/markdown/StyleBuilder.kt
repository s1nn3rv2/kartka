package com.s1nn3rv2.kartka.markdown

import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak

/** What a range of source text is, for Edit-mode coloring. Markdown symbols stay visible, only dimmed. */
enum class Kind {
    MARKER, HEADING, STRONG, EMPH, STRIKE, INLINE_CODE, LINK, CODE_BLOCK, QUOTE, LIST_MARKER, TASK_DONE, TAG, HIGHLIGHT,
    CODE_KEYWORD, CODE_STRING, CODE_NUMBER, CODE_COMMENT,
}

/** [level]: heading level, otherwise 0. */
data class StyleRange(val start: Int, val end: Int, val kind: Kind, val level: Int = 0)

/** Pure Kotlin: markdown text in, color ranges out, sorted by [rangeOrder]. Offsets index into the given string. */
object StyleBuilder {
    fun build(text: String): List<StyleRange> {
        val (doc, dedent) = Md.parse(text)
        val out = ArrayList<StyleRange>()
        doc.accept(Visitor(dedent.text, out))
        if (dedent.shifted) {
            for (i in out.indices) out[i].let { out[i] = it.copy(start = dedent.map(it.start), end = dedent.mapEnd(it.end)) }
        }
        out.sortWith(rangeOrder)
        return out
    }

    private class Visitor(val text: String, val out: MutableList<StyleRange>) : AbstractVisitor() {
        fun add(start: Int, end: Int, kind: Kind, level: Int = 0) {
            if (end > start) out += StyleRange(start, end, kind, level)
        }

        val SourceSpan.end get() = inputIndex + length
        fun lineEnd(from: Int) = text.indexOf('\n', from).let { if (it < 0) text.length else it }

        override fun visit(heading: Heading) {
            val spans = heading.sourceSpans
            val first = spans.first()
            var i = first.inputIndex
            if (text.getOrNull(i) == '#') {
                while (i < first.end && text[i] == '#') i++
                add(first.inputIndex, i, Kind.MARKER)
                add(i, first.end, Kind.HEADING, heading.level)
            } else {
                // Setext: text lines, then an underline line.
                spans.dropLast(1).forEach { add(it.inputIndex, it.end, Kind.HEADING, heading.level) }
                spans.last().let { add(it.inputIndex, it.end, Kind.MARKER) }
            }
            visitChildren(heading)
        }

        override fun visit(node: StrongEmphasis) = delimited(node, node.openingDelimiter.length, Kind.STRONG)
        override fun visit(node: Emphasis) = delimited(node, node.openingDelimiter.length, Kind.EMPH)

        override fun visit(node: CustomNode) {
            when (node) {
                is Strikethrough -> delimited(node, node.openingDelimiter.length, Kind.STRIKE)
                is Highlight -> delimited(node, 2, Kind.HIGHLIGHT)
                is WikiLink -> node.sourceSpans.singleOrNull()?.let { delimited(node, 2, Kind.LINK) }
                is MathInline -> node.sourceSpans.firstOrNull()?.let { add(it.inputIndex, node.sourceSpans.last().end, Kind.INLINE_CODE) }
                is Tag -> node.sourceSpans.singleOrNull()?.let { add(it.inputIndex, it.end, Kind.TAG) }
                is FootnoteReference -> node.sourceSpans.firstOrNull()?.let { add(it.inputIndex, it.inputIndex + node.label.length + 3, Kind.LINK) }
                else -> visitChildren(node)
            }
        }

        fun delimited(node: Node, delim: Int, kind: Kind) {
            val spans = node.sourceSpans
            if (spans.isEmpty()) return visitChildren(node)
            val start = spans.first().inputIndex
            val end = spans.last().end
            add(start, start + delim, Kind.MARKER)
            add(end - delim, end, Kind.MARKER)
            add(start + delim, end - delim, kind)
            visitChildren(node)
        }

        override fun visit(code: Code) {
            val start = code.sourceSpans.firstOrNull()?.inputIndex ?: return
            val end = code.sourceSpans.last().end
            add(start, end, Kind.INLINE_CODE)
        }

        override fun visit(link: Link) = linkLike(link)
        override fun visit(image: Image) = linkLike(image)

        fun linkLike(node: Node) {
            val spans = node.sourceSpans
            if (spans.isEmpty()) return
            val start = spans.first().inputIndex
            val end = spans.last().end
            val textStart = node.firstChild?.sourceSpans?.firstOrNull()?.inputIndex
            val textEnd = node.lastChild?.sourceSpans?.lastOrNull()?.end
            if (textStart == null || textEnd == null) {
                add(start, end, Kind.LINK)
            } else {
                add(start, textStart, Kind.MARKER)
                add(textEnd, end, Kind.MARKER)
                add(textStart, textEnd, Kind.LINK)
            }
            visitChildren(node)
        }

        override fun visit(quote: BlockQuote) {
            for (span in quote.sourceSpans) add(span.inputIndex, lineEnd(span.inputIndex), Kind.QUOTE)
            visitChildren(quote)
        }

        override fun visit(item: ListItem) {
            val span = item.sourceSpans.firstOrNull() ?: return visitChildren(item)
            var i = span.inputIndex
            val end = span.end
            while (i < end && (text[i] == ' ' || text[i] == '\t')) i++
            val markerStart = i
            if (item.parent is BulletList) i++ else {
                while (i < end && text[i].isDigit()) i++
                i++ // '.' or ')'
            }
            add(markerStart, i, Kind.LIST_MARKER)
            while (i < end && (text[i] == ' ' || text[i] == '\t')) i++
            if (i + 2 < text.length && text[i] == '[' && text[i + 2] == ']' && text[i + 1] in " xX") {
                add(i, i + 3, Kind.LIST_MARKER)
                if (text[i + 1] != ' ') add(i + 3, lineEnd(i), Kind.TASK_DONE)
            }
            visitChildren(item)
        }

        override fun visit(block: FencedCodeBlock) {
            codeBlock(block)
            // The lines between the fences, highlighted as written (indentation included, so offsets stay exact).
            val spans = block.sourceSpans
            val last = if (block.closingFenceLength != null) spans.size - 1 else spans.size
            if (last <= 1) return
            val start = spans[1].inputIndex
            for (t in CodeHighlight.tokens(text.substring(start, spans[last - 1].end), block.info)) {
                add(start + t.start, start + t.end, codeKinds[t.token.ordinal])
            }
        }
        override fun visit(block: IndentedCodeBlock) = codeBlock(block)

        fun codeBlock(block: Node) {
            val spans = block.sourceSpans
            if (spans.isNotEmpty()) add(spans.first().inputIndex, spans.last().end, Kind.CODE_BLOCK)
        }

        override fun visit(hr: ThematicBreak) {
            hr.sourceSpans.firstOrNull()?.let { add(it.inputIndex, it.end, Kind.MARKER) }
        }

        override fun visit(block: CustomBlock) {
            if (block is MathBlock) return codeBlock(block)
            if (block is FootnoteDefinition) {
                // The `[^label]:` prefix is syntax.
                block.sourceSpans.firstOrNull()?.let { add(it.inputIndex, it.inputIndex + block.label.length + 4, Kind.MARKER) }
                return visitChildren(block)
            }
            if (block !is TableBlock) return visitChildren(block)
            // Pipes and the |---| row are syntax; cell text keeps its inline styles.
            block.sourceSpans.forEachIndexed { row, span ->
                if (row == 1) {
                    add(span.inputIndex, span.end, Kind.MARKER)
                } else {
                    for (i in span.inputIndex until span.end) if (text[i] == '|') add(i, i + 1, Kind.MARKER)
                }
            }
            visitChildren(block)
        }
    }
}

private val codeKinds = Token.entries.map { Kind.valueOf("CODE_${it.name}") }

/** Document order, with ties broken on every field so equal parses give equal lists. */
val rangeOrder: Comparator<StyleRange> = compareBy({ it.start }, { it.end }, { it.kind.ordinal }, { it.level })

/**
 * The region (in new-text offsets, end exclusive) where [new] differs from [old], given that the text grew by [delta]
 * characters since [old] was built. Both lists must be sorted by [rangeOrder]. Ranges before the first difference
 * are identical; ranges after the last difference are identical once shifted by [delta]. Null when nothing changed.
 */
fun changedWindow(old: List<StyleRange>, new: List<StyleRange>, delta: Int): IntRange? {
    val min = minOf(old.size, new.size)
    var i = 0
    while (i < min && old[i] == new[i]) i++
    if (i == old.size && i == new.size) return null
    var j = 0
    while (j < min - i) {
        val o = old[old.size - 1 - j]
        if (o.copy(start = o.start + delta, end = o.end + delta) != new[new.size - 1 - j]) break
        j++
    }
    var a = Int.MAX_VALUE
    var b = Int.MIN_VALUE
    for (k in i until old.size - j) old[k].let {
        a = minOf(a, it.start, it.start + delta)
        b = maxOf(b, it.end, it.end + delta)
    }
    for (k in i until new.size - j) new[k].let {
        a = minOf(a, it.start)
        b = maxOf(b, it.end)
    }
    if (a > b) return null
    return a.coerceAtLeast(0) until b
}
