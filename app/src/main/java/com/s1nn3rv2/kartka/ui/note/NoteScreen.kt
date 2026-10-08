package com.s1nn3rv2.kartka.ui.note

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.s1nn3rv2.kartka.KartkaApp
import com.s1nn3rv2.kartka.editor.EditorColors
import com.s1nn3rv2.kartka.editor.MarkdownEditText
import com.s1nn3rv2.kartka.markdown.BlockBuilder
import com.s1nn3rv2.kartka.markdown.MdBlock
import com.s1nn3rv2.kartka.markdown.MdListItem
import com.s1nn3rv2.kartka.storage.PathRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(app: KartkaApp, path: String, isNew: Boolean, onBack: () -> Unit) {
    val session = remember(path) { NoteSession(app.notes, path, app.scope) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session) { session.load() }
    // Leaving the note (back, predictive back, process going away) saves whatever is pending.
    DisposableEffect(session) { onDispose { session.saveNow() } }
    LifecycleResumeEffect(session) {
        scope.launch { session.reloadIfChangedOnDisk() }
        onPauseOrDispose { session.saveNow() }
    }

    val text = session.text
    var editing by rememberSaveable { mutableStateOf(isNew) }
    val blocks by produceState<List<MdBlock>?>(null, text) {
        value = text?.let { withContext(Dispatchers.Default) { BlockBuilder.build(it) } }
    }
    // Null until the blocks arrive; then seeded from Logseq's collapsed:: true. Reset when the text changes shape.
    var folded by remember(path) { mutableStateOf<Set<Int>?>(null) }
    LaunchedEffect(blocks) {
        val b = blocks ?: return@LaunchedEffect
        if (folded == null || folded!!.any { it >= b.size }) {
            folded = b.indices.filter { (b[it] as? MdListItem)?.collapsed == true }.toSet()
        }
    }

    // The editor is built hidden shortly after the note opens: laying out a long note takes ~100 ms of main-thread
    // time, which would otherwise land on the Edit tap. Edit and Done then only switch which layer is on top.
    var editor by remember(path) { mutableStateOf<MarkdownEditText?>(null) }
    var prepareEditor by remember(path) { mutableStateOf(isNew) }
    LaunchedEffect(blocks) {
        if (blocks == null) return@LaunchedEffect
        delay(700) // after the open transition, so the ~100 ms of layout can't stutter the slide
        prepareEditor = true
    }
    LaunchedEffect(editor, text) {
        val e = editor ?: return@LaunchedEffect
        val t = text ?: return@LaunchedEffect
        if (session.editorText !== t) {
            e.load(t)
            session.editorText = t
        }
    }
    // New notes start with an empty title field (blank keeps the date-time file name); the title is the file name.
    val title = rememberTextFieldState(if (isNew) "" else PathRules.title(path.substringAfterLast('/')))
    val titleFocus = remember { FocusRequester() }
    val context = LocalContext.current
    fun focusEditor() {
        editor?.post {
            editor?.requestFocus()
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    LaunchedEffect(Unit) { if (isNew) titleFocus.requestFocus() }
    LaunchedEffect(editing, editor) { if (editing && !isNew) focusEditor() }

    fun finishEditing() {
        session.saveNow()
        session.rename(title.text.toString().trim())
        editing = false
    }
    BackHandler(editing) { finishEditing() }
    val leave = {
        if (editing) finishEditing()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (editing) {
                            TitleField(title, titleFocus, onNext = ::focusEditor)
                        } else {
                            Text(PathRules.title(session.path.substringAfterLast('/')), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val folder = session.path.substringBeforeLast('/', "")
                        if (folder.isNotEmpty()) {
                            Text(folder, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = leave, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            if (editing) {
                                finishEditing()
                            } else {
                                prepareEditor = true
                                editing = true
                            }
                        },
                        enabled = text != null,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        if (editing) Icon(Icons.Filled.Check, contentDescription = "Done") else Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            if (prepareEditor) {
                val colors = editorColors()
                AndroidView(
                    factory = { context ->
                        ScrollView(context).apply {
                            isFillViewport = true
                            addView(
                                MarkdownEditText(context, colors).also { e ->
                                    e.onEdited = { session.editorChanged { e.text.toString() } }
                                    editor = e
                                },
                                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                            )
                        }
                    },
                    // Drawn underneath the View layer while reading, so its layout and drawing are ready before Edit
                    // is tapped; it just can't take focus or be reached by screen readers until then.
                    update = { scroll ->
                        scroll.descendantFocusability =
                            if (editing) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        scroll.importantForAccessibility =
                            if (editing) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        editor?.setColors(colors)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val b = blocks
            val f = folded
            if (!editing && b != null && f != null) {
                MarkdownView(
                    blocks = b,
                    folded = f,
                    onToggleFold = { i -> folded = if (i in f) f - i else f + i },
                    onToggleTask = { task ->
                        text?.let { t ->
                            if (task.offset in t.indices) {
                                session.replace(t.replaceRange(task.offset, task.offset + 1, if (task.checked) " " else "x"))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(bottom = 24.dp),
                )
            }
        }
    }
}

/** The note's title, edited in place in the top bar; it becomes the file name when editing ends. */
@Composable
private fun TitleField(state: TextFieldState, focus: FocusRequester, onNext: () -> Unit) {
    val style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        state = state,
        textStyle = style,
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        onKeyboardAction = { onNext() },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorator = { field ->
            if (state.text.isEmpty()) Text("Title", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            field()
        },
    )
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
