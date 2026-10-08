package com.s1nn3rv2.kartka.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A note on disk. [path] is relative to the notes folder, with "/" separators. */
data class NoteMeta(val path: String, val modified: Long, val size: Long) {
    val title get() = PathRules.title(path.substringAfterLast('/'))
    val folder get() = path.substringBeforeLast('/', "")
}

data class LoadedNote(val text: String, val format: TextFormat, val modified: Long, val size: Long)

/**
 * The notes folder is the only source of truth: this class reads and writes plain files with java.io and keeps no
 * database. Every call blocks; callers run it on Dispatchers.IO.
 */
class NotesRepository(val root: File) {
    fun file(path: String) = File(root, path)

    /** Every note, plus every listed folder (relative paths, "" is the root). */
    fun scan(): Pair<List<NoteMeta>, List<String>> {
        val notes = ArrayList<NoteMeta>()
        val folders = ArrayList<String>()
        fun walk(dir: File, rel: String) {
            val children = dir.listFiles() ?: return
            folders += rel
            for (f in children) {
                val childRel = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                if (f.isDirectory) {
                    if (PathRules.isListedFolder(f.name)) walk(f, childRel)
                } else if (PathRules.isNote(f.name)) {
                    notes += NoteMeta(childRel, f.lastModified(), f.length())
                }
            }
        }
        walk(root, "")
        return notes to folders
    }

    fun read(path: String): LoadedNote {
        val f = file(path)
        val (text, format) = NoteText.decode(f.readText())
        return LoadedNote(text, format, f.lastModified(), f.length())
    }

    /**
     * Writes atomically: a hidden temp file in the same folder is moved over the note in one step, so a crash or
     * a sync mid-write never leaves a half-written note.
     */
    fun write(path: String, text: String, format: TextFormat): NoteMeta {
        val f = file(path)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, ".${f.name}.tmp")
        tmp.writeText(NoteText.encode(text, format))
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return NoteMeta(path, f.lastModified(), f.length())
    }

    /** Creates an empty note named after [title] (or the current date and time) in [folder]; returns its path. */
    fun create(folder: String, title: String): String {
        val dir = file(folder).apply { mkdirs() }
        val name = PathRules.unique(PathRules.fileName(title) ?: "${java.time.LocalDateTime.now().toString().take(19).replace(':', '-')}.md") {
            File(dir, it).exists()
        }
        File(dir, name).createNewFile()
        return join(folder, name)
    }

    /** Renames the note in place; returns the new path (unchanged when the title is unusable or the same). */
    fun rename(path: String, title: String): String {
        val name = PathRules.fileName(title) ?: return path
        if (name == path.substringAfterLast('/')) return path
        val folder = path.substringBeforeLast('/', "")
        val target = PathRules.unique(name) { file(join(folder, it)).exists() }
        return moveTo(path, join(folder, target))
    }

    /** Moves the note into [folder], keeping its name (numbered if taken); returns the new path. */
    fun move(path: String, folder: String): String {
        val name = PathRules.unique(path.substringAfterLast('/')) { file(join(folder, it)).exists() }
        return moveTo(path, join(folder, name))
    }

    /** Moves the note to `.trash/<its path>`, so restoring is just moving it back. */
    fun trash(path: String): String = moveTo(path, uniquePath("$TRASH/$path"))

    /** Moves a trashed note back to where it came from (numbered if that name is taken again). */
    fun restore(trashPath: String): String = moveTo(trashPath, uniquePath(trashPath.removePrefix("$TRASH/")))

    fun delete(path: String) = file(path).delete()

    /** Notes inside a hidden app folder ([TRASH] or [SCRATCH]), newest first. */
    fun listHidden(folder: String): List<NoteMeta> =
        file(folder).walkTopDown().filter { it.isFile && PathRules.isNote(it.name) }
            .map { NoteMeta(it.relativeTo(root).invariantSeparatorsPath, it.lastModified(), it.length()) }
            .sortedByDescending { it.modified }.toList()

    /** Deletes notes in [folder] last modified before [cutoff]; returns how many. */
    fun purge(folder: String, cutoff: Long): Int =
        listHidden(folder).count { it.modified < cutoff && delete(it.path) }

    private fun moveTo(from: String, to: String): String {
        val target = file(to)
        target.parentFile?.mkdirs()
        Files.move(file(from).toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return to
    }

    private fun uniquePath(path: String): String {
        val folder = path.substringBeforeLast('/', "")
        return join(folder, PathRules.unique(path.substringAfterLast('/')) { file(join(folder, it)).exists() })
    }

    private fun join(folder: String, name: String) = if (folder.isEmpty()) name else "$folder/$name"

    companion object {
        const val TRASH = ".trash"
        const val SCRATCH = ".scratch"
    }
}
