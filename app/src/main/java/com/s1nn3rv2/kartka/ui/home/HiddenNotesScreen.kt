package com.s1nn3rv2.kartka.ui.home

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialShapes
import com.s1nn3rv2.kartka.ui.EmptyState
import android.text.format.DateUtils
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.s1nn3rv2.kartka.Notes
import com.s1nn3rv2.kartka.storage.NoteMeta
import com.s1nn3rv2.kartka.storage.NotesRepository
import kotlinx.coroutines.launch

/**
 * The trash or the scratch folder: hidden folders inside the notes folder that the normal lists skip. Trash offers
 * restore and permanent deletion (always confirmed); scratch offers Keep (move to the default folder) and opens notes.
 * Both are emptied of notes older than 30 days on startup.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HiddenNotesScreen(notes: Notes, trash: Boolean, onOpenNote: (String) -> Unit, onBack: () -> Unit, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val folder = if (trash) NotesRepository.TRASH else NotesRepository.SCRATCH
    // Reloads whenever the notes change (including our own restores and deletions).
    val state by notes.state.collectAsStateWithLifecycle()
    val items by produceState(emptyList<NoteMeta>(), state) { value = notes.hidden(folder) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (trash) "Trash" else "Scratch") },
                subtitle = { Text(if (trash) "Deleted notes, kept for 30 days" else "Throwaway notes, kept for 30 days") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trash && items.isNotEmpty()) {
                        TextButton(onClick = {
                            confirm = "Delete all ${items.size} notes in the trash for good?" to { scope.launch { notes.emptyTrash() } }
                        }) { Text("Empty") }
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
            if (items.isEmpty()) {
                item {
                    if (trash) {
                        EmptyState(Icons.Outlined.Delete, "The trash is empty.", MaterialShapes.SoftBurst)
                    } else {
                        EmptyState(Icons.Outlined.Bolt, "No scratch notes. Start one from the + button on the home screen.", MaterialShapes.Sunny)
                    }
                }
            }
            itemsIndexed(items, key = { _, m -> m.path }) { i, meta ->
                val original = meta.path.removePrefix("$folder/")
                SegmentedListItem(
                    onClick = { if (!trash) onOpenNote(meta.path) },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = items.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    overlineContent = original.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1) } },
                    supportingContent = {
                        Text(DateUtils.getRelativeTimeSpanString(meta.modified).toString(), style = MaterialTheme.typography.labelMedium)
                    },
                    trailingContent = {
                        Row {
                            if (trash) {
                                IconButton(onClick = { scope.launch { notes.restore(meta.path) } }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore")
                                }
                            } else {
                                IconButton(onClick = { scope.launch { notes.move(meta.path, notes.defaultFolder) } }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Outlined.PushPin, contentDescription = "Keep")
                                }
                            }
                            IconButton(
                                onClick = { confirm = "Delete “${meta.title}” for good?" to { scope.launch { notes.deleteForever(meta.path) } } },
                                shapes = IconButtonDefaults.shapes(),
                            ) { Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete for good") }
                        }
                    },
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                ) { Text(meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }

    confirm?.let { (question, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete for good?") },
            text = { Text(question) },
            confirmButton = {
                TextButton(onClick = {
                    action()
                    confirm = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
