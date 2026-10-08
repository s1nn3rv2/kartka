package com.s1nn3rv2.kartka.index

import com.s1nn3rv2.kartka.storage.NoteMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private fun note(path: String, text: String, modified: Long = 0) = IndexedNote(NoteMeta(path, modified, text.length.toLong()), text)

    private val notes = listOf(
        note("- Składniki na ciasto (ok. 4 naleśniki).md", "mąka, mleko, jajka #przepis"),
        note("Plan treningowy.md", "Przysiad z hantlem, wyciskanie #trening #zdrowie/silownia"),
        note("React.md", "Components and hooks. A plan for learning."),
    )

    // Polish notes: searching without accents must still find them, and normalizing must not shift positions.
    @Test fun accentInsensitive() {
        assertEquals("- Składniki na ciasto (ok. 4 naleśniki)", search(notes, "skladniki nalesniki").single().meta.title)
        assertEquals("zazolc gesla jazn", normalize("Zażółć gęślą jaźń"))
        assertEquals("Zażółć gęślą jaźń".length, normalize("Zażółć gęślą jaźń").length)
    }

    @Test fun titleMatchesRankAboveContent() {
        assertEquals(listOf("Plan treningowy", "React"), search(notes, "plan").map { it.meta.title })
    }

    @Test fun everyWordMustMatch() {
        assertTrue(search(notes, "plan hantlem").single().meta.title == "Plan treningowy")
        assertTrue(search(notes, "plan banana").isEmpty())
    }

    @Test fun tagFilterIncludesNestedTags() {
        assertEquals(listOf("Plan treningowy"), search(notes, "tag:zdrowie").map { it.meta.title })
        assertEquals(setOf("trening", "zdrowie/silownia"), tags("# Heading\n#trening and #zdrowie/silownia, not a#b or #123"))
    }

    // [[links]] must find notes the way Obsidian does: by name, case-insensitive, preferring the same folder.
    @Test fun resolvesWikiLinks() {
        val metas = listOf(NoteMeta("Ideas.md", 0, 0), NoteMeta("Work/Ideas.md", 0, 0), NoteMeta("Recipes/Pancakes.md", 0, 0))
        assertEquals("Ideas.md", resolveLink(metas, "ideas", fromFolder = ""))
        assertEquals("Work/Ideas.md", resolveLink(metas, "Ideas", fromFolder = "Work"))
        assertEquals("Work/Ideas.md", resolveLink(metas, "Work/Ideas#Next steps", fromFolder = ""))
        assertEquals("Recipes/Pancakes.md", resolveLink(metas, "Pancakes.md", fromFolder = ""))
        assertEquals(null, resolveLink(metas, "Missing", fromFolder = ""))
        assertEquals(setOf("ideas", "pancakes"), wikiLinks("See [[Work/Ideas|my ideas]] and [[Pancakes#Batter]]"))
    }

    @Test fun snippetHighlightsTheMatch() {
        val r = search(notes, "hooks").single()
        assertTrue(r.highlights.isNotEmpty())
        assertEquals("hooks", r.snippet.substring(r.highlights[0]))
    }

    // Typing #tag in the search bar lists the notes with that tag, already while the tag is half typed,
    // and shows each note around where the tag is.
    @Test fun hashSearchesTags() {
        assertEquals(listOf("Plan treningowy"), search(notes, "#zdrowie").map { it.meta.title })
        assertEquals(listOf("Plan treningowy"), search(notes, "#zdr").map { it.meta.title })
        val r = search(notes, "#zdrowie").single()
        assertTrue(r.snippet.substring(r.highlights[0]).startsWith("#zdrowie"))
        assertTrue(search(notes, "#nosuchtag").isEmpty())
    }
}
