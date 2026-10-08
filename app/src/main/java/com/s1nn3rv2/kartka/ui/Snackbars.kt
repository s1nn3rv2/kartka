package com.s1nn3rv2.kartka.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.withTimeoutOrNull

/** Snackbars in the app's own surface colors, instead of Material's inverted ones (a white bar in dark mode). */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            data,
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
            actionColor = colors.primary,
        )
    }
}

/** How long an Undo snackbar stays: long enough to react, shorter than Material's 10-second "long". */
private const val UNDO_MILLIS = 5_000L

/** Shows [message] with Undo for a few seconds; true if Undo was tapped. */
suspend fun SnackbarHostState.showUndo(message: String): Boolean =
    withTimeoutOrNull(UNDO_MILLIS) {
        showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
    } == SnackbarResult.ActionPerformed
