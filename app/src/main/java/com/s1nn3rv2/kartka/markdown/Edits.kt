package com.s1nn3rv2.kartka.markdown

/** Text plus a selection (start <= end; equal means a cursor). The toolbar's operations map one to another. */
data class Edit(val text: String, val start: Int, val end: Int = start)

/** Formatting operations for the Edit-mode toolbar. Pure functions, so they are unit-tested. */
object Edits {
    private fun lineStart(t: String, i: Int) = t.lastIndexOf('\n', (i - 1).coerceAtLeast(-1)).let { if (i == 0) 0 else it + 1 }
    private fun lineEnd(t: String, i: Int) = t.indexOf('\n', i).let { if (it < 0) t.length else it }
    private fun indentOf(line: String) = line.takeWhile { it == ' ' || it == '\t' }
    private fun width(indent: String) = indent.sumOf { c -> if (c == '\t') 4 else 1 }

    /** The note's indentation unit: a tab if any line starts with one (the user's notes do), otherwise two spaces. */
    fun indentUnit(t: String) = if (t.lineSequence().any { it.startsWith('\t') }) "\t" else "  "

    /** Wraps the selection in [marker] (or inserts an empty pair at the cursor); unwraps if already wrapped. */
    fun toggleWrap(e: Edit, marker: String): Edit {
        val t = e.text
        val m = marker.length
        val before = t.substring(maxOf(0, e.start - m), e.start)
        val after = t.substring(e.end, minOf(t.length, e.end + m))
        if (before == marker && after == marker) {
            return Edit(t.removeRange(e.end, e.end + m).removeRange(e.start - m, e.start), e.start - m, e.end - m)
        }
        val inner = t.substring(e.start, e.end)
        if (inner.length >= 2 * m && inner.startsWith(marker) && inner.endsWith(marker)) {
            return Edit(t.replaceRange(e.start, e.end, inner.substring(m, inner.length - m)), e.start, e.end - 2 * m)
        }
        return Edit(t.replaceRange(e.start, e.end, marker + inner + marker), e.start + m, e.end + m)
    }

    /** Applies [change] to every line touched by the selection; the selection follows the edited text. */
    private fun mapLines(e: Edit, change: (String) -> String): Edit {
        val t = e.text
        val from = lineStart(t, e.start)
        val to = lineEnd(t, e.end)
        val lines = t.substring(from, to).split('\n')
        val changed = lines.map(change)
        val replaced = changed.joinToString("\n")
        val firstDelta = changed.first().length - lines.first().length
        val totalDelta = replaced.length - (to - from)
        return Edit(
            t.replaceRange(from, to, replaced),
            (e.start + firstDelta).coerceAtLeast(from),
            (e.end + totalDelta).coerceAtLeast(from),
        )
    }

    private val listMarker = Regex("""^([ \t]*)([-*+]|\d{1,9}[.)])[ \t]+(\[[ xX]][ \t]+)?""")
    private val headingMarker = Regex("""^#{1,6}[ \t]+""")

    /** No heading → # → ## → ### → no heading, for each selected line. */
    fun cycleHeading(e: Edit): Edit = mapLines(e) { line ->
        val current = headingMarker.find(line)?.value
        val level = current?.trim()?.length ?: 0
        val body = if (current != null) line.substring(current.length) else line
        if (level >= 3) body else "#".repeat(level + 1) + " " + body
    }

    /** Turns lines into bullets, or bullets back into plain lines. Checkboxes become plain bullets first. */
    fun toggleBullet(e: Edit): Edit = toggleListPrefix(e, "- ")

    /** Turns lines into `- [ ]` tasks, or tasks back into plain lines. */
    fun toggleTask(e: Edit): Edit = toggleListPrefix(e, "- [ ] ")

    private fun toggleListPrefix(e: Edit, prefix: String): Edit {
        val t = e.text
        val lines = t.substring(lineStart(t, e.start), lineEnd(t, e.end)).split('\n')
        val isTask = prefix.contains('[')
        val allHave = lines.filter { it.isNotBlank() }.all { l ->
            listMarker.find(l)?.let { m -> (m.groupValues[3].isNotEmpty()) == isTask } == true
        }
        return mapLines(e) { line ->
            if (line.isBlank()) return@mapLines line
            val indent = indentOf(line)
            val body = listMarker.find(line)?.let { line.substring(it.value.length) } ?: line.substring(indent.length)
            if (allHave) indent + body else indent + prefix + body
        }
    }

    fun indent(e: Edit): Edit {
        val unit = indentUnit(e.text)
        return mapLines(e) { if (it.isBlank()) it else unit + it }
    }

    fun outdent(e: Edit): Edit {
        val unit = indentUnit(e.text)
        return mapLines(e) { line ->
            when {
                line.startsWith('\t') -> line.substring(1)
                line.startsWith(unit) -> line.substring(unit.length)
                line.startsWith(' ') -> line.trimStart(' ').let { trimmed -> line.substring(minOf(4, line.length - trimmed.length)) }
                else -> line
            }
        }
    }

