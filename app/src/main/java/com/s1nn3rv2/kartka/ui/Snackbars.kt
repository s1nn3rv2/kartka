package com.s1nn3rv2.kartka.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.withTimeoutOrNull

/** Snackbars in the theme's primary container color, instead of Material's inverted ones (a white bar in dark mode). */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SnackbarHost(state, modifier) { data ->
        // Swipe it sideways to dismiss it early (the action, like Undo, isn't taken). Each snackbar gets its own
        // swipe state, so the next one doesn't start out swiped away.
        val swipe = key(data) { rememberSwipeToDismissBoxState() }
        LaunchedEffect(swipe.settledValue) {
            if (swipe.settledValue != SwipeToDismissBoxValue.Settled) data.dismiss()
        }
        SwipeToDismissBox(state = swipe, backgroundContent = {}) {
            Snackbar(
                data,
                shape = MaterialTheme.shapes.large,
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                actionColor = colors.onPrimaryContainer,
            )
        }
    }
}

/** How long an Undo snackbar stays: long enough to react, shorter than Material's 10-second "long". */
private const val UNDO_MILLIS = 5_000L

/** Shows [message] with Undo for a few seconds; true if Undo was tapped. */
suspend fun SnackbarHostState.showUndo(message: String): Boolean =
    withTimeoutOrNull(UNDO_MILLIS) {
        showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
    } == SnackbarResult.ActionPerformed
