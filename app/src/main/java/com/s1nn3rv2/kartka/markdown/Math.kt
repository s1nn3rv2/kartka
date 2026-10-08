package com.s1nn3rv2.kartka.markdown

import org.commonmark.node.Block
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.parser.SourceLine
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.beta.Scanner
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

/** `$latex$` inside a line. Parsed before markdown rules, so `\{`, `_` and `*` in formulas stay as written. */
class MathInline(val latex: String) : CustomNode()

/** A `$$ … $$` block, on one line or spanning several. */
class MathBlock : CustomBlock() {
    var latex = ""
}

/**
 * `$…$` like Obsidian and Pandoc: no space right inside the dollars and no digit right after the closing one, so
 * prices like "$5 and $10" stay text. Stays on one line.
 */
class MathInlineParser : InlineContentParser {
    override fun tryParse(state: InlineParserState): ParsedInline? {
        val s = state.scanner()
        s.next()
        if (s.peek() == '$' || s.peek() == Scanner.END || s.peek().isWhitespace()) return ParsedInline.none()
        val start = s.position()
        while (s.hasNext()) {
            when (s.peek()) {
                '\n' -> return ParsedInline.none()
                '\\' -> {
                    s.next()
                    if (s.peek() != '\n' && s.hasNext()) s.next()
                }
                '$' -> {
                    val end = s.position()
                    val spaceBefore = Character.isWhitespace(s.peekPreviousCodePoint())
                    s.next()
                    if (!spaceBefore && !s.peek().isDigit()) {
                        return ParsedInline.of(MathInline(s.getSource(start, end).content), s.position())
                    }
                }
                else -> s.next()
            }
        }
        return ParsedInline.none()
    }

    class Factory : InlineContentParserFactory {
        override fun getTriggerCharacters() = setOf('$')
        override fun create() = MathInlineParser()
    }
}

class MathBlockParser(first: String, private var done: Boolean) : AbstractBlockParser() {
    private val block = MathBlock()
    private val lines = StringBuilder(first)

    override fun getBlock(): Block = block

    override fun tryContinue(state: ParserState): BlockContinue? {
        if (done) return BlockContinue.none()
        val line = state.line.content
        val rest = line.subSequence(state.nextNonSpaceIndex.coerceAtMost(line.length), line.length).toString().trimEnd()
        if (rest.endsWith("$$")) {
            lines.append('\n').append(rest.removeSuffix("$$"))
            done = true
            return BlockContinue.finished()
        }
        return BlockContinue.atIndex(state.index)
    }

    override fun addLine(line: SourceLine) {
        lines.append('\n').append(line.content)
    }

    override fun closeBlock() {
        block.latex = lines.toString().trim()
    }

    class Factory : AbstractBlockParserFactory() {
        override fun tryStart(state: ParserState, matched: MatchedBlockParser): BlockStart? {
            if (state.indent >= 4) return BlockStart.none()
            val line = state.line.content
            val rest = line.subSequence(state.nextNonSpaceIndex, line.length).toString().trim()
            if (!rest.startsWith("$$")) return BlockStart.none()
            val inner = rest.removePrefix("$$")
            val oneLine = inner.endsWith("$$")
            return BlockStart.of(MathBlockParser(if (oneLine) inner.removeSuffix("$$") else inner, done = oneLine)).atIndex(line.length)
        }
    }
}