    /** The current line plus every following line indented deeper (its children, blank lines inside included). */
    private fun block(t: String, at: Int): IntRange {
        val start = lineStart(t, at)
        val base = width(indentOf(t.substring(start, lineEnd(t, start))))
        var end = lineEnd(t, start)
        var i = end + 1
        while (i <= t.length) {
            val le = lineEnd(t, i)
            val line = t.substring(i, le)
            if (line.isNotBlank() && width(indentOf(line)) <= base) break
            if (line.isNotBlank()) end = le
            if (le >= t.length) break
            i = le + 1
        }
        return start until end
    }

    /** Swaps the bullet at the cursor (with its children) with the sibling above or below it. */
    fun move(e: Edit, up: Boolean): Edit {
        val t = e.text
        val cur = block(t, e.start)
        val base = width(indentOf(t.substring(cur.first, lineEnd(t, cur.first))))
        val other: IntRange = if (up) {
            if (cur.first == 0) return e
            // The sibling above starts at the nearest earlier line indented exactly as much as this one.
            var i = lineStart(t, cur.first - 1)
            while (true) {
                val line = t.substring(i, lineEnd(t, i))
                if (line.isNotBlank()) {
                    val w = width(indentOf(line))
                    if (w < base) return e // reached the parent: nothing to swap with
                    if (w == base) break
                }
                if (i == 0) return e
                i = lineStart(t, i - 1)
            }
            block(t, i)
        } else {
            val next = cur.last + 2
            if (next > t.length) return e
            val line = t.substring(next, lineEnd(t, next))
            if (line.isBlank() || width(indentOf(line)) != base) return e
            block(t, next)
        }
        val (a, b) = if (up) other to cur else cur to other
        val first = t.substring(a.first, a.last + 1)
        val second = t.substring(b.first, b.last + 1)
        val between = t.substring(a.last + 1, b.first)
        val swapped = t.substring(0, a.first) + second + between + first + t.substring(b.last + 1)
        // The cursor keeps its place inside the moved block.
        val offsetInBlock = e.start - cur.first
        val selLen = e.end - e.start
        val newStart = if (up) a.first + offsetInBlock else a.first + second.length + between.length + offsetInBlock
        return Edit(swapped, newStart, newStart + selLen)
    }

    /**
     * After Enter was typed at [cursor] (just past the new "\n"): continues the list item above with the same
     * indent and marker (numbers count up, checkboxes start unchecked), or, if that item was empty, removes its
     * marker to end the list. Returns null when the previous line is not a list item.
     */
    fun continueList(text: String, cursor: Int): Edit? {
        if (cursor == 0 || text[cursor - 1] != '\n') return null
        val prevStart = lineStart(text, cursor - 1)
        val prev = text.substring(prevStart, cursor - 1)
        val m = listMarker.find(prev) ?: return null
        if (prev.length == m.value.length) {
            // Empty item: Enter ends the list instead of adding another empty bullet.
            return Edit(text.removeRange(prevStart, cursor), prevStart)
        }
        val indent = m.groupValues[1]
        val marker = m.groupValues[2].let { mk ->
            mk.dropLast(1).toIntOrNull()?.let { n -> "${n + 1}${mk.last()}" } ?: mk
        }
        val insert = indent + marker + " " + (if (m.groupValues[3].isNotEmpty()) "[ ] " else "")
        return Edit(text.substring(0, cursor) + insert + text.substring(cursor), cursor + insert.length)
    }

    /** Inserts a markdown link around the selection (or a placeholder) and selects the URL part to type over. */
    fun link(e: Edit): Edit {
        val label = e.text.substring(e.start, e.end).ifEmpty { "text" }
        val inserted = "[$label](url)"
        val urlStart = e.start + label.length + 3
        return Edit(e.text.replaceRange(e.start, e.end, inserted), urlStart, urlStart + 3)
    }
}

/**
 * Undo history for the editor. Typing within [mergeMs] of the previous change, at the position where it ended,
 * merges into one step, so undo removes words rather than single letters. A line break always starts a new step.
 */
class UndoStack(private val mergeMs: Long = 1000, private val limit: Int = 200) {
    private data class Change(val start: Int, val removed: String, val inserted: String, val time: Long)

    private val undo = ArrayDeque<Change>()
    private val redo = ArrayDeque<Change>()

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun record(start: Int, removed: String, inserted: String, time: Long) {
        if (removed == inserted) return
        redo.clear()
        val last = undo.lastOrNull()
        if (last != null && time - last.time < mergeMs && removed.isEmpty() && last.removed.isEmpty() &&
            start == last.start + last.inserted.length && !inserted.contains('\n') && !last.inserted.contains('\n')
        ) {
            undo[undo.lastIndex] = last.copy(inserted = last.inserted + inserted, time = time)
            return
        }
        undo.addLast(Change(start, removed, inserted, time))
        if (undo.size > limit) undo.removeFirst()
    }

    /** The replacement to apply to undo the last step: (start, length to remove, text to insert). */
    fun undo(): Triple<Int, Int, String>? = undo.removeLastOrNull()?.also { redo.addLast(it) }?.let {
        Triple(it.start, it.inserted.length, it.removed)
    }

    fun redo(): Triple<Int, Int, String>? = redo.removeLastOrNull()?.also { undo.addLast(it) }?.let {
        Triple(it.start, it.removed.length, it.inserted)
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}
