package com.s1nn3rv2.kartka.index

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
}
