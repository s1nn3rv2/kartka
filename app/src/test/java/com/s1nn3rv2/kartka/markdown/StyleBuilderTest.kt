package com.s1nn3rv2.kartka.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleBuilderTest {
    private fun texts(md: String, kind: Kind) = StyleBuilder.build(md).filter { it.kind == kind }.map { md.substring(it.start, it.end) }

    // Edit mode dims exactly the syntax characters and colors the content between them.
    @Test fun markersAndContent() {
        val md = "# Title\nSome **bold** and *it* and `code` and [link](https://x.y)\n"
        assertEquals(listOf("#", "**", "**", "*", "*", "[", "](https://x.y)"), texts(md, Kind.MARKER))
        assertEquals(listOf("bold"), texts(md, Kind.STRONG))
        assertEquals(listOf("`code`"), texts(md, Kind.INLINE_CODE))
        assertEquals(listOf("link"), texts(md, Kind.LINK))
    }

    // Logseq-style tab bullets must map back to the original offsets after the parser saw them dedented.
    @Test fun logseqOffsetsMapBack() {
        val md = "# Components\n\t- ```jsx\n\t  const a = 1;\n\t  ```\n\t- Main **idea**\n"
        assertEquals(listOf("-", "-"), texts(md, Kind.LIST_MARKER))
        assertEquals(listOf("idea"), texts(md, Kind.STRONG))
        assertTrue(texts(md, Kind.CODE_BLOCK).single().contains("const a = 1;"))
    }

    // Wiki links, tags and highlights must be colored in Edit mode too, also inside dedented Logseq bullets.
    @Test fun notesInlines() {
        val md = "# H\n\t- see [[Other note|other]] and ==this== #todo\n"
        assertEquals(listOf("Other note|other"), texts(md, Kind.LINK))
        assertEquals(listOf("this"), texts(md, Kind.HIGHLIGHT))
        assertEquals(listOf("#todo"), texts(md, Kind.TAG))
        assertEquals(listOf("#", "[[", "]]", "==", "=="), texts(md, Kind.MARKER))
    }

    @Test fun footnoteSyntax() {
        val md = "Text[^1].\n\n[^1]: Note.\n"
        assertEquals(listOf("[^1]"), texts(md, Kind.LINK))
        assertEquals(listOf("[^1]:"), texts(md, Kind.MARKER))
    }

    @Test fun tableSyntaxIsDimmed() {
        val md = "| a | b |\n|---|---|\n| 1 | 2 |\n"
        assertEquals(6 + 1, texts(md, Kind.MARKER).size)
    }

    // Typing must only restyle the edited area: everything outside the window is reused as-is.
    @Test fun changedWindowCoversOnlyTheEdit() {
        val before = "# A\n\n**one** two\n\n- three\n- four\n"
        val after = "# A\n\n**one** twoX\n\n- three\n- four\n"
        assertNull(changedWindow(StyleBuilder.build(before), StyleBuilder.build(after), 1))
        val bold = "# A\n\n**one** **two**\n\n- three\n- four\n"
        val w = changedWindow(StyleBuilder.build(before), StyleBuilder.build(bold), 4)!!
        assertEquals("**two**", bold.substring(w.first, w.last + 1))
    }
}
