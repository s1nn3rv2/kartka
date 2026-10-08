package com.s1nn3rv2.kartka.ui.note

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.s1nn3rv2.kartka.editor.MarkdownEditText
import com.s1nn3rv2.kartka.markdown.Edits

/** Formatting toolbar floating above the keyboard in Edit mode. Scrolls sideways when it doesn't fit. */
@Composable
fun EditToolbar(editor: MarkdownEditText, canUndo: Boolean, canRedo: Boolean, onImage: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    // Expressive floating toolbar in the vibrant colors, with Done as the attached button at its end.
    // The toolbar measures its buttons without a width limit, so the scrolling row gets one: the screen width
    // minus the Done button, the gap and the toolbar's padding.
    BoxWithConstraints(modifier) {
    val rowWidth = maxWidth - 104.dp
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onDone) {
                Icon(Icons.Filled.Check, contentDescription = "Done")
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        Row(Modifier.widthIn(max = rowWidth).horizontalScroll(rememberScrollState())) {
            Action(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = canUndo) { editor.undo() }
            Action(Icons.AutoMirrored.Filled.Redo, "Redo", enabled = canRedo) { editor.redo() }
            Action(Icons.Filled.FormatBold, "Bold") { editor.applyEdit { Edits.toggleWrap(it, "**") } }
            Action(Icons.Filled.FormatItalic, "Italic") { editor.applyEdit { Edits.toggleWrap(it, "*") } }
            Action(Icons.Filled.Title, "Heading") { editor.applyEdit(Edits::cycleHeading) }
            Action(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet") { editor.applyEdit(Edits::toggleBullet) }
            Action(Icons.Filled.CheckBox, "Checkbox") { editor.applyEdit(Edits::toggleTask) }
            Action(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent") { editor.applyEdit(Edits::indent) }
            Action(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Outdent") { editor.applyEdit(Edits::outdent) }
            Action(Icons.Filled.ArrowUpward, "Move up") { editor.applyEdit { Edits.move(it, up = true) } }
            Action(Icons.Filled.ArrowDownward, "Move down") { editor.applyEdit { Edits.move(it, up = false) } }
            Action(Icons.Filled.Code, "Code") { editor.applyEdit { Edits.toggleWrap(it, "`") } }
            Action(Icons.Filled.Link, "Link") { editor.applyEdit(Edits::link) }
            Action(Icons.Filled.Image, "Image", onClick = onImage)
        }
    }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
        Icon(icon, contentDescription = label)
    }
}

