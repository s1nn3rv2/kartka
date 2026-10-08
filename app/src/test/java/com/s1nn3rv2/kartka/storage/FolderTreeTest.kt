package com.s1nn3rv2.kartka.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTreeTest {
    @Test fun nestsAndCounts() {
        val tree = folderTree(
            listOf("", "daily", "Recipes", "Recipes/cakes", "archive"),
            listOf(NoteMeta("a.md", 0, 0), NoteMeta("Recipes/b.md", 0, 0), NoteMeta("Recipes/cakes/c.md", 0, 0)),
        )
        assertEquals(1, tree.notes)
        assertEquals(listOf("archive", "daily", "Recipes"), tree.children.map { it.name })
        val recipes = tree.children[2]
        assertEquals(1, recipes.notes)
        assertEquals(listOf("Recipes/cakes"), recipes.children.map { it.path })
    }
}
