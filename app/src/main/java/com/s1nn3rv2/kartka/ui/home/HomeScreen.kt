package com.s1nn3rv2.kartka.ui.home

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.s1nn3rv2.kartka.NoteLayout
import com.s1nn3rv2.kartka.Notes
import com.s1nn3rv2.kartka.NotesState
import com.s1nn3rv2.kartka.index.Age
import com.s1nn3rv2.kartka.index.SearchResult
import com.s1nn3rv2.kartka.index.age
import com.s1nn3rv2.kartka.index.preview
import com.s1nn3rv2.kartka.storage.FolderNode
import com.s1nn3rv2.kartka.storage.NoteMeta
import com.s1nn3rv2.kartka.storage.folderTree
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Home ([folder] null): search at the top, latest notes grouped by age, folders in the drawer. Folder ([folder]
 * set): its subfolders and notes under a large collapsing title. Both create notes from a FAB menu.
 */
@Composable
fun HomeScreen(
    notes: Notes,
    folder: String?,
    onOpenNote: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNewNote: (folder: String) -> Unit,
    onNewScratch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val state by notes.state.collectAsStateWithLifecycle()
    val layout by notes.layout.collectAsStateWithLifecycle()
    if (folder == null) {
        val scope = rememberCoroutineScope()
        val drawer = rememberDrawerState(DrawerValue.Closed)
        BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                FolderDrawer(
                    folderTree(state.folders, state.notes),
                    onOpenFolder = { path ->
                        scope.launch { drawer.close() }
                        onOpenFolder(path)
                    },
                    onOpenSettings = {
                        scope.launch { drawer.close() }
                        onOpenSettings()
                    },
                )
            },
        ) {
            Home(notes, state, layout, onOpenNote, onMenu = { scope.launch { drawer.open() } }, { onNewNote(notes.defaultFolder) }, onNewScratch)
        }
    } else {
        Folder(notes, state, layout, folder, onOpenNote, onOpenFolder, { onNewNote(folder) }, onNewScratch, onBack ?: {})
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Home(
    notes: Notes,
    state: NotesState,
    layout: NoteLayout,
    onOpenNote: (String) -> Unit,
    onMenu: () -> Unit,
    onNewNote: () -> Unit,
    onNewScratch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val query = rememberTextFieldState()
    val search = rememberContainedSearchBarState()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val colors = SearchBarDefaults.appBarWithSearchColors(searchBarColors = SearchBarDefaults.containedColors(state = search))
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = query,
            searchBarState = search,
            colors = colors.searchBarColors.inputFieldColors,
            onSearch = { },
            placeholder = { Text("Search notes") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
    }
    val list = rememberLazyStaggeredGridState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppBarWithSearch(
                state = search,
                inputField = inputField,
                scrollBehavior = scrollBehavior,
                colors = colors,
                navigationIcon = {
                    IconButton(onClick = onMenu, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Filled.Menu, contentDescription = "Folders")
                    }
                },
            )
            ExpandedFullScreenContainedSearchBar(state = search, inputField = inputField, colors = colors.searchBarColors) {
                SearchResults(notes, query) { path ->
                    scope.launch { search.animateToCollapsed() }
                    onOpenNote(path)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (!state.loaded) {
                ContainedLoadingIndicator(Modifier.align(Alignment.Center))
            } else {
                val groups = remember(state.notes) { state.notes.groupBy { age(it.modified) }.toSortedMap() }
                NoteGrid(layout, list, padding) {
                    if (state.notes.isEmpty()) fullLine("empty") { Empty("No notes yet. Tap + to write one.") }
                    for ((age, items) in groups) noteGroup(age.label, items, layout, notes, state.indexVersion, showFolder = true, onOpenNote)
                }
            }
            NewNoteMenu(list, onNewNote, onNewScratch, Modifier.align(Alignment.BottomEnd).padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Folder(
    notes: Notes,
    state: NotesState,
    layout: NoteLayout,
    folder: String,
    onOpenNote: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNewNote: () -> Unit,
    onNewScratch: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val here = remember(state.notes, folder) { state.notes.filter { it.folder == folder } }
    val subfolders = remember(state.folders, folder) {
        state.folders.filter { it.isNotEmpty() && it.substringBeforeLast('/', "") == folder }.sortedBy { it.lowercase() }
    }
    val list = rememberLazyStaggeredGridState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(folder.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(if (here.size == 1) "1 note" else "${here.size} notes") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            NoteGrid(layout, list, padding) {
                if (subfolders.isNotEmpty()) {
                    fullLine("folders") { GroupLabel("Folders") }
                    itemsIndexed(subfolders, key = { _, p -> "f:$p" }, span = { _, _ -> StaggeredGridItemSpan.FullLine }) { i, path ->
                        SegmentedListItem(
                            onClick = { onOpenFolder(path) },
                            shapes = ListItemDefaults.segmentedShapes(index = i, count = subfolders.size),
                            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                        ) { Text(path.substringAfterLast('/')) }
                    }
                }
                if (here.isEmpty() && subfolders.isEmpty()) fullLine("empty") { Empty("Nothing here yet. Tap + to write a note in this folder.") }
                if (here.isNotEmpty()) noteGroup("Notes", here, layout, notes, state.indexVersion, showFolder = false, onOpenNote)
            }
            NewNoteMenu(list, onNewNote, onNewScratch, Modifier.align(Alignment.BottomEnd).padding(padding))
        }
    }
}

/** List padding: the bars' padding plus room at the bottom so the FAB never covers the last note. */
@Composable
private fun listPadding(bars: PaddingValues): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = bars.calculateStartPadding(dir) + 16.dp,
        end = bars.calculateEndPadding(dir) + 16.dp,
        top = bars.calculateTopPadding(),
        bottom = bars.calculateBottomPadding() + 112.dp,
    )
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

/**
 * Notes in one column of segmented rows (List) or a grid of cards (Cards, two columns on a phone, more on wider
 * screens). Labels and folders always span the full width.
 */
@Composable
private fun NoteGrid(layout: NoteLayout, state: LazyStaggeredGridState, bars: PaddingValues, content: LazyStaggeredGridScope.() -> Unit) {
    val cards = layout == NoteLayout.CARDS
    LazyVerticalStaggeredGrid(
        columns = if (cards) StaggeredGridCells.Adaptive(160.dp) else StaggeredGridCells.Fixed(1),
        state = state,
        contentPadding = listPadding(bars),
        horizontalArrangement = Arrangement.spacedBy(if (cards) 8.dp else 0.dp),
        verticalItemSpacing = if (cards) 8.dp else 0.dp,
        content = content,
    )
}

private fun LazyStaggeredGridScope.fullLine(key: String, content: @Composable () -> Unit) =
    item(key = key, span = StaggeredGridItemSpan.FullLine) { content() }

/** A labelled group of notes: segmented rows (large corners only at the group's ends) or cards. */
private fun LazyStaggeredGridScope.noteGroup(
    label: String,
    items: List<NoteMeta>,
    layout: NoteLayout,
    notes: Notes,
    indexVersion: Int,
    showFolder: Boolean,
    onOpenNote: (String) -> Unit,
) {
    fullLine("label:$label") { GroupLabel(label) }
    itemsIndexed(items, key = { _, m -> m.path }) { i, meta ->
        if (layout == NoteLayout.CARDS) {
            NoteCard(meta, notes, indexVersion, showFolder, onOpenNote)
        } else {
            NoteRow(meta, i, items.size, notes, indexVersion, showFolder, onOpenNote)
        }
    }
}

@Composable
private fun NoteCard(meta: NoteMeta, notes: Notes, indexVersion: Int, showFolder: Boolean, onOpenNote: (String) -> Unit) {
    val snippet = remember(meta, indexVersion) { notes.indexed(meta.path)?.text?.let { preview(it, max = 280) }.orEmpty() }
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = { onOpenNote(meta.path) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (showFolder && meta.folder.isNotEmpty()) {
                Text(meta.folder, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(meta.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (snippet.isNotEmpty()) {
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(relativeTime(meta.modified), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun NoteRow(
    meta: NoteMeta,
    index: Int,
    count: Int,
    notes: Notes,
    indexVersion: Int,
    showFolder: Boolean,
    onOpenNote: (String) -> Unit,
) {
    val snippet = remember(meta, indexVersion) { notes.indexed(meta.path)?.text?.let { preview(it) }.orEmpty() }
    SegmentedListItem(
        onClick = { onOpenNote(meta.path) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        overlineContent = if (showFolder && meta.folder.isNotEmpty()) ({ Text(meta.folder, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
        supportingContent = if (snippet.isNotEmpty()) ({ Text(snippet, maxLines = 2, overflow = TextOverflow.Ellipsis) }) else null,
        trailingContent = { Text(relativeTime(meta.modified), style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
    }
}

private fun relativeTime(time: Long): String =
    if (age(time) == Age.TODAY) {
        DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    } else {
        DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_ALL).toString()
    }

@Composable
private fun SearchResults(notes: Notes, query: TextFieldState, onOpen: (String) -> Unit) {
    var results by remember { mutableStateOf(emptyList<SearchResult>()) }
    LaunchedEffect(Unit) {
        snapshotFlow { query.text.toString() }.collectLatest { q ->
            delay(100)
            results = notes.search(q)
        }
    }
    val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        itemsIndexed(results, key = { _, r -> r.meta.path }) { i, r ->
            SegmentedListItem(
                onClick = { onOpen(r.meta.path) },
                shapes = ListItemDefaults.segmentedShapes(index = i, count = results.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                overlineContent = if (r.meta.folder.isNotEmpty()) ({ Text(r.meta.folder, maxLines = 1) }) else null,
                supportingContent = { Text(highlighted(r, highlight), maxLines = 3, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
            ) { Text(r.meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private fun highlighted(r: SearchResult, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(r.snippet)
    for (h in r.highlights) if (h.last < r.snippet.length) addStyle(style, h.first, h.last + 1)
}

/** The FAB menu: New note and Scratch note. Hides while scrolling down, comes back when scrolling up. */
@Composable
private fun NewNoteMenu(list: LazyStaggeredGridState, onNewNote: () -> Unit, onNewScratch: () -> Unit, modifier: Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(open) { open = false }
    val visible by remember { derivedStateOf { !list.lastScrolledForward || !list.canScrollForward } }
    FloatingActionButtonMenu(
        expanded = open,
        modifier = modifier.navigationBarsPadding(),
        button = {
            ToggleFloatingActionButton(
                checked = open,
                onCheckedChange = { open = !open },
                modifier = Modifier.animateFloatingActionButton(visible = visible || open, alignment = Alignment.BottomEnd),
            ) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add } }
                Icon(
                    rememberVectorPainter(icon),
                    contentDescription = if (open) "Close" else "New",
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { open = false; onNewNote() },
            icon = { Icon(Icons.Filled.EditNote, contentDescription = null) },
            text = { Text("Note") },
        )
        FloatingActionButtonMenuItem(
            onClick = { open = false; onNewScratch() },
            icon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
            text = { Text("Scratch note") },
        )
    }
}

/** The sidebar: every folder, nested by indentation, with how many notes sit directly in it. */
@Composable
private fun FolderDrawer(tree: FolderNode, onOpenFolder: (String) -> Unit, onOpenSettings: () -> Unit) {
    val rows = remember(tree) {
        buildList {
            fun add(node: FolderNode, depth: Int) {
                add(node to depth)
                node.children.forEach { add(it, depth + 1) }
            }
            tree.children.forEach { add(it, 0) }
        }
    }
    ModalDrawerSheet {
        Text(
            "Kartka",
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            if (rows.isEmpty()) item { Empty("No folders yet.") }
            itemsIndexed(rows, key = { _, r -> r.first.path }) { _, (node, depth) ->
                NavigationDrawerItem(
                    label = { Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    badge = { if (node.notes > 0) Text("${node.notes}") },
                    selected = false,
                    onClick = { onOpenFolder(node.path) },
                    modifier = Modifier.padding(start = 12.dp + 16.dp * depth, end = 12.dp),
                )
            }
        }
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            selected = false,
            onClick = onOpenSettings,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}
