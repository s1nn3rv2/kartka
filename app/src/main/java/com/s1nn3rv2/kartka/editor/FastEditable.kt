package com.s1nn3rv2.kartka.editor

import android.text.Editable
import android.text.NoCopySpan
import android.text.SpanWatcher
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher

/**
 * A text buffer that keeps typing fast in long notes with thousands of spans.
 *
 * When one character is typed, every span after the cursor shifts by one. SpannableStringBuilder then, for each
 * shifted span, looks up all span watchers and notifies them; the text view invalidates and the layout re-lays out
 * per span (over a second per keystroke in a 75 KB note). A pure shift changes nothing visible: the edit itself
 * already re-lays out and redraws the paragraph that changed. So while an edit is in progress:
 * - the watcher lookup returns a list cached at the start of the edit, and
 * - span-moved notifications for our own [PassiveSpan]s are not passed to the text view and its layout.
 * Selection, composing and all other spans, and every add or remove, still go through.
 */
class FastEditable(text: CharSequence) : SpannableStringBuilder(text) {
    private var editing = 0
    private var watchers: Array<SpanWatcher> = emptyArray()
    private val wrappers = HashMap<Any, FilteringWatcher>()

    override fun replace(start: Int, end: Int, tb: CharSequence, tbstart: Int, tbend: Int): SpannableStringBuilder {
        if (editing++ == 0) watchers = super.getSpans(0, length, SpanWatcher::class.java)
        try {
            return super.replace(start, end, tb, tbstart, tbend)
        } finally {
            editing--
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSpans(queryStart: Int, queryEnd: Int, kind: Class<T>?): Array<T> =
        if (editing > 0 && kind == SpanWatcher::class.java) watchers as Array<T> else super.getSpans(queryStart, queryEnd, kind)

    override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
        val span = if (what != null && what.javaClass.name in FILTERED_WATCHERS) {
            wrappers.getOrPut(what) { FilteringWatcher(what as TextWatcher) }
        } else {
            what
        }
        super.setSpan(span, start, end, flags)
    }

    override fun removeSpan(what: Any?) {
        super.removeSpan(wrappers.remove(what) ?: what)
    }

    private inner class FilteringWatcher(private val inner: TextWatcher) : TextWatcher, SpanWatcher, NoCopySpan {
        private val spans = inner as SpanWatcher
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = inner.beforeTextChanged(s, start, count, after)
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = inner.onTextChanged(s, start, before, count)
        override fun afterTextChanged(s: Editable?) = inner.afterTextChanged(s)
        override fun onSpanAdded(text: Spannable?, what: Any?, start: Int, end: Int) = spans.onSpanAdded(text, what, start, end)
        override fun onSpanRemoved(text: Spannable?, what: Any?, start: Int, end: Int) = spans.onSpanRemoved(text, what, start, end)
        override fun onSpanChanged(text: Spannable?, what: Any?, ostart: Int, oend: Int, nstart: Int, nend: Int) {
            if (editing == 0 || what !is PassiveSpan) spans.onSpanChanged(text, what, ostart, oend, nstart, nend)
        }
    }

    private companion object {
        val FILTERED_WATCHERS = setOf("android.text.DynamicLayout\$ChangeWatcher", "android.widget.TextView\$ChangeWatcher")
    }
}
