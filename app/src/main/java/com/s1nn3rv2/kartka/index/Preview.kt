package com.s1nn3rv2.kartka.index

import com.s1nn3rv2.kartka.SortOrder
import com.s1nn3rv2.kartka.storage.NoteMeta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val lineMarker = Regex("""^\s*(#{1,6}\s+|[-*+]\s+(\[[ xX]]\s+)?|>\s*|\[!\w+][+-]?\s*|\d{1,9}[.)]\s+)*""")
private val property = Regex("""^\s*[\w-]+::""")
private val inlineMarks = Regex("""\*\*|__|~~|==|`|\[\[(?:[^]|\n]*\|)?([^]\n]*)]]|!?\[([^]]*)]\([^)]*\)|\[\^[^]\s]*](?::\s*)?""")

/** A one-glance preview of a note: its first text lines without markdown symbols, code, math blocks or Logseq properties. */
fun preview(text: String, max: Int = 160): String {
    val sb = StringBuilder()
    var inCode = false
    for (raw in text.lineSequence()) {
        if (raw.trimStart().startsWith("```") || raw.trim() == "$$") {
            inCode = !inCode
            continue
        }
        if (inCode || raw.isBlank() || property.containsMatchIn(raw)) continue
        val line = inlineMarks.replace(lineMarker.replace(raw, "")) { it.groupValues[1] + it.groupValues[2] }.trim()
        if (line.isEmpty()) continue
        if (sb.isNotEmpty()) sb.append(" · ")
        sb.append(line)
        if (sb.length >= max) break
    }
    return if (sb.length > max) sb.substring(0, max).trimEnd() + "…" else sb.toString()
}

enum class Age(val label: String) { TODAY("Today"), YESTERDAY("Yesterday"), WEEK("This week"), MONTH("This month"), EARLIER("Earlier") }

fun age(modified: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Age {
    val day = Instant.ofEpochMilli(modified).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> Age.TODAY
        days == 1L -> Age.YESTERDAY
        days < 7 -> Age.WEEK
        days < 30 -> Age.MONTH
        else -> Age.EARLIER
    }
}

// Accent-free and case-free first (Łódź sorts with L, Żaba with Z on any phone language), then exact title.
private val byName: Comparator<NoteMeta> = compareBy({ normalize(it.title) }, { it.title })

/**
 * Pinned notes first, then the rest: grouped by age when sorted by last edit (notes arrive newest first), or in one
 * alphabetical group. [restLabel] names the rest instead of age groups (folder screens).
 */
fun groupNotes(all: List<NoteMeta>, pins: Set<String>, sort: SortOrder, restLabel: String?): List<Pair<String, List<NoteMeta>>> {
    val (pinned, rest) = all.partition { it.path in pins }
    fun ordered(items: List<NoteMeta>) = if (sort == SortOrder.NAME) items.sortedWith(byName) else items
    return buildList {
        if (pinned.isNotEmpty()) add("Pinned" to ordered(pinned))
        when {
            rest.isEmpty() -> Unit
            restLabel != null -> add(restLabel to ordered(rest))
            sort == SortOrder.NAME -> add("All notes" to ordered(rest))
            else -> rest.groupBy { age(it.modified) }.toSortedMap().forEach { (age, items) -> add(age.label to items) }
        }
    }
}
