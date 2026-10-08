package com.s1nn3rv2.kartka.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocksTest {
    // The user's notes are Logseq-style: tab bullets under a heading must be a list, not one big code block.
    @Test fun logseqOutline() {
        val md = "# Components\n\t- ```jsx\n\t  const a = 1;\n\t  ```\n\t- Main idea\n\t\t- child **bold**\n"
        val b = BlockBuilder.build(md)
        assertEquals(MdHeading(1, Inline("Components"), 0), b[0])
        assertEquals(MdCode("const a = 1;", "jsx", 1), b[2])
        val child = b[4] as MdListItem
        assertEquals(1, child.indent)
        assertEquals(Inline("child bold", listOf(InlineSpan(6, 10, InlineStyle.STRONG))), child.text)
    }

    // Logseq stores fold state as a property line; it must not show as text, and the item starts folded.
    @Test fun logseqPropertiesAreHidden() {
        val md = "- Why CSS in head?\n  collapsed:: true\n\t- because\n"
        val item = BlockBuilder.build(md)[0] as MdListItem
        assertEquals("Why CSS in head?", item.text.text)
        assertTrue(item.collapsed)
    }

    // Tapping a checkbox edits the source: the offset must point at the character between the brackets.
    @Test fun taskOffsetsPointIntoTheSource() {
        val md = "# T\n\t- [ ] todo\n\t- [x] done\n"
        val tasks = BlockBuilder.build(md).filterIsInstance<MdListItem>().map { it.task!! }
        assertEquals(listOf(false, true), tasks.map { it.checked })
        assertEquals(listOf(' ', 'x'), tasks.map { md[it.offset] })
    }

    @Test fun tableColumns() {
        val md = "| Dzień | Trening |\n|---|---|\n| Wtorek | Całe ciało (**FBW**) |\n"
        val t = BlockBuilder.build(md).single() as MdTable
        assertEquals("Całe ciało (FBW)", t.rows[0][1].text)
        assertEquals(listOf(6 to 6, 7 to 16), t.columns)
    }

    // Wiki links, tags and highlights become their own styled spans; tags inside URLs stay part of the link.
    @Test fun notesInlines() {
        val p = BlockBuilder.build("See [[Work/Ideas|my ideas]], #todo and ==this== at https://x.com/#frag\n").single() as MdParagraph
        val styles = p.text.spans.map { it.style to p.text.text.substring(it.start, it.end) }
        assertEquals(
            listOf(
                InlineStyle.WIKILINK to "my ideas", InlineStyle.TAG to "#todo", InlineStyle.HIGHLIGHT to "this",
                InlineStyle.LINK to "https://x.com/#frag",
            ),
            styles,
        )
        assertEquals("Work/Ideas", p.text.spans[0].url)
    }

    // Obsidian callouts: the [!type] line becomes a header; the quote's text carries the type for its tint.
    @Test fun callouts() {
        val b = BlockBuilder.build("> [!warning] Watch out\n> Hot pan.\n\n> plain quote\n")
        assertEquals(MdCallout("warning", "Watch out", 0), b[0])
        assertEquals(MdParagraph(Inline("Hot pan."), 0, "warning"), b[1])
        assertEquals("", (b[2] as MdParagraph).quote)
        assertEquals("Note", (BlockBuilder.build("> [!note]\n> body\n")[0] as MdCallout).title)
    }

    @Test fun foldBulletHidesDeeperBlocks() {
        val b = BlockBuilder.build("- a\n\t- b\n\n\t  more\n- c\n")
        val a = b.indexOfFirst { (it as? MdListItem)?.text?.text == "a" }
        assertTrue(canFold(b, a))
        assertEquals(listOf("a", "c"), visibleBlocks(b, setOf(a)).map { (b[it] as MdListItem).text.text })
    }

    @Test fun foldHeadingStopsAtSameLevel() {
        val b = BlockBuilder.build("# A\ntext\n## B\nmore\n# C\n")
        assertEquals(listOf(0, 4), visibleBlocks(b, setOf(0)))
        assertFalse(canFold(b, 4))
    }

    // References are numbered by first use (not by label) and the definition keeps the label for jumping to it.
    @Test fun footnotes() {
        val blocks = BlockBuilder.build("Text[^b] more[^a] again[^b].\n\n[^a]: Alpha.\n[^b]: Beta.\n")
        val p = blocks[0] as MdParagraph
        assertEquals("Text1 more2 again1.", p.text.text)
        assertEquals(listOf("b", "a", "b"), p.text.spans.filter { it.style == InlineStyle.FOOTNOTE }.map { it.url })
        val defs = blocks.filterIsInstance<MdFootnote>()
        assertEquals(listOf("2" to "Alpha.", "1" to "Beta."), defs.map { it.number to it.text.text })
    }

    // Only a local image alone in its paragraph becomes a picture; web images (no internet) and inline ones stay text.
    @Test fun images() {
        val blocks = BlockBuilder.build("![cat](../attachments/c%20a.jpg)\n\n![web](https://x.y/a.png)\n\nSee ![i](a.png) here\n")
        assertEquals(MdImage("../attachments/c%20a.jpg", "cat", 0), blocks[0])
        assertTrue(blocks[1] is MdParagraph)
        assertTrue(blocks[2] is MdParagraph)
    }
}
