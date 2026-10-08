package com.s1nn3rv2.kartka.ui.share

import com.s1nn3rv2.kartka.LocalAppName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s1nn3rv2.kartka.Notes
import com.s1nn3rv2.kartka.storage.NoteMeta
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Text shared from another app: save it as a new note, a scratch note, or add it to the end of an existing note
 * (picked with search; recent notes are listed before anything is typed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    notes: Notes,
    text: String,
    onNewNote: () -> Unit,
    onScratch: () -> Unit,
    onAppendTo: (path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Save to ${LocalAppName.current}", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = 8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 16.dp),
            )
            if (!picking) {
                val options = listOf<Triple<String, ImageVector, () -> Unit>>(
                    Triple("New note", Icons.AutoMirrored.Outlined.NoteAdd, onNewNote),
                    Triple("Scratch note", Icons.Outlined.Bolt, onScratch),
                    Triple("Add to a note", Icons.Outlined.PostAdd) { picking = true },
                )
                options.forEachIndexed { i, (label, icon, action) ->
                    SegmentedListItem(
                        onClick = action,
                        shapes = ListItemDefaults.segmentedShapes(index = i, count = options.size),
                        colors = colors,
                        leadingContent = { Icon(icon, contentDescription = null) },
                        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                    ) { Text(label) }
                }
            } else {
                NotePicker(notes, colors, onAppendTo)
            }
        }
    }
}

@Composable
private fun NotePicker(notes: Notes, colors: ListItemColors, onPick: (String) -> Unit) {
    val query = rememberTextFieldState()
    var results by remember { mutableStateOf(notes.state.value.notes.take(20)) }
    LaunchedEffect(Unit) {
        snapshotFlow { query.text.toString() }.collectLatest { q ->
            delay(100)
            results = if (q.isBlank()) notes.state.value.notes.take(20) else notes.search(q).map { it.meta }
        }
    }
    OutlinedTextField(
        state = query,
        placeholder = { Text("Search notes") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        itemsIndexed(results, key = { _, m -> m.path }) { i, meta: NoteMeta ->
            SegmentedListItem(
                onClick = { onPick(meta.path) },
                shapes = ListItemDefaults.segmentedShapes(index = i, count = results.size),
                colors = colors,
                overlineContent = meta.folder.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1) } },
                modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
            ) { Text(meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
