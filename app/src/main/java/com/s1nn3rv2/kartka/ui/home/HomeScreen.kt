package com.s1nn3rv2.kartka.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Surface
import androidx.compose.material3.WideNavigationRailDefaults
import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.MaterialShapes
import com.s1nn3rv2.kartka.ui.EmptyState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.PushPin
import com.s1nn3rv2.kartka.SortOrder
import com.s1nn3rv2.kartka.index.groupNotes
import com.s1nn3rv2.kartka.LocalAppName
import androidx.compose.runtime.Stable
import androidx.compose.runtime.MutableState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ContainedLoadingIndicator
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberContainedSearchBarState
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
    layout: NoteLayout,
    sort: SortOrder,
    onOpenNote: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNewNote: (folder: String) -> Unit,
    onNewScratch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHidden: (trash: Boolean) -> Unit,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState,
) {
    val state by notes.state.collectAsStateWithLifecycle()
    if (folder == null) {
        // The sidebar stays open: the next screen slides in over it, and back returns to it still open
        // (this state is saved while another screen is on top).
        var sidebar by rememberSaveable { mutableStateOf(false) }
        Box {
            Home(notes, state, layout, sort, onOpenNote, onMenu = { sidebar = true }, { onNewNote(notes.defaultFolder) }, onNewScratch, snackbar)
            val tree = remember(state.folders, state.notes) { folderTree(state.folders, state.notes) }
            Sidebar(sidebar, onClose = { sidebar = false }) {
                FolderRail(tree, onOpenFolder = onOpenFolder, onOpenSettings = onOpenSettings, onOpenHidden = onOpenHidden)
            }
        }
    } else {
        Folder(notes, state, layout, sort, folder, onOpenNote, onOpenFolder, { onNewNote(folder) }, onNewScratch, onBack ?: {}, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Home(
    notes: Notes,
    state: NotesState,
    layout: NoteLayout,
    sort: SortOrder,
    onOpenNote: (String) -> Unit,
    onMenu: () -> Unit,
    onNewNote: () -> Unit,
    onNewScratch: () -> Unit,
    snackbar: SnackbarHostState,
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
            placeholder = { Text("Search notes or #tags") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
    }
    val list = rememberLazyStaggeredGridState()
    val selection = rememberSelection(notes, state, snackbar)
    // The bar turns gray by adding up scroll events; a list that moved without them (restored, jumped) would leave
    // it gray at the top. At the very top, it's reset.
    LaunchedEffect(list) {
        snapshotFlow { list.canScrollBackward }.collect { if (!it) scrollBehavior.scrollState.contentOffset = 0f }
    }
    // The status bar area takes the bar's color, so the bar reads as one solid piece when it slides back in
    // (otherwise the notes show through above it).
    val scrolled by remember { derivedStateOf { list.canScrollBackward } }
    val statusBarColor by animateColorAsState(
        when {
            selection.active -> TopAppBarDefaults.topAppBarColors().containerColor
            scrolled -> colors.scrolledAppBarContainerColor
            else -> colors.appBarContainerColor
        },
    )
    Box {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            // Both in Scaffold slots, so a snackbar lifts the FAB menu above itself instead of covering it.
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = { NewNoteMenu(list, onNewNote, onNewScratch, Modifier, hidden = selection.active) },
            topBar = {
                SelectionAware(selection.active, { SelectionBar(selection, notes) { state.notes.map { it.path } } }) {
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
                }
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
                    val pins by notes.pins.collectAsStateWithLifecycle()
                    val groups = remember(state.notes, pins, sort) { groupNotes(state.notes, pins, sort, restLabel = null) }
                    // A pin added while at the top would appear above the first visible row; keep the top in view.
                    KeepTopInView(list, pins)
                    NoteGrid(layout, list, padding) {
                        if (state.notes.isEmpty()) fullLine("empty") { EmptyState(Icons.Outlined.EditNote, "No notes yet. Tap + to write one.", MaterialShapes.Cookie9Sided) }
                        for ((label, items) in groups) noteGroup(label, items, layout, notes, state.indexVersion, showFolder = true, selection, pins, onOpenNote)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(statusBarColor))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Folder(
    notes: Notes,
    state: NotesState,
    layout: NoteLayout,
    sort: SortOrder,
    folder: String,
    onOpenNote: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNewNote: () -> Unit,
    onNewScratch: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val here = remember(state.notes, folder) { state.notes.filter { it.folder == folder } }
    val subfolders = remember(state.folders, folder) {
        state.folders.filter { it.isNotEmpty() && it.substringBeforeLast('/', "") == folder }.sortedBy { it.lowercase() }
    }
    val pins by notes.pins.collectAsStateWithLifecycle()
    val groups = remember(here, pins, sort) { groupNotes(here, pins, sort, restLabel = "Notes") }
    val list = rememberLazyStaggeredGridState()
    KeepTopInView(list, pins)
    val selection = rememberSelection(notes, state, snackbar)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { NewNoteMenu(list, onNewNote, onNewScratch, Modifier, hidden = selection.active) },
        topBar = {
            SelectionAware(selection.active, { SelectionBar(selection, notes) { here.map { it.path } } }) {
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
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            NoteGrid(layout, list, padding) {
                if (subfolders.isNotEmpty()) {
                    fullLine("folders") { GroupLabel("Folders") }
                    itemsIndexed(subfolders, key = { _, p -> "f:$p" }, contentType = { _, _ -> "folder" }, span = { _, _ -> StaggeredGridItemSpan.FullLine }) { i, path ->
                        SegmentedListItem(
                            onClick = { onOpenFolder(path) },
                            shapes = ListItemDefaults.segmentedShapes(index = i, count = subfolders.size),
                            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                        ) { Text(path.substringAfterLast('/')) }
                    }
                }
                if (here.isEmpty() && subfolders.isEmpty()) fullLine("empty") {
                    EmptyState(Icons.Outlined.FolderOpen, "Nothing here yet. Tap + to write a note in this folder.", MaterialShapes.Clover4Leaf)
                }
                for ((label, items) in groups) noteGroup(label, items, layout, notes, state.indexVersion, showFolder = false, selection, pins, onOpenNote)
            }
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
    item(key = key, contentType = "label", span = StaggeredGridItemSpan.FullLine) { content() }

/** A labelled group of notes: segmented rows (large corners only at the group's ends) or cards. */
private fun LazyStaggeredGridScope.noteGroup(
    label: String,
    items: List<NoteMeta>,
    layout: NoteLayout,
    notes: Notes,
    indexVersion: Int,
    showFolder: Boolean,
    selection: Selection,
    pins: Set<String>,
    onOpenNote: (String) -> Unit,
) {
    fullLine("label:$label") { GroupLabel(label) }
    itemsIndexed(items, key = { _, m -> m.path }, contentType = { _, _ -> "note" }) { i, meta ->
        val haptics = LocalHapticFeedback.current
        // Holding a note starts selecting; while selecting, a tap adds or removes a note instead of opening it.
        val onClick = {
            if (selection.active) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                selection.toggle(meta.path)
            } else {
                onOpenNote(meta.path)
            }
        }
        val onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            selection.toggle(meta.path)
        }
        val selected = if (selection.active) meta.path in selection.paths else null
        // Notes glide to their new place with the theme's springs when pinned, deleted, restored or re-sorted.
        Box(Modifier.animateItem()) {
            val shape = if (layout == NoteLayout.CARDS) MaterialTheme.shapes.large else ListItemDefaults.segmentedShapes(i, items.size).shape
            SwipeActions(
                pinned = meta.path in pins,
                shape = shape,
                enabled = !selection.active,
                onPin = { notes.setPinned(listOf(meta.path), meta.path !in pins) },
                onTrash = { selection.trash(listOf(meta.path)) },
            ) {
                if (layout == NoteLayout.CARDS) {
                    NoteCard(meta, notes, indexVersion, showFolder, meta.path in pins, selected, onClick, onLongClick)
                } else {
                    NoteRow(meta, i, items.size, notes, indexVersion, showFolder, meta.path in pins, selected, onClick, onLongClick)
                }
            }
        }
    }
}

/**
 * Swipe a note to the right to pin or unpin it, to the left to move it to the trash. The colored action shows under
 * the note as it moves, with a buzz when the swipe passes the point where it takes effect; the note springs back.
 */
@Composable
private fun SwipeActions(
    pinned: Boolean,
    shape: Shape,
    enabled: Boolean,
    onPin: () -> Unit,
    onTrash: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Acts once per swipe, after the note has sprung back: acting right away would move a pinned note to another
    // group while it still counts as swiped there, which would fire the action again.
    LaunchedEffect(state.settledValue) {
        val swiped = state.settledValue
        if (swiped == SwipeToDismissBoxValue.Settled) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            state.reset()
            if (swiped == SwipeToDismissBoxValue.StartToEnd) onPin() else onTrash()
        }
    }
    LaunchedEffect(state.targetValue) {
        if (state.targetValue != SwipeToDismissBoxValue.Settled) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    SwipeToDismissBox(
        state = state,
        gesturesEnabled = enabled,
        // The note slides within its own space, not over its neighbors.
        modifier = Modifier.clip(shape),
        backgroundContent = {
            val direction = state.dismissDirection
            if (direction == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            val colors = MaterialTheme.colorScheme
            val toPin = direction == SwipeToDismissBoxValue.StartToEnd
            val armed = state.targetValue != SwipeToDismissBoxValue.Settled
            // The icon grows with a spring once the swipe will take effect.
            val iconScale by animateFloatAsState(if (armed) 1.25f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "swipe icon")
            Box(
                Modifier.fillMaxSize().clip(shape).background(if (toPin) colors.primaryContainer else colors.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toPin) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    when {
                        !toPin -> Icons.Outlined.Delete
                        pinned -> Icons.Outlined.PushPin
                        else -> Icons.Filled.PushPin
                    },
                    contentDescription = when {
                        !toPin -> "Move to trash"
                        pinned -> "Unpin"
                        else -> "Pin"
                    },
                    tint = if (toPin) colors.onPrimaryContainer else colors.onErrorContainer,
                    modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                )
            }
        },
    ) { content() }
}

/** Notes picked by holding them, for deleting several at once. */
@Stable
private class Selection(private val state: MutableState<Set<String>>) {
    val paths get() = state.value
    val active get() = paths.isNotEmpty()
    var delete: () -> Unit = {}

    /** Moves notes to the trash with an Undo snackbar (selection's Delete, and swiping a note away). */
    var trash: (List<String>) -> Unit = {}

    /** How far a back gesture leaving selection mode has gone: 0 = not started, 1 = done. */
    val backProgress = Animatable(0f)
    fun toggle(path: String) {
        state.value = if (path in paths) paths - path else paths + path
    }
    fun set(paths: Set<String>) {
        state.value = paths
    }
}

/** Selection for one list; back clears it, and notes that disappear (deleted, moved) drop out of it. */
@Composable
private fun rememberSelection(notes: Notes, state: NotesState, snackbar: SnackbarHostState): Selection {
    val scope = rememberCoroutineScope()
    val selection = remember { Selection(mutableStateOf(emptySet())) }
    LaunchedEffect(state.notes) {
        val existing = state.notes.mapTo(HashSet()) { it.path }
        if (selection.paths.any { it !in existing }) selection.set(selection.paths.filterTo(HashSet()) { it in existing })
    }
    // The selection bar follows the back gesture; letting go ends selecting, cancelling springs it back.
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    PredictiveBackHandler(selection.active) { events ->
        try {
            events.collect { selection.backProgress.snapTo(it.progress) }
            selection.set(emptySet())
            selection.backProgress.snapTo(0f)
        } catch (e: CancellationException) {
            selection.backProgress.animateTo(0f, spring)
            throw e
        }
    }
    selection.delete = {
        val paths = selection.paths.toList()
        selection.set(emptySet())
        selection.trash(paths)
    }
    selection.trash = { paths ->
        scope.launch {
            val moved = notes.trash(paths)
            val message = if (moved.size == 1) "Moved 1 note to trash" else "Moved ${moved.size} notes to trash"
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) moved.forEach { notes.restore(it) }
        }
    }
    return selection
}

/** The normal top bar, or the selection bar while selecting, sliding and fading between the two. */
@Composable
private fun SelectionAware(selecting: Boolean, selectionBar: @Composable () -> Unit, bar: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        selecting,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { -it / 3 }) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it / 3 }) using
                SizeTransform(clip = false)
        },
        label = "top bar",
    ) { if (it) selectionBar() else bar() }
}

/**
 * When pins change while the list is at its top, scrolls to the very top so a newly pinned note (which appears
 * above) is in view. Only on real changes: coming back to the screen restores the scroll position untouched.
 */
@Composable
private fun KeepTopInView(list: LazyStaggeredGridState, pins: Set<String>) {
    var seen by rememberSaveable { mutableStateOf(pins.sorted()) }
    LaunchedEffect(pins) {
        val now = pins.sorted()
        if (now == seen) return@LaunchedEffect
        seen = now
        if (list.firstVisibleItemIndex <= 1) list.scrollToItem(0)
    }
}

/** Replaces the top bar while selecting: how many, select all, and move to trash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(selection: Selection, notes: Notes, all: () -> List<String>) {
    val pins by notes.pins.collectAsStateWithLifecycle()
    val allPinned = pins.containsAll(selection.paths)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    TopAppBar(
        modifier = Modifier.graphicsLayer {
            val p = selection.backProgress.value
            scaleX = 1f - 0.08f * p
            scaleY = 1f - 0.08f * p
            alpha = 1f - 0.4f * p
            translationY = -p * with(density) { 12.dp.toPx() }
        },
        // While the bar animates away the selection is already empty; keep showing the last count.
        title = {
            var count by remember { mutableIntStateOf(selection.paths.size) }
            if (selection.paths.isNotEmpty()) count = selection.paths.size
            Text("$count selected")
        },
        navigationIcon = {
            IconButton(onClick = { selection.set(emptySet()) }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Filled.Close, contentDescription = "Stop selecting")
            }
        },
        actions = {
            IconButton(onClick = { selection.set(all().toSet()) }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    notes.setPinned(selection.paths, !allPinned)
                    selection.set(emptySet())
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(if (allPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = if (allPinned) "Unpin" else "Pin")
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    selection.delete()
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "Move to trash")
            }
        },
    )
}

/** Marks pop in and out with a small scale, like the selection itself. */
private fun AnimatedContentTransitionScope<*>.markTransition(): ContentTransform =
    (fadeIn(tween(150)) + scaleIn(tween(200), initialScale = 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(150), targetScale = 0.6f))

/** A card's top corner: the selection circle while selecting, a pin on pinned notes, otherwise nothing. */
@Composable
private fun CornerMark(pinned: Boolean, selected: Boolean?, modifier: Modifier) {
    AnimatedContent(selected, modifier, transitionSpec = { markTransition() }, label = "card corner") { s ->
        when {
            s != null -> SelectionMark(s)
            pinned -> Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** In selection mode, a filled check for selected notes and an empty circle for the rest. */
@Composable
private fun SelectionMark(selected: Boolean) {
    Icon(
        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoteCard(
    meta: NoteMeta,
    notes: Notes,
    indexVersion: Int,
    showFolder: Boolean,
    pinned: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val snippet = remember(meta, indexVersion) { notes.indexed(meta.path)?.text?.let { preview(it, max = 280) }.orEmpty() }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val container by animateColorAsState(if (selected == true) colors.secondaryContainer else colors.surfaceContainer, MaterialTheme.motionScheme.defaultEffectsSpec())
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
        // Pinned cards get a thin outline in the theme's main color, the same on every side.
        border = if (pinned) BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)) else null,
        modifier = Modifier.clip(shape).combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = "Select",
        ),
    ) {
        Box(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // The top lines leave room for the corner mark, so nothing reflows when selecting starts.
            if (showFolder && meta.folder.isNotEmpty()) {
                Text(
                    meta.folder,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 22.dp),
                )
            }
            Text(
                meta.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 22.dp),
            )
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
        // Top corner: the selection circle while selecting, otherwise a pin on pinned notes. Drawn over the card,
        // so nothing in it moves when selecting starts or ends.
        CornerMark(pinned, selected, Modifier.align(Alignment.TopEnd).padding(10.dp))
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
    pinned: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val snippet = remember(meta, indexVersion) { notes.indexed(meta.path)?.text?.let { preview(it) }.orEmpty() }
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected == true) colors.secondaryContainer else colors.surfaceContainer, MaterialTheme.motionScheme.defaultEffectsSpec())
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "Select",
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = container),
        overlineContent = if (showFolder && meta.folder.isNotEmpty()) ({ Text(meta.folder, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
        supportingContent = if (snippet.isNotEmpty()) ({ Text(snippet, maxLines = 2, overflow = TextOverflow.Ellipsis) }) else null,
        // The end of the row: the time (with a pin on pinned notes), or the selection circle while selecting.
        // Swapped in place, so the text never jumps sideways.
        trailingContent = {
            AnimatedContent(selected, transitionSpec = { markTransition() }, contentAlignment = Alignment.CenterEnd, label = "row end") { s ->
                if (s != null) {
                    SelectionMark(s)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (pinned) Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = colors.primary, modifier = Modifier.size(16.dp))
                        Text(relativeTime(meta.modified), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResults(notes: Notes, query: TextFieldState, onOpen: (String) -> Unit) {
    var results by remember { mutableStateOf(emptyList<SearchResult>()) }
    var searched by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        snapshotFlow { query.text.toString() }.collectLatest { q ->
            delay(100)
            results = notes.search(q)
            searched = q
        }
    }
    if (searched.isNotBlank() && results.isEmpty()) {
        return EmptyState(Icons.Outlined.SearchOff, "Nothing matches \u201c${searched.trim()}\u201d.", MaterialShapes.Ghostish)
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
private fun NewNoteMenu(list: LazyStaggeredGridState, onNewNote: () -> Unit, onNewScratch: () -> Unit, modifier: Modifier, hidden: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(open) { open = false }
    val scrolledAway by remember { derivedStateOf { list.lastScrolledForward && list.canScrollForward } }
    val visible = !hidden && !scrolledAway
    FloatingActionButtonMenu(
        expanded = open,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = open,
                onCheckedChange = { open = !open },
                modifier = Modifier.animateFloatingActionButton(visible = !hidden && (visible || open), alignment = Alignment.BottomEnd),
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

/**
 * A sidebar panel in the style of Material 3 Expressive's expanded navigation rail, over a scrim. It slides in from
 * the start edge, follows the predictive back gesture, and closes on a tap outside. Built in the screen itself (the
 * stock modal rail is a separate window, which would stay on top of the next screen).
 */
@Composable
private fun Sidebar(open: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    val shown = remember { Animatable(if (open) 1f else 0f) } // 1 = fully open
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(open) { shown.animateTo(if (open) 1f else 0f, spatial) }
    PredictiveBackHandler(enabled = open) { events ->
        try {
            events.collect { shown.snapTo(1f - it.progress * 0.4f) }
            onClose()
        } catch (e: CancellationException) {
            shown.animateTo(1f, spatial)
            throw e
        }
    }
    if (shown.value == 0f && !open) return
    val colors = WideNavigationRailDefaults.colors()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }.background(colors.modalScrimColor)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        Surface(
            shape = WideNavigationRailDefaults.modalExpandedShape,
            color = colors.modalContainerColor,
            contentColor = colors.modalContentColor,
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.82f).widthIn(max = 360.dp)
                .graphicsLayer { translationX = -(1f - shown.value) * size.width },
        ) { content() }
    }
}

/** The sidebar content: every folder, nested by indentation, with how many notes sit directly in it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderRail(tree: FolderNode, onOpenFolder: (String) -> Unit, onOpenSettings: () -> Unit, onOpenHidden: (trash: Boolean) -> Unit) {
    val rows = remember(tree) {
        buildList {
            fun add(node: FolderNode, depth: Int) {
                add(node to depth)
                node.children.forEach { add(it, depth + 1) }
            }
            tree.children.forEach { add(it, 0) }
        }
    }
    // Full-width pill rows like the expanded rail's items: the whole line is tappable and shows the press.
    @Composable
    fun Item(label: String, icon: ImageVector, count: Int = 0, depth: Int = 0, onClick: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Row(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(56.dp).clip(CircleShape).clickable(onClick = onClick)
                .padding(start = 16.dp + 16.dp * depth, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (count > 0) Text("$count", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Text(
            LocalAppName.current,
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            if (rows.isEmpty()) item { EmptyState(Icons.Outlined.Folder, "No folders yet.", MaterialShapes.Clover4Leaf) }
            items(rows, key = { it.first.path }) { (node, depth) ->
                Item(node.name, Icons.Outlined.Folder, node.notes, depth) { onOpenFolder(node.path) }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        Item("Scratch", Icons.Outlined.Bolt) { onOpenHidden(false) }
        Item("Trash", Icons.Outlined.Delete) { onOpenHidden(true) }
        Item("Settings", Icons.Outlined.Settings) { onOpenSettings() }
        Spacer(Modifier.height(12.dp))
    }
}
