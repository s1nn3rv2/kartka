package com.s1nn3rv2.kartka.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** One menu entry. [danger] colors it as destructive (Delete); [selected] marks the current choice. */
class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val selected: Boolean? = null,
    val onClick: () -> Unit,
)

/**
 * A Material 3 Expressive menu: entries in rounded groups separated by a small gap, the current choice shown as
 * selected. Anchored to the enclosing Box like a classic dropdown menu. Empty groups are skipped.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupedMenu(expanded: Boolean, onDismiss: () -> Unit, groups: List<List<MenuAction>>) {
    val shown = groups.filter { it.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        shown.forEachIndexed { g, group ->
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(g, shown.size)) {
                group.forEachIndexed { i, action ->
                    val shapes = MenuDefaults.itemShape(i, group.size)
                    val icon: (@Composable () -> Unit)? = action.icon?.let { { Icon(it, contentDescription = null) } }
                    if (action.selected != null) {
                        SelectableDropdownMenuItem(
                            selected = action.selected,
                            onClick = action.onClick,
                            text = { Text(action.label) },
                            shapes = shapes,
                            leadingIcon = icon,
                        )
                    } else {
                        val error = MaterialTheme.colorScheme.error
                        DropdownMenuItem(
                            onClick = action.onClick,
                            text = { Text(action.label) },
                            shape = shapes.shape,
                            leadingIcon = icon,
                            colors = if (action.danger) MenuDefaults.itemColors(textColor = error, leadingIconColor = error) else MenuDefaults.itemColors(),
                        )
                    }
                }
            }
            if (g < shown.lastIndex) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        }
    }
}
