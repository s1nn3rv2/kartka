package com.s1nn3rv2.kartka.index

import com.s1nn3rv2.kartka.storage.NoteMeta
import java.text.Normalizer

/**
 * Lowercase without accents, so "skladniki" finds "Składniki". Removing accents keeps every other character in
 * place, which lets a match position in the normalized text index the original text directly.
 */
fun normalize(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        sb.append(
            when {
                c.code < 0x80 -> c.lowercaseChar()
                c == 'ł' || c == 'Ł' -> 'l' // Unicode does not decompose ł into l + accent
                c == 'ß' -> 's'
                else -> {
                    val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0]
                    base.lowercaseChar()
                }
            }
        )
    }
    return sb.toString()
}

private val tagPattern = Regex("""(?<![\w#&/])#([\p{L}\p{N}_/-]*[\p{L}_][\p{L}\p{N}_/-]*)""")

/** `#tags` in the text, lowercased and without the `#`. Markdown headings ("# Title") are not tags. */
fun tags(text: String): Set<String> = tagPattern.findAll(text).map { it.groupValues[1].lowercase() }.toSet()

private val wikiLinkPattern = Regex("""\[\[([^\[\]\n|#]+)""")

/** Lowercased note names a text links to with `[[…]]` (folders and `#heading` parts dropped). */
fun wikiLinks(text: String): Set<String> =
    wikiLinkPattern.findAll(text).map { it.groupValues[1].trim().substringAfterLast('/').lowercase() }.toSet()

/**
 * The note a `[[target]]` points to: matched by name, or by folder path when the target has one, ignoring case and
 * any `#heading` or `.md`. Ties prefer the linking note's own folder, then the shortest path. Null when none exists.
 */
fun resolveLink(notes: List<NoteMeta>, target: String, fromFolder: String): String? {
    val t = target.substringBefore('#').trim().removeSuffix(".md").lowercase()
    if (t.isEmpty()) return null
    return notes.filter { m -> m.path.removeSuffix(".md").lowercase().let { it == t || it.endsWith("/$t") } }
        .minWithOrNull(compareBy({ if (it.folder == fromFolder) 0 else 1 }, { it.path.length }))?.path
}

/** What the index keeps per note: the metadata plus normalized text for matching. */
class IndexedNote(val meta: NoteMeta, val text: String) {
    val normTitle = normalize(meta.title)
    val normText = normalize(text)
    val tags = tags(text)
    val links = wikiLinks(text)
}

/** A search hit. [snippet] is a piece of the note around the first match; [highlights] are ranges inside it. */
data class SearchResult(val meta: NoteMeta, val snippet: String, val highlights: List<IntRange>)

/**
 * Ranks title matches above content matches. Every word of the query must appear (in the title or the text);
 * `tag:name` words filter by tag instead.
 */
fun search(notes: Collection<IndexedNote>, query: String, limit: Int = 100): List<SearchResult> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val tagFilters = words.filter { it.startsWith("tag:", ignoreCase = true) }.map { it.drop(4).removePrefix("#").lowercase() }
    val terms = words.filterNot { it.startsWith("tag:", ignoreCase = true) }.map(::normalize)

    return notes.asSequence()
        .filter { n -> tagFilters.all { t -> n.tags.any { it == t || it.startsWith("$t/") } } }
        .mapNotNull { n ->
            if (!terms.all { n.normTitle.contains(it) || n.normText.contains(it) }) return@mapNotNull null
            val score = terms.sumOf { t ->
                when {
                    n.normTitle.startsWith(t) -> 100
                    n.normTitle.contains(t) -> 50
                    else -> 1
                }
            }
            score to n
        }
        .sortedWith(compareByDescending<Pair<Int, IndexedNote>> { it.first }.thenByDescending { it.second.meta.modified })
        .take(limit)
        .map { (_, n) -> snippet(n, terms) }
        .toList()
}

private fun snippet(n: IndexedNote, terms: List<String>): SearchResult {
    val first = terms.mapNotNull { t -> n.normText.indexOf(t).takeIf { it >= 0 } }.minOrNull()
    if (first == null) return SearchResult(n.meta, n.text.take(120).replace('\n', ' '), emptyList())
    val start = (first - 40).coerceAtLeast(0).let { s -> n.text.lastIndexOf(' ', s).takeIf { it >= 0 && first - it < 60 }?.plus(1) ?: s }
    val end = (start + 140).coerceAtMost(n.text.length)
    val snippet = n.text.substring(start, end).replace('\n', ' ').replace('\t', ' ') // 1:1, so highlights stay aligned
    val norm = n.normText.substring(start, end)
    val highlights = terms.flatMap { t ->
        if (t.isEmpty()) emptyList() else generateSequence(norm.indexOf(t).takeIf { it >= 0 }) { i -> norm.indexOf(t, i + t.length).takeIf { it >= 0 } }
            .map { it until it + t.length }.toList()
    }
    return SearchResult(n.meta, (if (start > 0) "…" else "") + snippet, highlights.map { if (start > 0) (it.first + 1)..(it.last + 1) else it })
}
