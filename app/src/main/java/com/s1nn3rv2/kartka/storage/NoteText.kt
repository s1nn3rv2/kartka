package com.s1nn3rv2.kartka.storage

/**
 * How a note's bytes looked on disk, so saving writes them back the same way: the app edits with plain "\n" line
 * breaks, and a file that used a byte-order mark or Windows "\r\n" line breaks keeps them.
 */
data class TextFormat(val bom: Boolean = false, val crlf: Boolean = false)

object NoteText {
    private const val BOM = '﻿'

    fun decode(raw: String): Pair<String, TextFormat> {
        val bom = raw.startsWith(BOM)
        val body = if (bom) raw.substring(1) else raw
        // Only convert when every line break is "\r\n"; mixed files keep their bytes as they are.
        val crlf = body.contains("\r\n") && body.replace("\r\n", "").none { it == '\n' }
        return (if (crlf) body.replace("\r\n", "\n") else body) to TextFormat(bom, crlf)
    }

    fun encode(text: String, format: TextFormat): String {
        val body = if (format.crlf) text.replace("\n", "\r\n") else text
        return if (format.bom) BOM + body else body
    }
}

/** File and folder naming rules shared by scanning, creating and renaming. */
object PathRules {
    /** Characters that are illegal in file names on Windows or Linux; removed so Syncthing never trips on a name. */
    private val illegal = Regex("""[/\\:*?"<>|\x00-\x1F]""")

    /** Only `.md` files show up; hidden files (Syncthing temp files, dotfiles) never do. */
    fun isNote(name: String) = name.endsWith(".md", ignoreCase = true) && !name.startsWith(".")

    /** Hidden folders (.stfolder, .stversions, .trash, .scratch, .obsidian, .git) are skipped when listing notes. */
    fun isListedFolder(name: String) = !name.startsWith(".")

    fun title(fileName: String) = fileName.substring(0, fileName.length - 3)

    /** A safe file name for [title], or null when nothing usable is left. */
    fun fileName(title: String): String? {
        val clean = illegal.replace(title, "").trim().trimEnd('.')
        return if (clean.isEmpty() || clean.startsWith(".")) null else "$clean.md"
    }

    /** "name.md", or "name 2.md", "name 3.md" … whichever does not exist yet. */
    fun unique(fileName: String, exists: (String) -> Boolean): String {
        if (!exists(fileName)) return fileName
        val base = fileName.removeSuffix(".md")
        return generateSequence(2) { it + 1 }.map { "$base $it.md" }.first { !exists(it) }
    }
}
