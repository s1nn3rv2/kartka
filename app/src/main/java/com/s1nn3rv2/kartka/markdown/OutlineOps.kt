package com.s1nn3rv2.kartka.markdown

/** Pure text helpers for outline editing. Offsets index into the given string. */
object OutlineOps {
    private val headingPrefix = Regex("^(#{1,6})[ \t]")

    fun lineStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    fun lineEnd(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    private fun indentCols(text: CharSequence, start: Int): Int {
        var cols = 0
        var i = start
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) {
            cols += if (text[i] == '\t') 4 - cols % 4 else 1
            i++
        }
        return cols
    }

    private fun isBlank(text: CharSequence, start: Int, end: Int) = (start until end).all { text[it] == ' ' || text[it] == '\t' }

    /**
     * The region a fold on the line containing [offset] hides: for a heading, everything until the next heading of the
     * same or higher level; for any other line, the following lines indented deeper (blank lines inside count).
     * Returns null when there is nothing to fold. The range runs from the start of the next line to the end of the
     * last hidden line, excluding its newline.
     */
    fun foldRegion(text: CharSequence, offset: Int): IntRange? {
        val start = lineStart(text, offset)
        val end = lineEnd(text, offset)
        if (end >= text.length) return null
        val line = text.subSequence(start, end).toString()
        val heading = headingPrefix.find(line)?.groupValues?.get(1)?.length
        var lastEnd = -1
        var i = end + 1
        if (heading != null) {
            while (i <= text.length) {
                val le = lineEnd(text, i)
                val level = headingPrefix.find(text.subSequence(i, le))?.groupValues?.get(1)?.length
                if (level != null && level <= heading) break
                if (!isBlank(text, i, le)) lastEnd = le
                if (le >= text.length) break
                i = le + 1
            }
        } else {
            if (isBlank(text, start, end)) return null
            val base = indentCols(text, start)
            while (i <= text.length) {
                val le = lineEnd(text, i)
                if (!isBlank(text, i, le)) {
                    if (indentCols(text, i) <= base) break
                    lastEnd = le
                }
                if (le >= text.length) break
                i = le + 1
            }
        }
        // Trailing blank lines stay visible.
        return if (lastEnd < 0) null else (end + 1) until lastEnd
    }
}
