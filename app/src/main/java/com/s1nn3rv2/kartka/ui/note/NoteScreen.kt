package com.s1nn3rv2.kartka.ui.note

import com.s1nn3rv2.kartka.markdown.Edits
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.s1nn3rv2.kartka.KartkaApp
import com.s1nn3rv2.kartka.editor.EditorColors
import com.s1nn3rv2.kartka.editor.MarkdownEditText
import com.s1nn3rv2.kartka.markdown.BlockBuilder
import com.s1nn3rv2.kartka.markdown.MdBlock
import com.s1nn3rv2.kartka.markdown.InlineStyle
import com.s1nn3rv2.kartka.markdown.MdFootnote
import com.s1nn3rv2.kartka.markdown.MdHeading
import com.s1nn3rv2.kartka.markdown.MdListItem
import com.s1nn3rv2.kartka.markdown.foldEnd
import com.s1nn3rv2.kartka.markdown.visibleBlocks
import com.s1nn3rv2.kartka.storage.NoteMeta
import com.s1nn3rv2.kartka.storage.PathRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    app: KartkaApp,
    path: String,
    isNew: Boolean,
    onBack: () -> Unit,
    onTrashed: (trashPath: String) -> Unit,
    onOpenNote: (path: String) -> Unit,
    onOpenWikiLink: (target: String, from: String) -> Unit,
    onOpenTag: (tag: String) -> Unit,
) {
    // Opened notes arrive already read and parsed, so the first frame shows the content (no blank flash).
    val prepared = remember(path) { app.notes.takePrepared(path) }
    val session = remember(path) { NoteSession(app.notes, path, app.scope, prepared?.loaded) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session) { if (prepared == null) session.load() }
    // Leaving the note (back, predictive back, process going away) saves whatever is pending.
    DisposableEffect(session) {
        onDispose {
            session.saveNow()
            if (isNew) session.discardIfEmpty()
        }
    }
    LifecycleResumeEffect(session) {
        scope.launch { session.reloadIfChangedOnDisk() }
        onPauseOrDispose { session.saveNow() }
    }

    val text = session.text
    var editing by rememberSaveable { mutableStateOf(isNew) }
    val blocks by produceState(prepared?.blocks, text) {
        if (text != null && text !== prepared?.loaded?.text) value = withContext(Dispatchers.Default) { BlockBuilder.build(text) }
    }
    // Null until the blocks arrive; then seeded from Logseq's collapsed:: true. Reset when the text changes shape.
    var folded by remember(path) { mutableStateOf(prepared?.blocks?.let(::initialFolds)) }
    val listState = rememberLazyListState()
    var showOutline by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showBacklinks by remember { mutableStateOf(false) }
    LaunchedEffect(blocks) {
        val b = blocks ?: return@LaunchedEffect
        if (folded == null || folded!!.any { it >= b.size }) {
            folded = initialFolds(b)
        }
    }

    // The editor is built hidden shortly after the note opens: laying out a long note takes ~100 ms of main-thread
    // time, which would otherwise land on the Edit tap. Edit and Done then only switch which layer is on top.
    var editor by remember(path) { mutableStateOf<MarkdownEditText?>(null) }
    var canUndo by remember(path) { mutableStateOf(false) }
    var canRedo by remember(path) { mutableStateOf(false) }
    // Also right away when Edit mode was restored (activity recreated mid-edit), or the screen would be blank.
    var prepareEditor by remember(path) { mutableStateOf(isNew || editing) }
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
    // New untitled notes start with an empty title field (blank keeps the date-time file name); the title is the file name.
    // Scratch notes keep the date-time name and start in the body: what you type matters more than a title there.
    val untitled = isNew && !session.isScratch && autoName.matches(path.substringAfterLast('/'))
    val title = rememberTextFieldState(if (untitled) "" else PathRules.title(path.substringAfterLast('/')))
    val titleFocus = remember { FocusRequester() }
    val context = LocalContext.current
    fun focusEditor() {
        editor?.post {
            editor?.requestFocus()
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    LaunchedEffect(Unit) { if (untitled) titleFocus.requestFocus() }
    LaunchedEffect(editing, editor) { if (editing && !untitled) focusEditor() }

    // The title becomes the file name as soon as you leave the title field, so menus and sheets see the new name.
    fun commitTitle() = session.rename(title.text.toString().trim())
    fun finishEditing() {
        // The editor stays alive under View mode, so it must give up focus or the keyboard stays open.
        editor?.let { e ->
            e.clearFocus()
            context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(e.windowToken, 0)
        }
        session.saveNow()
        commitTitle()
        editing = false
    }
    BackHandler(editing) { finishEditing() }
    val leave = {
        if (editing) finishEditing()
        onBack()
    }

    // Photos are copied into the attachments folder and linked at the cursor.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { app.notes.attach(uri, session.path)?.let { path -> editor?.applyEdit { Edits.image(it, path) } } }
    }

    // Unfolds anything hiding block [index], then scrolls it to the top.
    fun reveal(b: List<MdBlock>, index: Int) {
        val open = folded.orEmpty().filterTo(HashSet()) { it >= index || foldEnd(b, it) <= index }
        folded = open
        val position = visibleBlocks(b, open).indexOf(index)
        if (position >= 0) scope.launch { listState.animateScrollToItem(position) }
    }

    if (showOutline) {
        val b = blocks.orEmpty()
        val headings = remember(b) { b.mapIndexedNotNull { i, block -> (block as? MdHeading)?.let { i to it } } }
        OutlineSheet(
            headings,
            onPick = { index ->
                showOutline = false
                if (editing) {
                    // Headings appear in the same order in the text, so the n-th block heading is the n-th heading range.
                    val n = headings.indexOfFirst { it.first == index }
                    editor?.headings()?.getOrNull(n)?.let { editor?.jumpTo(it.first) }
                } else {
                    reveal(b, index)
                }
            },
            onDismiss = { showOutline = false },
        )
    }

    if (showBacklinks) {
        val linking = remember(session.path) { app.notes.backlinks(session.path) }
        NoteListSheet(
            title = "Backlinks",
            empty = "No notes link here yet. Link to this note with [[${PathRules.title(session.path.substringAfterLast('/'))}]].",
            notes = linking,
            onPick = {
                showBacklinks = false
                onOpenNote(it)
            },
            onDismiss = { showBacklinks = false },
        )
    }

    if (showMove) {
        val folders by app.notes.state.collectAsStateWithLifecycle()
        MoveDialog(
            folders = folders.folders,
            current = session.path.substringBeforeLast('/', ""),
            onPick = { folder ->
                showMove = false
                session.move(folder)
            },
            onDismiss = { showMove = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (editing) {
                            TitleField(title, titleFocus, onNext = ::focusEditor, onLeave = ::commitTitle)
                        } else {
                            Text(PathRules.title(session.path.substringAfterLast('/')), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val folder = if (session.isScratch) "Scratch" else session.path.substringBeforeLast('/', "")
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
                    if (blocks?.any { it is MdHeading } == true) {
                        IconButton(onClick = { commitTitle(); showOutline = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Outline")
                        }
                    }
                    Box {
                        IconButton(onClick = { commitTitle(); showMenu = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (session.isScratch) {
                                DropdownMenuItem(
                                    text = { Text("Keep") },
                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        session.move(app.notes.defaultFolder)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Backlinks") },
                                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showBacklinks = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to folder") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showMove = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    app.scope.launch { session.trash()?.let(onTrashed) }
                                },
                            )
                        }
                    }
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
                                    e.onHistoryChanged = {
                                        canUndo = e.canUndo
                                        canRedo = e.canRedo
                                    }
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
                    // The scroll view can draw past its box (into the navigation bar area), where the View layer
                    // doesn't cover it; clip it so the hidden editor never shows through.
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                )
            }
            val b = blocks
            val f = folded
            if (!editing && b != null && f != null) {
                MarkdownView(
                    blocks = b,
                    folded = f,
                    onToggleFold = { i -> folded = if (i in f) f - i else f + i },
                    onLink = { span ->
                        when (span.style) {
                            InlineStyle.WIKILINK -> onOpenWikiLink(span.url.orEmpty(), session.path)
                            InlineStyle.TAG -> onOpenTag(span.url.orEmpty())
                            InlineStyle.FOOTNOTE -> b.indexOfFirst { it is MdFootnote && it.label == span.url }.takeIf { it >= 0 }?.let { reveal(b, it) }
                            else -> Unit
                        }
                    },
                    imageFile = { url -> app.notes.imageFile(session.path, url) },
                    onToggleTask = { task ->
                        text?.let { t ->
                            if (task.offset in t.indices) {
                                session.replace(t.replaceRange(task.offset, task.offset + 1, if (task.checked) " " else "x"))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    state = listState,
                )
            }
            val e = editor
            if (editing && e != null) {
                EditToolbar(
                    e, canUndo, canRedo,
                    onImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Folds a note opens with: items Logseq marked `collapsed:: true`. */
private fun initialFolds(blocks: List<MdBlock>) = blocks.indices.filter { (blocks[it] as? MdListItem)?.collapsed == true }.toSet()

/** The note's title, edited in place in the top bar; it becomes the file name when editing ends. */
/** A sheet listing notes (backlinks); tapping one opens it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteListSheet(title: String, empty: String, notes: List<NoteMeta>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        if (notes.isEmpty()) {
            Text(
                empty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            itemsIndexed(notes, key = { _, m -> m.path }) { i, meta ->
                SegmentedListItem(
                    onClick = { onPick(meta.path) },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = notes.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    overlineContent = meta.folder.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1) } },
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                ) { Text(meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Picks the folder to move a note into. */
@Composable
private fun MoveDialog(folders: List<String>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            LazyColumn {
                items(folders.filter { it != current }, key = { it }) { folder ->
                    ListItem(
                        onClick = { onPick(folder) },
                        leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    ) { Text(folder.ifEmpty { "Notes folder" }) }
                }
            }
        },
        confirmButton = { },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The headings of the note in order; tapping one jumps to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlineSheet(headings: List<Pair<Int, MdHeading>>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Outline",
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(headings, key = { it.first }) { (index, heading) ->
                Text(
                    heading.text.text,
                    style = if (heading.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(index) }
                        .padding(start = 24.dp + 16.dp * (heading.level - 1), end = 24.dp, top = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TitleField(state: TextFieldState, focus: FocusRequester, onNext: () -> Unit, onLeave: () -> Unit) {
    val style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        state = state,
        textStyle = style,
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        onKeyboardAction = { onNext() },
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (!it.isFocused) onLeave() },
        decorator = { field ->
            if (state.text.isEmpty()) Text("Title", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            field()
        },
    )
}

/** The date-time file name that NotesRepository.create gives notes without a title. */
private val autoName = Regex("""\d{4}-\d{2}-\d{2}[T ]\d{2}-\d{2}(-\d{2})?( \d+)?\.md""")

@Composable
private fun editorColors(): EditorColors {
    val scheme = MaterialTheme.colorScheme
    return EditorColors(
        text = scheme.onSurface.toArgb(),
        dim = scheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb(),
        primary = scheme.primary.toArgb(),
        secondary = scheme.secondary.toArgb(),
        tertiary = scheme.tertiary.toArgb(),
        codeBackground = scheme.surfaceContainerHigh.toArgb(),
        inlineCodeBackground = scheme.surfaceContainerHighest.toArgb(),
        highlight = scheme.tertiaryContainer.toArgb(),
        onHighlight = scheme.onTertiaryContainer.toArgb(),
    )
}
