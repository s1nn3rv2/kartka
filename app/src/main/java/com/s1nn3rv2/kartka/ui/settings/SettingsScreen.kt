package com.s1nn3rv2.kartka.ui.settings

import com.s1nn3rv2.kartka.SortOrder
import com.s1nn3rv2.kartka.setAppName
import com.s1nn3rv2.kartka.AppName
import com.s1nn3rv2.kartka.LocalAppName
import androidx.compose.ui.Alignment
import com.s1nn3rv2.kartka.ui.canLock
import com.s1nn3rv2.kartka.LockAfter
import com.s1nn3rv2.kartka.TextSize
import com.s1nn3rv2.kartka.ThemeMode
import com.s1nn3rv2.kartka.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
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
fun SettingsScreen(notes: Notes, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val layout by settings.layout.state.collectAsStateWithLifecycle()
    val theme by settings.theme.state.collectAsStateWithLifecycle()
    val sort by settings.sort.state.collectAsStateWithLifecycle()
    val dynamicColor by settings.dynamicColor.state.collectAsStateWithLifecycle()
    val appName by settings.appName.state.collectAsStateWithLifecycle()
    val textSize by settings.textSize.state.collectAsStateWithLifecycle()
    val lock by settings.lock.state.collectAsStateWithLifecycle()
    val lockAfter by settings.lockAfter.state.collectAsStateWithLifecycle()
    val hideInRecents by settings.hideInRecents.state.collectAsStateWithLifecycle()
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
                    ChoiceToggle(
                        listOf(NoteLayout.LIST to "List", NoteLayout.CARDS to "Cards"), layout, { settings.layout.value = it },
                        icons = listOf(Icons.Outlined.ViewAgenda, Icons.Outlined.GridView),
                    )
                    Text("Sort notes by", style = MaterialTheme.typography.titleMedium)
                    ChoiceToggle(SortOrder.entries.map { it to it.label }, sort, { settings.sort.value = it })
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    ChoiceToggle(ThemeMode.entries.map { it to it.label }, theme, { settings.theme.value = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Material You colors", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Colors from your wallpaper; off uses the standard Material colors",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = { settings.dynamicColor.value = it })
                    }
                    Text("App name", style = MaterialTheme.typography.titleMedium)
                    ChoiceToggle(AppName.entries.map { it to it.label }, appName, { settings.setAppName(context, it) })
                    Text(
                        "Some launchers drop the home screen icon when the name changes; add it again from the app list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Text size in notes", style = MaterialTheme.typography.titleMedium)
                    ChoiceToggle(TextSize.entries.map { it to it.label }, textSize, { settings.textSize.value = it })
                }
            }
            item { GroupLabel("Privacy") }
            item {
                SegmentedListItem(
                    onClick = { settings.hideInRecents.value = !hideInRecents },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    colors = colors,
                    leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                    supportingContent = { Text("Blank preview in recent apps; screenshots are blocked too") },
                    trailingContent = { Switch(checked = hideInRecents, onCheckedChange = null) },
                    modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                ) { Text("Hide in Recents") }
            }
            item {
                SegmentedListItem(
                    onClick = {
                        if (lock) {
                            settings.lock.value = false
                        } else if (canLock(context)) {
                            settings.lock.value = true
                        } else {
                            Toast.makeText(context, "Set up a screen lock in Android settings first.", Toast.LENGTH_LONG).show()
                        }
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    colors = colors,
                    leadingContent = { Icon(Icons.Outlined.Fingerprint, contentDescription = null) },
                    supportingContent = { Text("Fingerprint or screen lock to open ${LocalAppName.current}") },
                    trailingContent = { Switch(checked = lock, onCheckedChange = null) },
                ) { Text("Lock ${LocalAppName.current}") }
            }
            if (lock) {
                item {
                    Column(
                        Modifier.padding(top = 12.dp).fillMaxWidth().background(colors.containerColor, MaterialTheme.shapes.large).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Lock again after leaving for", style = MaterialTheme.typography.titleMedium)
                        ChoiceToggle(LockAfter.entries.map { it to it.label }, lockAfter, { settings.lockAfter.value = it })
                    }
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

/** One choice out of a few, as a connected button group: the selected option fills and the shapes morph on press. */
@Composable
private fun <T> ChoiceToggle(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, icons: List<ImageVector>? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (value, label) ->
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { onSelect(value) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                // The default unselected color matches the panel behind it; one step stronger keeps the button visible.
                colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.weight(1f),
            ) {
                icons?.get(i)?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(label, maxLines = 1)
            }
        }
    }
}
