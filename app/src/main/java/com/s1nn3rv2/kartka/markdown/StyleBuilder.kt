package com.s1nn3rv2.kartka.markdown

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
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
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

enum class Kind {
    /** Syntax characters: hidden when outside the active region, dimmed inside it. */
    MARKER,
    HEADING, STRONG, EMPH, STRIKE, INLINE_CODE, LINK, IMAGE,
    CODE_BLOCK, CODE_INFO, QUOTE,
    BULLET, ORDERED_MARKER, TASK, TASK_DONE,
    /** First line of a list item, for the hanging indent of wrapped lines. `data`: the line's text before the item content. */
    LIST_ITEM,
    HR, TABLE_ROW, TABLE_DELIMITER;

    /** Kinds drawn as something else when inactive and shown as plain source when the cursor is on them. */
    val revealable get() = this == MARKER || this == HR || this == TABLE_ROW || this == TABLE_DELIMITER
}

/** [level]: heading level, list depth, indent columns, or 1 for checked tasks / header rows. [data]: url, language, or table column weights. */
data class StyleRange(val start: Int, val end: Int, val kind: Kind, val level: Int = 0, val data: String? = null)

data class HeadingInfo(val start: Int, val level: Int, val text: String)

class MarkdownStyles(
    val ranges: List<StyleRange>,
    /** Regions revealed as a whole when the cursor is anywhere inside (code fences, tables). */
    val blocks: List<IntRange>,
    val headings: List<HeadingInfo>,
)

/** Pure Kotlin: markdown text in, style ranges out. Offsets index into the given string. */
object StyleBuilder {
    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .build()

    fun build(text: String): MarkdownStyles {
        val out = ArrayList<StyleRange>()
        val blocks = ArrayList<IntRange>()
        val headings = ArrayList<HeadingInfo>()
        val dedent = Dedent.of(text)
        parser.parse(dedent.text).accept(Visitor(dedent.text, out, blocks, headings))
        if (dedent.shifted) {
            for (i in out.indices) out[i].let { out[i] = it.copy(start = dedent.map(it.start), end = dedent.mapEnd(it.end)) }
            for (i in blocks.indices) blocks[i].let { blocks[i] = dedent.map(it.first) until dedent.mapEnd(it.last + 1) }
            for (i in headings.indices) headings[i].let { headings[i] = it.copy(start = dedent.map(it.start)) }
        }
        // Android applies paragraph spans only from the paragraph start, so list items run from their line start;
        // the prefix comes from the original text, since the parser may have seen it dedented.
        for (i in out.indices) out[i].let { r ->
            if (r.kind == Kind.LIST_ITEM) {
                val start = lineStartOf(text, r.start)
                val end = text.indexOf('\n', r.end).let { if (it < 0) text.length else it }
                out[i] = r.copy(start = start, end = end, data = text.substring(start, r.end))
            } else if (r.kind == Kind.CODE_BLOCK || r.kind == Kind.QUOTE) {
                out[i] = r.copy(start = lineStartOf(text, r.start))
            }
        }
        out.sortWith(rangeOrder)
        return MarkdownStyles(out, blocks, headings)
    }

    private fun lineStartOf(text: String, offset: Int): Int {
        var i = offset
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }


    /**
     * Logseq-style notes put tab-indented bullets directly under a heading. Standard markdown reads 4+ columns of
     * indentation there as a code block, so for parsing only, such runs are shifted left by their smallest
     * indentation. [map] translates offsets in the shifted text back to the original.
     */
    private class Dedent(val text: String, private val lineStarts: IntArray, private val removed: IntArray) {
        val shifted get() = removed.isNotEmpty() && removed.last() > 0

        fun map(offset: Int): Int {
            if (!shifted) return offset
            var line = lineStarts.binarySearch(offset)
            if (line < 0) line = -line - 2
            return offset + removed[line.coerceAtLeast(0)]
        }

        fun mapEnd(end: Int) = if (end == 0) 0 else map(end - 1) + 1

        companion object {
            private val listStart = Regex("""^[ \t]*([-*+]|\d{1,9}[.)])([ \t]|$)""")

            fun of(text: String): Dedent {
                val lines = text.split('\n')
                val sb = StringBuilder(text.length)
                val starts = IntArray(lines.size)
                val removed = IntArray(lines.size) // cumulative chars removed up to and including each line
                var total = 0
                var prevStructural = false // previous non-blank line was a list item or indented
                var i = 0
                while (i < lines.size) {
                    val line = lines[i]
                    val indented = line.isNotEmpty() && (line[0] == '\t' || line[0] == ' ')
                    if (indented && line.isNotBlank() && !prevStructural && listStart.containsMatchIn(line)) {
                        var end = i
                        while (end < lines.size && (lines[end].isBlank() || lines[end][0] == '\t' || lines[end][0] == ' ')) end++
                        val ch = line[0]
                        val k = (i until end).filter { lines[it].isNotBlank() }
                            .minOf { j -> lines[j].takeWhile { it == ch }.length }
                        for (j in i until end) {
                            val cut = lines[j].takeWhile { it == ch }.length.coerceAtMost(k)
                            total += cut
                            starts[j] = sb.length
                            removed[j] = total
                            sb.append(lines[j], cut, lines[j].length)
                            if (j < lines.size - 1) sb.append('\n')
                        }
                        prevStructural = true
                        i = end
                        continue
                    }
                    starts[i] = sb.length
                    removed[i] = total
                    sb.append(line)
                    if (i < lines.size - 1) sb.append('\n')
                    if (line.isNotBlank()) prevStructural = indented || listStart.containsMatchIn(line)
                    i++
                }
                return Dedent(if (total == 0) text else sb.toString(), starts, removed)
            }
        }
    }

