package com.s1nn3rv2.kartka.index

import com.s1nn3rv2.kartka.storage.NoteMeta
import com.s1nn3rv2.kartka.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PreviewTest {
    // Previews must read like text: no #, -, [ ], code or Logseq properties, which is what the user's notes are full of.
    @Test fun stripsMarkdown() {
        val md = "# HTML\ncollapsed:: true\n\t- Describe **the** `<script>` tag\n\t  ```js\n\t  code()\n\t  ```\n- [x] done [link](http://x)\n"
        assertEquals("HTML · Describe the <script> tag · done link", preview(md))
    }

    // Previews show what View mode shows: link labels, not link targets or callout syntax.
    @Test fun wikiLinksAndCallouts() {
        assertEquals("See my plan and Other · Careful", preview("See [[Plan|my plan]] and [[Other]]\n> [!warning] Careful\n"))
    }

    @Test fun footnotesAndMathBlocks() {
        assertEquals("Claim it · Note.", preview("Claim[^1] it\n\$\$\nx^2\n\$\$\n[^1]: Note.\n"))
    }

    @Test fun truncates() {
        assertEquals("aaaaa…", preview("a".repeat(20), max = 5))
    }

    @Test fun ages() {
        val today = LocalDate.of(2026, 10, 8)
        fun at(d: LocalDate) = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(Age.TODAY, age(at(today), today, ZoneOffset.UTC))
        assertEquals(Age.YESTERDAY, age(at(today.minusDays(1)), today, ZoneOffset.UTC))
        assertEquals(Age.WEEK, age(at(today.minusDays(6)), today, ZoneOffset.UTC))
        assertEquals(Age.MONTH, age(at(today.minusDays(20)), today, ZoneOffset.UTC))
        assertEquals(Age.EARLIER, age(at(today.minusDays(40)), today, ZoneOffset.UTC))
    }

    // Pinned notes always come first; the rest follow the chosen order, with Polish letters sorted where they belong.
    @Test fun groupsPinnedFirst() {
        val now = System.currentTimeMillis()
        val notes = listOf("Żaba.md", "Ala.md", "Łódź.md", "Zebra.md").mapIndexed { i, p -> NoteMeta(p, now - i, 0) }
        val byName = groupNotes(notes, pins = setOf("Zebra.md"), SortOrder.NAME, restLabel = null)
        assertEquals(listOf("Pinned" to listOf("Zebra"), "All notes" to listOf("Ala", "Łódź", "Żaba")), byName.map { (l, n) -> l to n.map { it.title } })
        val byEdit = groupNotes(notes, pins = emptySet(), SortOrder.EDITED, restLabel = "Notes")
        assertEquals(listOf("Notes" to listOf("Żaba", "Ala", "Łódź", "Zebra")), byEdit.map { (l, n) -> l to n.map { it.title } })
    }

    // Markdown symbols aren't words; Polish words and contractions count once.
    @Test fun countsWords() {
        assertEquals(5, textStats("# Zażółć\n- **gęślą** jaźń, it's [[x]]").words)
        assertEquals(2, textStats("word ".repeat(400)).readingMinutes)
    }
}
