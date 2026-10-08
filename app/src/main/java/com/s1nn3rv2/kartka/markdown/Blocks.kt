package com.s1nn3rv2.kartka.markdown

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak

enum class InlineStyle { STRONG, EMPH, STRIKE, CODE, LINK }

data class InlineSpan(val start: Int, val end: Int, val style: InlineStyle, val url: String? = null)

/** Rendered inline text: plain characters plus style ranges over them. */
data class Inline(val text: String, val spans: List<InlineSpan> = emptyList())

/** One row of View mode. [indent] is the nesting depth: children of a list item have its indent + 1. */
sealed interface MdBlock {
    val indent: Int
}

data class MdHeading(val level: Int, val text: Inline, override val indent: Int) : MdBlock
data class MdParagraph(val text: Inline, override val indent: Int, val quote: Boolean) : MdBlock

/** [marker] is "•" or "3."; [task] is set for `- [ ]` items; [collapsed] comes from Logseq's `collapsed:: true`. */
data class MdListItem(
    val text: Inline,
    override val indent: Int,
    val marker: String,
    val task: Task?,
    val collapsed: Boolean,
) : MdBlock

/** [offset]: position of the character between the brackets in the original text, for toggling. */
data class Task(val checked: Boolean, val offset: Int)

data class MdCode(val code: String, val language: String?, override val indent: Int) : MdBlock
data class MdRule(override val indent: Int) : MdBlock

/** [columns]: per column, (longest word, longest cell) in characters, for sizing. */
data class MdTable(
    val header: List<Inline>,
    val rows: List<List<Inline>>,
    val columns: List<Pair<Int, Int>>,
    override val indent: Int,
) : MdBlock

/** Pure Kotlin: markdown text in, View-mode rows out. */
object BlockBuilder {
    private val property = Regex("""^[\w-]+:: ?(.*)$""")

    fun build(text: String): List<MdBlock> {
        val (doc, dedent) = Md.parse(text)
        val out = ArrayList<MdBlock>()
        Walker(dedent, out).blocks(doc.firstChild, indent = 0, quote = false)
        return out
    }

    private class Walker(val dedent: Dedent, val out: MutableList<MdBlock>) {
        fun blocks(first: Node?, indent: Int, quote: Boolean) {
            var n = first
            while (n != null) {
                when (n) {
                    is Heading -> out += MdHeading(n.level, inline(n).first, indent)
                    is Paragraph -> inline(n).first.takeIf { it.text.isNotBlank() }?.let { out += MdParagraph(it, indent, quote) }
                    is BlockQuote -> blocks(n.firstChild, indent, quote = true)
                    is BulletList -> items(n, indent, quote, null)
                    is OrderedList -> items(n, indent, quote, n.markerStartNumber ?: 1)
                    is FencedCodeBlock -> out += MdCode(n.literal.trimEnd('\n'), n.info?.takeIf { it.isNotBlank() }, indent)
                    is IndentedCodeBlock -> out += MdCode(n.literal.trimEnd('\n'), null, indent)
                    is ThematicBreak -> out += MdRule(indent)
                    is TableBlock -> out += table(n, indent)
                    is HtmlBlock -> out += MdParagraph(Inline(n.literal.trimEnd()), indent, quote)
                    else -> blocks(n.firstChild, indent, quote)
                }
                n = n.next
            }
        }

        fun items(list: Node, indent: Int, quote: Boolean, start: Int?) {
            var number = start
            var item = list.firstChild
            while (item != null) {
                if (item is ListItem) {
                    var child = item.firstChild
                    var task: Task? = null
                    if (child is TaskListItemMarker) {
                        task = Task(child.isChecked, taskOffset(item))
                        child = child.next
                    }
                    var text = Inline("")
                    var props = emptyMap<String, String>()
                    if (child is Paragraph) {
                        inline(child).let { text = it.first; props = it.second }
                        child = child.next
                    }
                    out += MdListItem(text, indent, number?.let { "$it." } ?: "•", task, props["collapsed"] == "true")
                    number = number?.plus(1)
                    blocks(child, indent + 1, quote)
                }
                item = item.next
            }
        }

        /** Original-text offset of the character between `[` and `]` of a task item. */
        fun taskOffset(item: ListItem): Int {
            val span = item.sourceSpans.firstOrNull() ?: return -1
            val bracket = dedent.text.indexOf('[', span.inputIndex)
            return if (bracket < 0) -1 else dedent.map(bracket + 1)
        }

