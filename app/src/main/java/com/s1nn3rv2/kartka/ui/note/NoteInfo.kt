package com.s1nn3rv2.kartka.ui.note

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s1nn3rv2.kartka.Notes
import com.s1nn3rv2.kartka.index.TextStats
import com.s1nn3rv2.kartka.index.textStats
import com.s1nn3rv2.kartka.storage.NotesRepository
import com.s1nn3rv2.kartka.storage.PathRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Counts and file details of the open note, from the note menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteInfoSheet(path: String, text: String, notes: Notes, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val stats by produceState<TextStats?>(null, text) { value = withContext(Dispatchers.Default) { textStats(text) } }
    val file = remember(path) { notes.repository?.file(path) }
    val folder = when {
        path.startsWith(NotesRepository.SCRATCH + "/") -> "Scratch"
        else -> path.substringBeforeLast('/', "").ifEmpty { "Notes folder" }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                PathRules.title(path.substringAfterLast('/')),
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val s = stats
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Words", s?.words?.toString() ?: "…", Modifier.weight(1f))
                Stat("Characters", s?.characters?.toString() ?: "…", Modifier.weight(1f))
                Stat("Reading", s?.let { if (it.readingMinutes < 1) "< 1 min" else "${it.readingMinutes} min" } ?: "…", Modifier.weight(1f))
            }
            Detail("Folder", folder)
            Detail("File", path.substringAfterLast('/'))
            file?.let { f ->
                val details by produceState<Pair<Long, Long>?>(null, f, text) { value = withContext(Dispatchers.IO) { f.lastModified() to f.length() } }
                details?.let { (modified, size) ->
                    Detail("Last edited", DateUtils.formatDateTime(context, modified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR))
                    Detail("Size", Formatter.formatShortFileSize(context, size))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
}
