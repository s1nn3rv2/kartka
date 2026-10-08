package com.s1nn3rv2.kartka.markdown

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.CustomNode
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SourceSpan
import org.commonmark.node.Text
import org.commonmark.parser.PostProcessor

/** `[[target]]` or `[[target|label]]`; [target] may include a folder path or a `#heading`. */
class WikiLink(val target: String, val label: String) : CustomNode()

/** `#tag`, without the `#`. */
class Tag(val name: String) : CustomNode()

/** `==highlighted==`; its text is the child. */
class Highlight : CustomNode()

/**
 * Finds wiki links, tags and highlights inside plain text, after the main parse (and after bare URLs became
 * links, so tags in URLs are left alone). Each match becomes its own node; source spans are kept when the text maps
 * one to one onto the source, so Edit mode can color them.
 */
class NotesInlinesPostProcessor : PostProcessor {
    override fun process(node: Node): Node {
        node.accept(object : AbstractVisitor() {
            override fun visit(text: Text) {
                if (text.parent !is Link && text.parent !is Code) split(text)
            }
        })
        return node
    }

    private fun split(text: Text) {
        val literal = text.literal
        val matches = pattern.findAll(literal).toList()
        if (matches.isEmpty()) return
        val span = text.sourceSpans.singleOrNull()?.takeIf { it.length == literal.length }
        fun spanOf(start: Int, end: Int) = span?.let { SourceSpan.of(it.lineIndex, it.columnIndex + start, it.inputIndex + start, end - start) }
        fun textNode(start: Int, end: Int) = Text(literal.substring(start, end)).apply { spanOf(start, end)?.let { setSourceSpans(listOf(it)) } }

        var pos = 0
        for (m in matches) {
            if (m.range.first > pos) text.insertBefore(textNode(pos, m.range.first))
            val start = m.range.first
            val end = m.range.last + 1
            val node: Node = when {
                m.groups["wiki"] != null -> {
                    val inner = m.groups["wiki"]!!.value
                    WikiLink(inner.substringBefore('|').trim(), inner.substringAfter('|', inner.substringBefore('|')).trim())
                }
                m.groups["mark"] != null -> Highlight().apply {
                    appendChild(Text(m.groups["mark"]!!.value).apply { spanOf(start + 2, end - 2)?.let { setSourceSpans(listOf(it)) } })
                }
                else -> Tag(m.groups["tag"]!!.value)
            }
            spanOf(start, end)?.let { node.setSourceSpans(listOf(it)) }
            text.insertBefore(node)
            pos = end
        }
        if (pos < literal.length) text.insertBefore(textNode(pos, literal.length))
        text.unlink()
    }

    private companion object {
        val pattern = Regex(
            """\[\[(?<wiki>[^\[\]\n]+)]]|==(?<mark>[^=\n](?:[^\n]*?[^=\n])?)==|(?<![\w#&/])#(?<tag>[\p{L}\p{N}_/-]*[\p{L}_][\p{L}\p{N}_/-]*)""",
        )
    }
}
