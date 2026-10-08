package com.s1nn3rv2.kartka

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.s1nn3rv2.kartka.editor.EditorColors
import com.s1nn3rv2.kartka.editor.MarkdownEditText
import com.s1nn3rv2.kartka.markdown.BlockBuilder
import com.s1nn3rv2.kartka.markdown.MdBlock
import com.s1nn3rv2.kartka.markdown.MdListItem
import com.s1nn3rv2.kartka.ui.note.MarkdownView
import com.s1nn3rv2.kartka.ui.theme.KartkaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ponytail: milestone 0 test harness (hardcoded folder, no saving); replaced by the real screens in milestone 1.
private val notesRoot = File(Environment.getExternalStorageDirectory(), "Notes")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KartkaTheme { SpikeApp() } }
    }
}

@Composable
private fun SpikeApp() {
    var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LifecycleResumeEffect(Unit) {
        granted = Environment.isExternalStorageManager()
        onPauseOrDispose { }
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    when {
        !granted -> PermissionScreen()
        open != null -> {
            BackHandler { open = null }
            NoteScreen(File(open!!), onBack = { open = null })
        }
        else -> NoteList(onOpen = { open = it.path })
    }
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Kartka needs file access to read your notes folder.", style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
                    )
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Grant access") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteList(onOpen: (File) -> Unit) {
    val notes by produceState(emptyList<File>()) {
        value = withContext(Dispatchers.IO) {
            notesRoot.walkTopDown()
                .onEnter { it == notesRoot || !it.name.startsWith(".") }
                .filter { it.isFile && it.name.endsWith(".md", ignoreCase = true) }
                .sortedByDescending { it.lastModified() }
                .toList()
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Kartka (test build)") }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            items(notes, key = { it.path }) { file ->
                ListItem(
                    headlineContent = { Text(file.nameWithoutExtension) },
                    supportingContent = { Text("${file.length() / 1024} KB · ${file.parentFile?.relativeTo(notesRoot)?.path?.ifEmpty { "/" }}") },
                    modifier = Modifier.clickable { onOpen(file) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteScreen(file: File, onBack: () -> Unit) {
    // ponytail: edits live in memory only until saving lands in milestone 2.
    var text by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file) { text = withContext(Dispatchers.IO) { file.readText() } }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<MarkdownEditText?>(null) }
    val blocks by produceState<List<MdBlock>?>(null, text) {
        value = text?.let { withContext(Dispatchers.Default) { BlockBuilder.build(it) } }
    }
    // Null until the blocks arrive; then seeded from Logseq's collapsed:: true. Reset after editing, since block
    // indices can shift.
    var folded by remember(file) { mutableStateOf<Set<Int>?>(null) }
    LaunchedEffect(blocks) {
        val b = blocks ?: return@LaunchedEffect
        if (folded == null) folded = b.indices.filter { (b[it] as? MdListItem)?.collapsed == true }.toSet()
    }
    fun finishEditing() {
        editor?.let { text = it.text.toString() }
        editor = null
        folded = null
        editing = false
    }
    BackHandler(editing) { finishEditing() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.nameWithoutExtension, maxLines = 1)
                        Text("Test build: edits are not saved", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (editing) {
                        TextButton(onClick = ::finishEditing) { Text("Done") }
                    } else {
                        TextButton(onClick = { editing = true }, enabled = text != null) { Text("Edit") }
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()
        if (editing) {
            val colors = editorColors()
            AndroidView(
                factory = { context ->
                    ScrollView(context).apply {
                        isFillViewport = true
                        addView(
                            MarkdownEditText(context, colors).also { editor = it },
                            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                        )
                    }
                },
                update = { editor?.setColors(colors) },
                modifier = content,
            )
            LaunchedEffect(editor) { editor?.let { e -> text?.let { e.load(it) } } }
        } else {
            val b = blocks
            val f = folded
            if (b != null && f != null) {
                MarkdownView(
                    blocks = b,
                    folded = f,
                    onToggleFold = { i -> folded = if (i in f) f - i else f + i },
                    onToggleTask = { task ->
                        text = text?.let { t ->
                            if (task.offset !in t.indices) t else t.replaceRange(task.offset, task.offset + 1, if (task.checked) " " else "x")
                        }
                    },
                    modifier = content,
                    contentPadding = PaddingValues(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun editorColors(): EditorColors {
    val scheme = MaterialTheme.colorScheme
    return EditorColors(
        text = scheme.onSurface.toArgb(),
        dim = scheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb(),
        primary = scheme.primary.toArgb(),
        tertiary = scheme.tertiary.toArgb(),
        codeBackground = scheme.surfaceContainerHigh.toArgb(),
        inlineCodeBackground = scheme.surfaceContainerHighest.toArgb(),
    )
}
