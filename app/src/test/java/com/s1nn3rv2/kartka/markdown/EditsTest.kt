package com.s1nn3rv2.kartka.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {
    /** "|" marks the cursor, "[" and "]" the selection, so tests read like the text on screen. */
    private fun e(s: String): Edit {
        val start = s.indexOfFirst { it == '|' || it == '[' }
        val t = s.replace("|", "").replace("[", "").replace("]", "")
        return if (s.contains('|')) Edit(t, start) else Edit(t, start, s.indexOf(']') - 1)
    }

    private fun show(x: Edit) =
        if (x.start == x.end) x.text.substring(0, x.start) + "|" + x.text.substring(x.start)
        else x.text.substring(0, x.start) + "[" + x.text.substring(x.start, x.end) + "]" + x.text.substring(x.end)

    @Test fun boldWrapsAndUnwraps() {
        assertEquals("a **[word]** b", show(Edits.toggleWrap(e("a [word] b"), "**")))
        assertEquals("a [word] b", show(Edits.toggleWrap(e("a **[word]** b"), "**")))
        assertEquals("a **|** b", show(Edits.toggleWrap(e("a | b"), "**")))
    }

    @Test fun headingCycles() {
        assertEquals("# Ti|tle", show(Edits.cycleHeading(e("Ti|tle"))))
        assertEquals("## Ti|tle", show(Edits.cycleHeading(e("# Ti|tle"))))
        assertEquals("Ti|tle", show(Edits.cycleHeading(e("### Ti|tle"))))
    }

    // Toggling must keep the user's tab indentation (their notes are tab-indented Logseq outlines).
    @Test fun bulletsAndTasksKeepIndent() {
        assertEquals("\t- it|em", show(Edits.toggleBullet(e("\tit|em"))))
        assertEquals("\tit|em", show(Edits.toggleBullet(e("\t- it|em"))))
        assertEquals("\t- [ ] it|em", show(Edits.toggleTask(e("\t- it|em"))))
        assertEquals("- [one\n- two]", show(Edits.toggleBullet(e("[one\ntwo]"))))
    }

    @Test fun indentUsesTheNotesStyle() {
        assertEquals("\t- a\n\t\t- b|", show(Edits.indent(e("\t- a\n\t- b|"))))
        assertEquals("- a\n  - b|", show(Edits.indent(e("- a\n- b|"))))
        assertEquals("\t- a\n\t- b|", show(Edits.outdent(e("\t- a\n\t\t- b|"))))
    }

    // Moving a bullet carries its children and never jumps out of its parent.
    @Test fun moveCarriesChildren() {
        val md = "- a\n\t- a1\n- b|\n\t- b1\n- c"
        assertEquals("- b|\n\t- b1\n- a\n\t- a1\n- c", show(Edits.move(e(md), up = true)))
        assertEquals("- a\n\t- a1\n- c\n- b|\n\t- b1", show(Edits.move(e(md), up = false)))
        val child = "- a\n\t- a|1\n- b"
        assertEquals(child, show(Edits.move(e(child), up = true)))
    }

    @Test fun enterContinuesLists() {
        assertEquals("- one\n- |", show(Edits.continueList("- one\n", 6)!!))
        assertEquals("\t- [x] done\n\t- [ ] |", show(Edits.continueList("\t- [x] done\n", 12)!!))
        assertEquals("1. a\n2. |", show(Edits.continueList("1. a\n", 5)!!))
        assertEquals("text\n|", show(Edits.continueList("text\n- \n", 8)!!))
        assertEquals("|", show(Edits.continueList("- \n", 3)!!))
        assertNull(Edits.continueList("plain\n", 6))
    }

    @Test fun linkSelectsTheUrl() {
        val r = Edits.link(Edit("see docs", 4, 8))
        assertEquals("see [docs](url)", r.text)
        assertEquals("url", r.text.substring(r.start, r.end))
    }

    @Test fun undoMergesTyping() {
        val u = UndoStack(mergeMs = 1000)
        u.record(0, "", "h", 0); u.record(1, "", "i", 100); u.record(2, "", "!", 5000)
        assertEquals(Triple(2, 1, ""), u.undo())
        assertEquals(Triple(0, 2, ""), u.undo())
        assertFalse(u.canUndo)
        assertTrue(u.canRedo)
        assertEquals(Triple(0, 0, "hi"), u.redo())
    }
}
