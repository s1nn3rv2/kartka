package com.s1nn3rv2.kartka.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlightTest {
    private fun tokens(code: String, lang: String?) = CodeHighlight.tokens(code, lang).map { code.substring(it.start, it.end) to it.token }

    @Test fun javascript() {
        val code = "const a = \"if\"; // note\nreturn 42"
        assertEquals(
            listOf("const" to Token.KEYWORD, "\"if\"" to Token.STRING, "// note" to Token.COMMENT, "return" to Token.KEYWORD, "42" to Token.NUMBER),
            tokens(code, "js"),
        )
    }

    // `#` starts a comment only in languages where it does; in JS it's a private field.
    @Test fun hashComments() {
        assertEquals(listOf("# hi" to Token.COMMENT), tokens("# hi", "bash"))
        assertTrue(tokens("this.#x", "js").none { it.second == Token.COMMENT })
    }

    // Plain text and blocks without a language stay uncolored, so prose words like "if" aren't highlighted.
    @Test fun plainTextIsNotColored() {
        assertTrue(tokens("if you return", null).isEmpty())
        assertTrue(tokens("if you return", "text").isEmpty())
    }
}