    private class Visitor(
        val text: String,
        val out: MutableList<StyleRange>,
        val blocks: MutableList<IntRange>,
        val headings: MutableList<HeadingInfo>,
    ) : AbstractVisitor() {

        fun add(start: Int, end: Int, kind: Kind, level: Int = 0, data: String? = null) {
            if (end > start) out += StyleRange(start, end, kind, level, data)
        }

        val SourceSpan.end get() = inputIndex + length
        fun lineEnd(from: Int) = text.indexOf('\n', from).let { if (it < 0) text.length else it }

        override fun visit(heading: Heading) {
            val spans = heading.sourceSpans
            val first = spans.first()
            var i = first.inputIndex
            if (text.getOrNull(i) == '#') {
                while (i < first.end && text[i] == '#') i++
                while (i < first.end && (text[i] == ' ' || text[i] == '\t')) i++
                add(first.inputIndex, i, Kind.MARKER)
                add(first.inputIndex, first.end, Kind.HEADING, heading.level)
            } else {
                // Setext: text lines, then an underline line.
                spans.dropLast(1).forEach { add(it.inputIndex, it.end, Kind.HEADING, heading.level) }
                spans.last().let { add(it.inputIndex, it.end, Kind.MARKER) }
            }
            headings += HeadingInfo(first.inputIndex, heading.level, text.substring(i, first.end).trim())
            visitChildren(heading)
        }

        override fun visit(node: StrongEmphasis) = delimited(node, node.openingDelimiter.length, Kind.STRONG)
        override fun visit(node: Emphasis) = delimited(node, node.openingDelimiter.length, Kind.EMPH)

        override fun visit(node: CustomNode) {
            if (node is Strikethrough) delimited(node, node.openingDelimiter.length, Kind.STRIKE) else visitChildren(node)
        }

        fun delimited(node: Node, delim: Int, kind: Kind) {
            val spans = node.sourceSpans
            if (spans.isEmpty()) return visitChildren(node)
            val start = spans.first().inputIndex
            val end = spans.last().end
            add(start, start + delim, Kind.MARKER)
            add(end - delim, end, Kind.MARKER)
            spans.forEach { add(it.inputIndex, it.end, kind) }
            visitChildren(node)
        }

        override fun visit(code: Code) {
            val span = code.sourceSpans.firstOrNull() ?: return
            val start = span.inputIndex
            val end = code.sourceSpans.last().end
            var n = 0
            while (start + n < end && text[start + n] == '`') n++
            add(start, start + n, Kind.MARKER)
            add(end - n, end, Kind.MARKER)
            add(start, end, Kind.INLINE_CODE)
        }

        override fun visit(link: Link) = linkLike(link, Kind.LINK, link.destination)
        override fun visit(image: Image) = linkLike(image, Kind.IMAGE, image.destination)

        fun linkLike(node: Node, kind: Kind, url: String) {
            val spans = node.sourceSpans
            if (spans.isEmpty()) return
            val start = spans.first().inputIndex
            val end = spans.last().end
            val textStart = node.firstChild?.sourceSpans?.firstOrNull()?.inputIndex
            val textEnd = node.lastChild?.sourceSpans?.lastOrNull()?.end
            if (textStart == null || textEnd == null) {
                add(start, end, kind, data = url)
            } else {
                add(start, textStart, Kind.MARKER)
                add(textEnd, end, Kind.MARKER)
                add(textStart, textEnd, kind, data = url)
            }
            visitChildren(node)
        }

        override fun visit(quote: BlockQuote) {
            for (span in quote.sourceSpans) {
                var i = span.inputIndex
                while (i < span.end && text[i] != '>') i++
                if (i < span.end) {
                    val markerEnd = if (text.getOrNull(i + 1) == ' ') i + 2 else i + 1
                    add(i, markerEnd, Kind.MARKER)
                }
            }
            val spans = quote.sourceSpans
            if (spans.isNotEmpty()) add(spans.first().inputIndex, lineEnd(spans.last().inputIndex), Kind.QUOTE)
            visitChildren(quote)
        }

        override fun visit(item: ListItem) {
            val span = item.sourceSpans.firstOrNull() ?: return visitChildren(item)
            var i = span.inputIndex
            val end = span.end
            while (i < end && (text[i] == ' ' || text[i] == '\t')) i++
            val depth = generateSequence(item.parent) { it.parent }.count { it is ListBlock } - 1
            val itemStart = i
            if (item.parent is BulletList) {
                add(i, i + 1, Kind.BULLET, depth)
                i++
            } else {
                val markerStart = i
                while (i < end && text[i].isDigit()) i++
                i++ // '.' or ')'
                add(markerStart, i, Kind.ORDERED_MARKER, depth)
            }
            while (i < end && (text[i] == ' ' || text[i] == '\t')) i++
            if (i + 2 < text.length && text[i] == '[' && text[i + 2] == ']' && text[i + 1] in " xX") {
                val checked = text[i + 1] != ' '
                add(i, i + 3, Kind.TASK, if (checked) 1 else 0)
                if (checked) add(i + 3, lineEnd(i), Kind.TASK_DONE)
                i += 3
                while (i < end && (text[i] == ' ' || text[i] == '\t')) i++
            }
            // Item start to content start; build() widens it to the whole line and stores the prefix text.
            out += StyleRange(itemStart, i, Kind.LIST_ITEM)
            visitChildren(item)
        }

        override fun visit(block: FencedCodeBlock) = codeBlock(block, fenced = true, info = block.info)
        override fun visit(block: IndentedCodeBlock) = codeBlock(block, fenced = false, info = null)

        fun codeBlock(block: Node, fenced: Boolean, info: String?) {
            val spans = block.sourceSpans
            if (spans.isEmpty()) return
            val start = spans.first().inputIndex
            val end = spans.last().end
            add(start, end, Kind.CODE_BLOCK, data = info?.takeIf { it.isNotBlank() })
            if (fenced) {
                val open = spans.first()
                var i = open.inputIndex
                while (i < open.end && (text[i] == '`' || text[i] == '~')) i++
                add(open.inputIndex, i, Kind.MARKER)
                add(i, open.end, Kind.CODE_INFO)
                val close = spans.last()
                val closeText = text.substring(close.inputIndex, close.end).trim()
                if (spans.size > 1 && closeText.isNotEmpty() && closeText.all { it == '`' || it == '~' }) {
                    add(close.inputIndex, close.end, Kind.MARKER)
                }
                blocks += start until end
            }
        }

        override fun visit(hr: ThematicBreak) {
            hr.sourceSpans.firstOrNull()?.let { add(it.inputIndex, it.end, Kind.HR) }
        }

        override fun visit(block: CustomBlock) {
            if (block !is TableBlock) return visitChildren(block)
            val lines = block.sourceSpans
            if (lines.size < 2) return
            val rows = lines.map { splitTableRow(text.substring(it.inputIndex, it.end)) }
            val columns = rows[1].size
            // Per column "longest word:longest cell" in characters; the row span turns these into pixel widths.
            val weights = (0 until columns).joinToString(",") { c ->
                val cells = rows.filterIndexed { i, _ -> i != 1 }.map { stripInline(it.getOrNull(c).orEmpty()) }
                "${cells.maxOf { cell -> cell.split(' ').maxOf { it.length } }}:${cells.maxOf { it.length }}"
            }
            lines.forEachIndexed { i, span ->
                if (i == 1) add(span.inputIndex, span.end, Kind.TABLE_DELIMITER)
                else add(span.inputIndex, span.end, Kind.TABLE_ROW, if (i == 0) 1 else 0, weights)
            }
            blocks += lines.first().inputIndex until lines.last().end
            // Inline styles inside cells are drawn by the row span; skip children to avoid hidden markers in raw rows.
        }
    }
}

