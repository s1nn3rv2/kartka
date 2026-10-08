package com.s1nn3rv2.kartka

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    val scheme = MaterialTheme.colorScheme
    val colors = EditorColors(
        text = scheme.onSurface.toArgb(),
        dim = scheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb(),
        primary = scheme.primary.toArgb(),
        onPrimary = scheme.onPrimary.toArgb(),
        outline = scheme.outline.toArgb(),
        outlineVariant = scheme.outlineVariant.toArgb(),
        codeBackground = scheme.surfaceContainerHigh.toArgb(),
        quoteBackground = scheme.surfaceContainer.toArgb(),
        inlineCodeBackground = scheme.surfaceContainerHighest.toArgb(),
        tableHeaderBackground = scheme.secondaryContainer.toArgb(),
    )
    var editor by remember { mutableStateOf<MarkdownEditText?>(null) }
    LaunchedEffect(editor, file) {
        val e = editor ?: return@LaunchedEffect
        e.load(withContext(Dispatchers.IO) { file.readText() })
    }
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
                actions = { TextButton(onClick = { editor?.toggleFold() }) { Text("Fold") } },
            )
        },
    ) { padding ->
        AndroidView(
            factory = { MarkdownEditText(it, colors).also { view -> editor = view } },
            update = { it.setColors(colors) },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize(),
        )
    }
}
