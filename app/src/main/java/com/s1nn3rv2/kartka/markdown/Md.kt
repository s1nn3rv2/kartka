package com.s1nn3rv2.kartka.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Node
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** The one markdown parser setup, shared by View mode (blocks) and Edit mode (colors). */
object Md {
    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create(),
                FootnotesExtension.create(),
            ),
        )
        .postProcessor(NotesInlinesPostProcessor())
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .build()

    /** Parses [text] after the Logseq dedent; source offsets in the tree index into [Dedent.text]. */
    fun parse(text: String): Pair<Node, Dedent> {
        val dedent = Dedent.of(text)
        return parser.parse(dedent.text) to dedent
    }
}

/**
 * Logseq-style notes put tab-indented bullets directly under a heading. Standard markdown reads 4+ columns of
 * indentation there as a code block, so for parsing only, such runs are shifted left by their smallest
 * indentation. [map] translates offsets in the shifted text back to the original.
 */
class Dedent private constructor(val text: String, private val lineStarts: IntArray, private val removed: IntArray) {
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
