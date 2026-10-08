package com.s1nn3rv2.kartka.ui.note

import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Info
import com.s1nn3rv2.kartka.ui.MenuAction
import com.s1nn3rv2.kartka.ui.GroupedMenu
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Share
import android.content.Intent
import com.s1nn3rv2.kartka.index.normalize
import com.s1nn3rv2.kartka.Notes
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import com.s1nn3rv2.kartka.markdown.foldKeys
import com.s1nn3rv2.kartka.markdown.foldsFromKeys
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
    routePath: String,
    isNew: Boolean,
    onBack: () -> Unit,
    onTrashed: (trashPath: String) -> Unit,
    onOpenNote: (path: String) -> Unit,
    onOpenWikiLink: (target: String, from: String) -> Unit,
    onOpenTag: (tag: String) -> Unit,
) {
    // A note renamed while it was open (screen rebuilt later, e.g. on coming back to it) is found under its new name.
    val path = remember(routePath) { app.notes.currentPath(routePath) }
    // Opened notes arrive already read and parsed, so the first frame shows the content (no blank flash).
    val prepared = remember(path) { app.notes.takePrepared(path) }
    val session = remember(path) { NoteSession(app.notes, path, app.scope, prepared?.loaded) }
    val textScale = app.settings.textSize.state.collectAsStateWithLifecycle().value.scale
    val scope = rememberCoroutineScope()
    LaunchedEffect(session) { if (prepared == null && !session.load()) onBack() }
    // Leaving the note (back, predictive back, process going away) saves whatever is pending.
    DisposableEffect(session) {
        onDispose {
            session.saveNow(final = true)
            if (isNew) session.discardIfEmpty()
        }
    }
    // Changes from other devices show up while the note is open, not only when coming back to the app.
    LaunchedEffect(session) {
        app.notes.state.map { s -> s.notes.find { it.path == session.path }?.modified }.distinctUntilChanged()
            .collect { session.reloadIfChangedOnDisk() }
    }
    LifecycleResumeEffect(session) {
        scope.launch { session.reloadIfChangedOnDisk() }
        // Final: with a conflict open, the edits are also copied to "(my edits)" in case Android ends the app meanwhile.
        onPauseOrDispose { session.saveNow(final = true) }
    }

    val text = session.text
    var editing by rememberSaveable { mutableStateOf(isNew) }
    val blocks by produceState(prepared?.blocks, text) {
        if (text != null && text !== prepared?.loaded?.text) value = withContext(Dispatchers.Default) { BlockBuilder.build(text) }
    }
    // Folds are remembered per note by the folded blocks' text, so they survive edits elsewhere and reopening. Until
    // they're changed in the app, a note opens with Logseq's collapsed:: true items folded. Null until blocks arrive.
    var foldKeys by remember(path) { mutableStateOf(app.notes.folds(path)) }
    val folded = remember(blocks, foldKeys) { blocks?.let { b -> foldKeys?.let { foldsFromKeys(b, it) } ?: initialFolds(b) } }
    fun setFolded(b: List<MdBlock>, indices: Set<Int>) {
        val all = foldKeys(b)
        val keys = indices.mapNotNullTo(HashSet()) { all[it] }
        foldKeys = keys
        app.notes.setFolds(session.path, keys)
    }
    val listState = rememberLazyListState()
    var showOutline by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showBacklinks by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    // Set while the cursor is after an unfinished `[[`: where the name starts and what's typed so far.
    var linkQuery by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // The editor is built hidden shortly after the note opens: laying out a long note takes ~100 ms of main-thread
    // time, which would otherwise land on the Edit tap. Edit and Done then only switch which layer is on top.
    var editor by remember(path) { mutableStateOf<MarkdownEditText?>(null) }
    var canUndo by remember(path) { mutableStateOf(false) }
    var canRedo by remember(path) { mutableStateOf(false) }
    // Also right away when Edit mode was restored (activity recreated mid-edit), or the screen would be blank.
    var prepareEditor by remember(path) { mutableStateOf(isNew || editing) }
    LaunchedEffect(blocks) {
        if (blocks == null) return@LaunchedEffect
        // After the open transition, and only once the note has been still for a moment: laying out a long note
        // takes ~60 ms on the main thread (75 KB), which would stutter a scroll or fling in progress.
        delay(700)
        while (true) {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            delay(300)
            if (!listState.isScrollInProgress) break
        }
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
        setFolded(b, open)
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

    if (showInfo) {
        NoteInfoSheet(session.path, editor?.takeIf { editing }?.text?.toString() ?: text.orEmpty(), app.notes) { showInfo = false }
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
                        val pinned = session.path in app.notes.pins.collectAsStateWithLifecycle().value
                        fun close(then: () -> Unit) = { showMenu = false; then() }
                        GroupedMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            groups = listOf(
                                listOfNotNull(
                                    if (session.isScratch) MenuAction("Keep", Icons.Outlined.Bookmark, onClick = close { session.move(app.notes.defaultFolder) }) else null,
                                    MenuAction(if (pinned) "Unpin" else "Pin", if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        onClick = close { app.notes.setPinned(listOf(session.path), !pinned) }),
                                    MenuAction("Share", Icons.Outlined.Share, onClick = close {
                                        val body = if (editing) editor?.text?.toString() else session.text
                                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                            .putExtra(Intent.EXTRA_SUBJECT, PathRules.title(session.path.substringAfterLast('/')))
                                            .putExtra(Intent.EXTRA_TEXT, body.orEmpty())
                                        context.startActivity(Intent.createChooser(send, null))
                                    }),
                                ),
                                listOf(
                                    MenuAction("Backlinks", Icons.Outlined.Link, onClick = close { showBacklinks = true }),
                                    MenuAction("Note info", Icons.Outlined.Info, onClick = close { showInfo = true }),
                                ),
                                listOf(
                                    MenuAction("Move to folder", Icons.AutoMirrored.Outlined.DriveFileMove, onClick = close { showMove = true }),
                                    MenuAction("Delete", Icons.Outlined.Delete, danger = true, onClick = close {
                                        app.scope.launch { session.trash()?.let(onTrashed) }
                                    }),
                                ),
                            ),
                        )
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
                        enabled = text != null && (editing || !session.readOnly),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        if (editing) Icon(Icons.Filled.Check, contentDescription = "Done") else Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            if (session.conflict) ConflictBanner(session::useTheirs, session::keepMine)
            when {
                session.readOnly -> Notice("This file isn't UTF-8 text. It's shown read-only here, so saving can't damage its letters.")
                session.saveFailed -> Notice("Couldn't save this note (is the storage full?). Your text is kept and saved with the next change.")
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (prepareEditor) {
                    val colors = editorColors()
                    AndroidView(
                        factory = { context ->
                            ScrollView(context).apply {
                                isFillViewport = true
                                addView(
                                    MarkdownEditText(context, colors).also { e ->
                                        e.onEdited = { session.editorChanged { e.text.toString() } }
                                        e.onCursorMoved = { cursor -> linkQuery = if (cursor < 0) null else Edits.linkQuery(e.text, cursor) }
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
                            editor?.scale = textScale
                        },
                        // The scroll view can draw past its box (into the navigation bar area), where the View layer
                        // doesn't cover it; clip it so the hidden editor never shows through.
                        modifier = Modifier.fillMaxSize().clipToBounds(),
                    )
                }
                val b = blocks
                val f = folded
                if (!editing && b != null && f != null) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
                        MarkdownView(
                            blocks = b,
                            folded = f,
                            onToggleFold = { i -> setFolded(b, if (i in f) f - i else f + i) },
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
                                    // Only ever a checkbox character: the blocks can be a moment behind the text.
                                    if (!session.readOnly && task.offset in t.indices && t[task.offset] in " xX") {
                                        val mark = if (task.checked) " " else "x"
                                        val next = t.replaceRange(task.offset, task.offset + 1, mark)
                                        // The hidden editor gets the same one-character change instead of reloading
                                        // the whole note on the main thread (~100 ms for a long note).
                                        val e = editor?.takeIf { session.editorText === t }
                                        e?.editableText?.replace(task.offset, task.offset + 1, mark)
                                        session.replace(next)
                                        if (e != null) session.editorText = next
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            state = listState,
                        )
                    }
                }
                val e = editor
                if (editing && e != null) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        linkQuery?.let { (start, typed) ->
                            LinkSuggestions(app.notes, typed, exclude = session.path) { title ->
                                e.applyEdit { Edits.completeLink(it, start, title) }
                            }
                        }
                        EditToolbar(
                            e, canUndo, canRedo,
                            onImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                    }
                }
            }
        }
    }
}

/** Note titles matching what's typed after `[[`, best first; tapping one completes the link. */
@Composable
private fun LinkSuggestions(notes: Notes, typed: String, exclude: String, onPick: (String) -> Unit) {
    val all = notes.state.collectAsStateWithLifecycle().value.notes
    val titles = remember(all, typed, exclude) {
        val q = normalize(typed)
        // The search index already holds every title without accents.
        all.asSequence().filter { it.path != exclude }
            .mapNotNull { m -> (notes.indexed(m.path)?.normTitle ?: normalize(m.title)).indexOf(q).takeIf { it >= 0 }?.let { m.title to it } }
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.second != 0 }, { it.first.length })).take(8).map { it.first }.toList()
    }
    if (titles.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items(titles) { title ->
            SuggestionChip(
                onClick = { onPick(title) },
                label = { Text(title, maxLines = 1) },
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
    )
}

@Composable
private fun ConflictBanner(onUseTheirs: () -> Unit, onKeepMine: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.tertiaryContainer)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            "This note changed on another device while you were editing it.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onTertiaryContainer,
        )
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = onUseTheirs) { Text("Use theirs") }
            TextButton(onClick = onKeepMine) { Text("Keep mine") }
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
