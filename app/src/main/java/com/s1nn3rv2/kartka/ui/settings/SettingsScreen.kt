package com.s1nn3rv2.kartka.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.s1nn3rv2.kartka.NoteLayout
import com.s1nn3rv2.kartka.Notes
import com.s1nn3rv2.kartka.ui.home.GroupLabel
import com.s1nn3rv2.kartka.ui.onboarding.treeUriToPath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(notes: Notes, onBack: () -> Unit) {
    val layout by notes.layout.collectAsStateWithLifecycle()
    val state by notes.state.collectAsStateWithLifecycle()
    var root by remember { mutableStateOf(notes.rootPath) }
    var defaultFolder by remember { mutableStateOf(notes.defaultFolder) }
    var pickingDefault by remember { mutableStateOf(false) }
    val pickRoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = uri?.let(::treeUriToPath) ?: return@rememberLauncherForActivityResult
        notes.rootPath = path
        notes.defaultFolder = ""
        root = path
        defaultFolder = ""
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { GroupLabel("Appearance") }
            item {
                Column(
                    Modifier.fillMaxWidth().background(colors.containerColor, MaterialTheme.shapes.large).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Notes layout", style = MaterialTheme.typography.titleMedium)
                    LayoutToggle(layout, notes::setLayout)
                }
            }
            item { GroupLabel("Storage") }
            item {
                SegmentedListItem(
                    onClick = { pickRoot.launch(null) },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    colors = colors,
                    leadingContent = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                    supportingContent = { Text(root ?: "Not set") },
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                ) { Text("Notes folder") }
            }
            item {
                Box {
                    SegmentedListItem(
                        onClick = { pickingDefault = true },
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                        colors = colors,
                        leadingContent = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                        supportingContent = { Text(defaultFolder.ifEmpty { "Notes folder" }) },
                    ) { Text("New notes go to") }
                    DropdownMenu(expanded = pickingDefault, onDismissRequest = { pickingDefault = false }) {
                        for (folder in state.folders) {
                            DropdownMenuItem(
                                text = { Text(folder.ifEmpty { "Notes folder" }) },
                                onClick = {
                                    notes.defaultFolder = folder
                                    defaultFolder = folder
                                    pickingDefault = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** List or Cards, as a connected button group: the selected option fills and the shapes morph on press. */
@Composable
private fun LayoutToggle(selected: NoteLayout, onSelect: (NoteLayout) -> Unit) {
    val options = listOf(
        Triple(NoteLayout.LIST, "List", Icons.Outlined.ViewAgenda),
        Triple(NoteLayout.CARDS, "Cards", Icons.Outlined.GridView),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (value, label, icon) ->
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { onSelect(value) },
                shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                // The default unselected color matches the panel behind it; one step stronger keeps the button visible.
                colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.weight(1f),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(label)
            }
        }
    }
}
