package com.s1nn3rv2.kartka.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleBuilderTest {
    private fun ranges(md: String, kind: Kind) = StyleBuilder.build(md).ranges.filter { it.kind == kind }
    private fun texts(md: String, kind: Kind) = ranges(md, kind).map { md.substring(it.start, it.end) }

    // Hidden markers must cover exactly the syntax characters, or live preview hides real text.
    @Test fun markersCoverOnlySyntax() {
        val md = "# Title\nSome **bold** and *it* and `code` and [link](https://x.y)\n"
        assertEquals(listOf("# ", "**", "**", "*", "*", "`", "`", "[", "](https://x.y)"), texts(md, Kind.MARKER))
        assertEquals(listOf("**bold**"), texts(md, Kind.STRONG))
        assertEquals(listOf("link"), texts(md, Kind.LINK))
        assertEquals(1, ranges(md, Kind.HEADING).single().level)
    }

    // The user's notes are Logseq-style: tab-indented bullets with fenced code nested inside bullets.
    @Test fun logseqOutlineWithNestedCode() {
        val md = "# Components\n\t- ```jsx\n\t  const a = 1;\n\t  ```\n\t- Main idea\n\t\t- child\n"
        assertEquals(listOf("-", "-", "-"), texts(md, Kind.BULLET))
        assertEquals(listOf(0, 0, 1), ranges(md, Kind.BULLET).map { it.level })
        val code = ranges(md, Kind.CODE_BLOCK).single()
        assertEquals("jsx", code.data)
        assertTrue(md.substring(code.start, code.end).contains("const a = 1;"))
        assertEquals(listOf("```", "```"), texts(md, Kind.MARKER).filter { it.startsWith("`") })
        // The hanging indent is measured from the exact original prefix, including the tab the parser never saw.
        assertEquals(listOf("\t- ", "\t- ", "\t\t- "), ranges(md, Kind.LIST_ITEM).map { it.data })
    }

    // The Logseq dedent must only apply to bullet runs; ordinary indented code stays code.
    @Test fun indentedCodeIsNotDedented() {
        val md = "Para\n\n    val x = 1\n"
        assertEquals(listOf("    val x = 1"), texts(md, Kind.CODE_BLOCK))
        assertTrue(ranges(md, Kind.BULLET).isEmpty())
    }

    @Test fun tasks() {
        val md = "- [ ] todo\n- [x] done\n"
        assertEquals(listOf(0, 1), ranges(md, Kind.TASK).map { it.level })
        assertEquals(listOf(" done"), texts(md, Kind.TASK_DONE))
        assertEquals(listOf("- [ ] ", "- [x] "), ranges(md, Kind.LIST_ITEM).map { it.data })
    }

    // Matches the shape of "Plan treningowy.md": Polish text, 3 columns, delimiter row collapsed.
    @Test fun table() {
        val md = "| Dzień | Trening |\n|---|---|\n| Wtorek | Całe ciało (**FBW**) |\n"
        val rows = ranges(md, Kind.TABLE_ROW)
        assertEquals(listOf(1, 0), rows.map { it.level })
        assertEquals("6:6,7:16", rows.first().data)
        assertEquals(listOf("|---|---|"), texts(md, Kind.TABLE_DELIMITER))
        assertEquals(listOf(0 until md.length - 1), StyleBuilder.build(md).blocks)
        // No hidden markers inside table rows: the row span draws cells itself.
        assertTrue(ranges(md, Kind.MARKER).isEmpty())
    }

    @Test fun splitRowHonorsEscapesAndCode() {
        assertEquals(listOf("a", "b|c", "`x|y`"), splitTableRow("| a | b\\|c | `x|y` |"))
        assertEquals("Całe ciało (FBW) link", stripInline("Całe ciało (**FBW**) [link](u)"))
    }

    // Typing must only restyle the edited area: everything outside the window is reused as-is.
    @Test fun changedWindowCoversOnlyTheEdit() {
        val before = "# A\n\n**one** two\n\n- three\n- four\n"
        val after = "# A\n\n**one** twoX\n\n- three\n- four\n"
        val w = changedWindow(StyleBuilder.build(before).ranges, StyleBuilder.build(after).ranges, 1)
        assertNull(w) // plain typing inside a paragraph changes no styles
        val bold = "# A\n\n**one** **two**\n\n- three\n- four\n"
        val w2 = changedWindow(StyleBuilder.build(before).ranges, StyleBuilder.build(bold).ranges, 4)!!
        assertEquals("**two**", bold.substring(w2.first, w2.last + 1))
    }

    @Test fun foldHeadingStopsAtSameLevel() {
        val md = "# A\ntext\n## B\nmore\n\n# C\n"
        val r = OutlineOps.foldRegion(md, 0)!!
        assertEquals("text\n## B\nmore", md.substring(r.first, r.last + 1))
    }

    @Test fun foldBulletTakesDeeperLines() {
        val md = "- a\n\t- b\n\n\t- c\n- d\n"
        val r = OutlineOps.foldRegion(md, 1)!!
        assertEquals("\t- b\n\n\t- c", md.substring(r.first, r.last + 1))
        assertNull(OutlineOps.foldRegion(md, md.indexOf("- d")))
    }
}