/** Document order, with ties broken on every field so equal parses give equal lists. */
val rangeOrder: Comparator<StyleRange> =
    compareBy<StyleRange>({ it.start }, { it.end }, { it.kind.ordinal }, { it.level }, { it.data })

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

/** Splits a table row into cell texts, honoring escaped pipes and pipes inside backticks. */
fun splitTableRow(row: String): List<String> {
    val cells = ArrayList<String>()
    val cur = StringBuilder()
    var inCode = false
    var i = 0
    val s = row.trim().removePrefix("|")
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && s[i + 1] == '|' -> { cur.append('|'); i++ }
            c == '`' -> { inCode = !inCode; cur.append(c) }
            c == '|' && !inCode -> { cells += cur.toString().trim(); cur.clear() }
            else -> cur.append(c)
        }
        i++
    }
    if (cur.isNotBlank()) cells += cur.toString().trim()
    return cells
}

private val inlineMarkup = Regex("""\*\*|__|~~|==|`|(?<!\w)[*_]|[*_](?!\w)""")
private val inlineLink = Regex("""!?\[([^\]]*)]\([^)]*\)""")

/** Plain text of a table cell: link text kept, emphasis and code markers removed. */
fun stripInline(cell: String): String = inlineMarkup.replace(inlineLink.replace(cell, "$1"), "")