        fun table(block: TableBlock, indent: Int): MdTable {
            val rows = ArrayList<List<Inline>>()
            fun collect(n: Node?) {
                var c = n
                while (c != null) {
                    if (c is TableRow) {
                        val cells = ArrayList<Inline>()
                        var cell = c.firstChild
                        while (cell != null) {
                            if (cell is TableCell) cells += inline(cell).first
                            cell = cell.next
                        }
                        rows += cells
                    } else {
                        collect(c.firstChild)
                    }
                    c = c.next
                }
            }
            collect(block.firstChild)
            val count = rows.maxOfOrNull { it.size } ?: 0
            val columns = (0 until count).map { col ->
                val texts = rows.map { it.getOrNull(col)?.text.orEmpty() }
                texts.maxOf { t -> t.split(' ').maxOf { it.length } } to texts.maxOf { it.length }
            }
            return MdTable(rows.firstOrNull().orEmpty(), rows.drop(1), columns, indent)
        }

        /** Inline text of [parent], plus Logseq `key:: value` property lines, which are taken out of the text. */
        fun inline(parent: Node): Pair<Inline, Map<String, String>> {
            val sb = StringBuilder()
            val spans = ArrayList<InlineSpan>()
            val props = HashMap<String, String>()

            fun styled(node: Node, style: InlineStyle, url: String? = null, walk: (Node) -> Unit) {
                val start = sb.length
                walk(node)
                if (sb.length > start) spans += InlineSpan(start, sb.length, style, url)
            }

            fun walk(node: Node) {
                var c = node.firstChild
                var lineStart = true
                while (c != null) {
                    if (lineStart && c is Text) {
                        val match = property.matchEntire(c.literal)
                        if (match != null) {
                            props[c.literal.substringBefore("::")] = match.groupValues[1].trim()
                            c = c.next
                            if (c is SoftLineBreak || c is HardLineBreak) c = c.next
                            continue
                        }
                    }
                    lineStart = false
                    when (c) {
                        is Text -> sb.append(c.literal)
                        is Code -> styled(c, InlineStyle.CODE) { sb.append((it as Code).literal) }
                        is Emphasis -> styled(c, InlineStyle.EMPH, walk = ::walk)
                        is StrongEmphasis -> styled(c, InlineStyle.STRONG, walk = ::walk)
                        is Strikethrough -> styled(c, InlineStyle.STRIKE, walk = ::walk)
                        is Link -> styled(c, InlineStyle.LINK, c.destination, ::walk)
                        is Image -> styled(c, InlineStyle.LINK, c.destination) { img ->
                            val before = sb.length
                            walk(img)
                            if (sb.length == before) sb.append("image")
                        }
                        is SoftLineBreak -> { sb.append(' '); lineStart = true }
                        is HardLineBreak -> { sb.append('\n'); lineStart = true }
                        is HtmlInline -> sb.append(c.literal)
                        else -> walk(c)
                    }
                    c = c.next
                }
            }

            walk(parent)
            // A property line after a soft break leaves a trailing space.
            val text = sb.trimEnd().toString()
            return Inline(text, spans.map { it.copy(end = minOf(it.end, text.length)) }.filter { it.end > it.start }) to props
        }
    }
}

/** Index of the first block after the region that folding block [i] hides. */
fun foldEnd(blocks: List<MdBlock>, i: Int): Int {
    val b = blocks[i]
    var j = i + 1
    if (b is MdHeading) {
        while (j < blocks.size && !(blocks[j].let { it is MdHeading && it.level <= b.level })) j++
    } else {
        while (j < blocks.size && blocks[j].indent > b.indent && blocks[j] !is MdHeading) j++
    }
    return j
}

fun canFold(blocks: List<MdBlock>, i: Int) = foldEnd(blocks, i) > i + 1

/** Indices of the blocks to show when the blocks in [folded] have their children hidden. */
fun visibleBlocks(blocks: List<MdBlock>, folded: Set<Int>): List<Int> {
    val out = ArrayList<Int>(blocks.size)
    var i = 0
    while (i < blocks.size) {
        out += i
        i = if (i in folded) foldEnd(blocks, i) else i + 1
    }
    return out
}
