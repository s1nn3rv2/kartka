package com.s1nn3rv2.kartka

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.s1nn3rv2.kartka.ui.SlideStack
import com.s1nn3rv2.kartka.ui.home.HiddenNotesScreen
import com.s1nn3rv2.kartka.ui.home.HomeScreen
import com.s1nn3rv2.kartka.ui.rememberSlideStack
import com.s1nn3rv2.kartka.ui.settings.SettingsScreen
import com.s1nn3rv2.kartka.ui.note.NoteScreen
import com.s1nn3rv2.kartka.ui.onboarding.OnboardingScreen
import com.s1nn3rv2.kartka.ui.theme.KartkaTheme
import kotlinx.coroutines.launch

/** Screens. The back stack is a plain list; Navigation 3 animates between entries, including predictive back. */
sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data object Trash : Route
    data object Scratch : Route
    data class Folder(val path: String) : Route
    /** [isNew]: just created, so it opens in Edit mode with the title field focused. */
    data class Note(val path: String, val isNew: Boolean = false) : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KartkaApp
        setContent {
            KartkaTheme {
                // The window shows through during transitions and at startup; match it to the theme instead of
                // the platform's plain light or dark background.
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect { window.setBackgroundDrawable(ColorDrawable(background)) }
                App(app)
            }
        }
    }
}

@Composable
private fun App(app: KartkaApp) {
    val notes = app.notes
    var ready by remember { mutableStateOf(Environment.isExternalStorageManager() && notes.canRead()) }
    if (!ready) {
        OnboardingScreen(notes, onDone = { ready = true })
        return
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { notes.refresh() }
    // Picks up changes made while the app was in the background (Syncthing, other editors).
    LifecycleResumeEffect(Unit) {
        notes.refreshSoon()
        onPauseOrDispose { }
    }
    // ponytail: the back stack is not kept across process death; save it once deep links or restore matter.
    val stack = rememberSlideStack<Route>(Route.Home)
    val activity = LocalActivity.current
    // Back on the last screen leaves the app.
    val back = { if (!stack.pop()) activity?.finish() }
    var opening by remember { mutableStateOf(false) }
    fun openNote(path: String, isNew: Boolean = false) {
        if (opening) return // a quick double tap must not open the note twice
        opening = true
        scope.launch {
            notes.prepare(path)
            stack.push(Route.Note(path, isNew))
            opening = false
        }
    }
    val snackbar = remember { SnackbarHostState() }
    fun trashed(trashPath: String) {
        back()
        scope.launch {
            val result = snackbar.showSnackbar("Moved to trash", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) notes.restore(trashPath)
        }
    }
    Box(Modifier.fillMaxSize()) {
    SlideStack(stack) { route ->
        when (route) {
            Route.Home -> {
                HomeScreen(
                    notes = notes,
                    folder = null,
                    onOpenNote = { openNote(it) },
                    onOpenFolder = { stack.push(Route.Folder(it)) },
                    onNewNote = { folder -> scope.launch { notes.create(folder)?.let { openNote(it, isNew = true) } } },
                    onNewScratch = { scope.launch { notes.create("", scratch = true)?.let { openNote(it, isNew = true) } } },
                    onOpenSettings = { stack.push(Route.Settings) },
                    onOpenHidden = { trash -> stack.push(if (trash) Route.Trash else Route.Scratch) },
                    onBack = null,
                )
            }
            is Route.Folder -> {
                HomeScreen(
                    notes = notes,
                    folder = route.path,
                    onOpenNote = { openNote(it) },
                    onOpenFolder = { stack.push(Route.Folder(it)) },
                    onNewNote = { folder -> scope.launch { notes.create(folder)?.let { openNote(it, isNew = true) } } },
                    onNewScratch = { scope.launch { notes.create("", scratch = true)?.let { openNote(it, isNew = true) } } },
                    onOpenSettings = { stack.push(Route.Settings) },
                    onOpenHidden = { trash -> stack.push(if (trash) Route.Trash else Route.Scratch) },
                    onBack = { back() },
                )
            }
            Route.Settings -> SettingsScreen(notes, onBack = { back() })
            Route.Trash -> HiddenNotesScreen(notes, trash = true, onOpenNote = { openNote(it) }, onBack = { back() })
            Route.Scratch -> HiddenNotesScreen(notes, trash = false, onOpenNote = { openNote(it) }, onBack = { back() })
            is Route.Note -> {
                NoteScreen(app, route.path, route.isNew, onBack = { back() }, onTrashed = ::trashed)
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }
}
