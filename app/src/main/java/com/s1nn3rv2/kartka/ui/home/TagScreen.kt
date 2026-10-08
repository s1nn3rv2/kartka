package com.s1nn3rv2.kartka.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.s1nn3rv2.kartka.Notes
import com.s1nn3rv2.kartka.index.SearchResult
import com.s1nn3rv2.kartka.index.preview

/** Every note carrying `#tag` (nested tags like `#tag/sub` included), opened by tapping a tag in a note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScreen(notes: Notes, tag: String, onOpenNote: (String) -> Unit, onBack: () -> Unit) {
    val state by notes.state.collectAsStateWithLifecycle()
    val results by produceState(emptyList<SearchResult>(), state.indexVersion) { value = notes.search("tag:$tag") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("#$tag", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(if (results.size == 1) "1 note" else "${results.size} notes") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val dir = LocalLayoutDirection.current
        LazyColumn(
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(dir) + 16.dp,
                end = padding.calculateEndPadding(dir) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            itemsIndexed(results, key = { _, r -> r.meta.path }) { i, r ->
                SegmentedListItem(
                    onClick = { onOpenNote(r.meta.path) },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = results.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    overlineContent = r.meta.folder.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1) } },
                    supportingContent = { Text(notes.indexed(r.meta.path)?.text?.let { preview(it) }.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                ) { Text(r.meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
