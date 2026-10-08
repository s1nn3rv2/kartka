package com.s1nn3rv2.kartka.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo() = NotesRepository(tmp.root)
    private fun put(path: String, text: String) = File(tmp.root, path).apply { parentFile?.mkdirs(); writeText(text) }

    // Saving must give back the exact bytes for files written on other systems: BOM, CRLF, no final newline.
    @Test fun roundTripKeepsBytes() {
        for (raw in listOf("﻿# A\r\nline\r\n", "no newline at end", "mixed\r\nlines\nkept\r\n", "zażółć\n")) {
            val f = put("n.md", raw)
            val before = f.readBytes()
            val loaded = repo().read("n.md")
            repo().write("n.md", loaded.text, loaded.format)
            assertArrayEquals(raw, before, f.readBytes())
        }
        assertEquals("# A\nline\n", repo().read("n.md").let { put("n.md", "﻿# A\r\nline\r\n"); repo().read("n.md").text })
    }

    // Only .md files show; hidden Syncthing/app folders and temp files never do; conflict copies do (user's call).
    @Test fun scanFollowsTheRules() {
        put("a.md", ""); put("Recipes/b.MD", ""); put("x.txt", ""); put(".hidden.md", "")
        put(".stversions/old.md", ""); put(".trash/gone.md", ""); put("c.sync-conflict-20261005-170512-HTSOTRK.md", "")
        File(tmp.root, "empty").mkdirs()
        val (notes, folders) = repo().scan()
        assertEquals(listOf("Recipes/b.MD", "a.md", "c.sync-conflict-20261005-170512-HTSOTRK.md"), notes.map { it.path }.sorted())
        assertEquals(listOf("", "Recipes", "empty"), folders.sorted())
    }

    @Test fun createRenameMoveNeverOverwrite() {
        val r = repo()
        put("Plan.md", "keep me")
        val p = r.create("", "Plan")
        assertEquals("Plan 2.md", p)
        assertEquals("keep me", File(tmp.root, "Plan.md").readText())
        assertEquals("Ideas.md", r.rename(p, "Ide/as?"))
        assertEquals("Recipes/Ideas.md", r.move("Ideas.md", "Recipes"))
        assertEquals("Recipes/Ideas.md", r.rename("Recipes/Ideas.md", "  "))
    }

    // Trash keeps the folder path so restore puts the note back exactly where it was.
    @Test fun trashAndRestore() {
        val r = repo()
        put("Recipes/Pancakes.md", "x")
        val t = r.trash("Recipes/Pancakes.md")
        assertEquals(".trash/Recipes/Pancakes.md", t)
        assertFalse(File(tmp.root, "Recipes/Pancakes.md").exists())
        assertEquals("Recipes/Pancakes.md", r.restore(t))
        assertTrue(File(tmp.root, "Recipes/Pancakes.md").exists())
    }

    // A note last edited months ago must not be purged the moment it is trashed: 30 days count from deletion.
    @Test fun trashCountsFromDeletion() {
        val r = repo()
        put("old.md", "x").setLastModified(1000)
        r.trash("old.md")
        assertEquals(0, r.purge(NotesRepository.TRASH, cutoff = System.currentTimeMillis() - 60_000))
    }

    @Test fun purgeDeletesOnlyOldNotes() {
        val r = repo()
        put(".scratch/old.md", "").setLastModified(1000)
        put(".scratch/new.md", "")
        assertEquals(1, r.purge(NotesRepository.SCRATCH, cutoff = 5000))
        assertEquals(listOf(".scratch/new.md"), r.listHidden(NotesRepository.SCRATCH).map { it.path })
    }

    // Photos go to one attachments folder; links to them must work from notes at any depth, on the PC too.
    @Test fun attachments() {
        val path = repo().saveAttachment("img".byteInputStream(), "jpg")
        assertTrue(path.startsWith("attachments/") && path.endsWith(".jpg"))
        assertEquals("img", File(tmp.root, path).readText())
        assertEquals(path, PathRules.relative("", path))
        assertEquals("../../$path", PathRules.relative("Recipes/Cakes", path))
        val second = repo().saveAttachment("2".byteInputStream(), "jpg")
        assertTrue(second != path && File(tmp.root, path).readText() == "img")
    }
}
