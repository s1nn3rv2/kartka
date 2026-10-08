package com.s1nn3rv2.kartka.storage

/** A folder for the sidebar: [path] is relative ("" is the notes folder), [notes] counts notes directly inside. */
data class FolderNode(val path: String, val name: String, val notes: Int, val children: List<FolderNode>)

/** Builds the folder tree from flat relative paths, sorted by name, ignoring case. */
fun folderTree(folders: List<String>, notes: List<NoteMeta>): FolderNode {
    val counts = notes.groupingBy { it.folder }.eachCount()
    val byParent = folders.filter { it.isNotEmpty() }.groupBy { it.substringBeforeLast('/', "") }
    fun node(path: String): FolderNode = FolderNode(
        path = path,
        name = path.substringAfterLast('/'),
        notes = counts[path] ?: 0,
        children = byParent[path].orEmpty().sortedBy { it.lowercase() }.map(::node),
    )
    return node("")
}
