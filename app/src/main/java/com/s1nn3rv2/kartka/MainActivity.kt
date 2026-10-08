package com.s1nn3rv2.kartka

import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.s1nn3rv2.kartka.ui.SlideStack
import com.s1nn3rv2.kartka.ui.home.HiddenNotesScreen
import com.s1nn3rv2.kartka.ui.home.HomeScreen
import com.s1nn3rv2.kartka.ui.rememberSlideStack
import com.s1nn3rv2.kartka.ui.settings.SettingsScreen
import com.s1nn3rv2.kartka.ui.share.ShareSheet
import com.s1nn3rv2.kartka.ui.note.NoteScreen
import com.s1nn3rv2.kartka.ui.onboarding.OnboardingScreen
import com.s1nn3rv2.kartka.ui.theme.KartkaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val ACTION_NEW_NOTE = "com.s1nn3rv2.kartka.NEW_NOTE"
private const val ACTION_NEW_SCRATCH = "com.s1nn3rv2.kartka.NEW_SCRATCH"

/** Screens. The back stack is a plain list of these, animated by [SlideStack]. */
sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data object Trash : Route
    data object Scratch : Route
    data class Folder(val path: String) : Route
    /** [isNew]: just created, so it opens in Edit mode with the title field focused. */
    data class Note(val path: String, val isNew: Boolean = false) : Route

    /** Plain strings, so the back stack can be saved across activity recreation. */
    fun encode(): String = when (this) {
        Home -> "home"
        Settings -> "settings"
        Trash -> "trash"
        Scratch -> "scratch"
        is Folder -> "folder:$path"
        is Note -> "note:${if (isNew) 1 else 0}:$path"
    }

    companion object {
        fun decode(s: String): Route = when {
            s == "settings" -> Settings
            s == "trash" -> Trash
            s == "scratch" -> Scratch
            s.startsWith("folder:") -> Folder(s.removePrefix("folder:"))
            // A restored note is no longer "new": the title focus and edit start already happened.
            s.startsWith("note:") -> Note(s.substringAfter(':').substringAfter(':'))
            else -> Home
        }
    }
}

class MainActivity : ComponentActivity() {
    /** Shortcut and share intents waiting for the app to handle them (also when it is already open). */
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh start carries a new request; after recreation the intent was already handled.
        if (savedInstanceState == null) pendingIntent.value = intent
        enableEdgeToEdge()
        val app = application as KartkaApp
        setContent {
            KartkaTheme {
                // The window shows through during transitions and at startup; match it to the theme instead of
                // the platform's plain light or dark background.
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect { window.setBackgroundDrawable(ColorDrawable(background)) }
                App(app, pendingIntent)
            }
        }
    }
}

@Composable
private fun App(app: KartkaApp, pendingIntent: MutableStateFlow<Intent?>) {
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
    val stack = rememberSlideStack<Route>(Route.Home, save = Route::encode, restore = Route::decode)
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
    // Launcher shortcuts start a note right away; shared text asks where it should go.
    var shared by remember { mutableStateOf<Intent?>(null) }
    val incoming by pendingIntent.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        val intent = incoming ?: return@LaunchedEffect
        pendingIntent.value = null
        when (intent.action) {
            ACTION_NEW_NOTE -> notes.create(notes.defaultFolder)?.let { openNote(it, isNew = true) }
            ACTION_NEW_SCRATCH -> notes.create("", scratch = true)?.let { openNote(it, isNew = true) }
            Intent.ACTION_SEND -> if (!intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()) shared = intent
        }
    }
    shared?.let { intent ->
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        fun saveNew(scratch: Boolean) {
            shared = null
            scope.launch {
                notes.createWith(text, if (scratch) "" else notes.defaultFolder, scratch, title = subject)?.let { openNote(it, isNew = true) }
            }
        }
        ShareSheet(
            notes = notes,
            text = text,
            onNewNote = { saveNew(scratch = false) },
            onScratch = { saveNew(scratch = true) },
            onAppendTo = { path ->
                shared = null
                scope.launch {
                    notes.append(path, text)
                    openNote(path)
                }
            },
            onDismiss = { shared = null },
        )
    }

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
                    snackbar = snackbar,
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
                    snackbar = snackbar,
                )
            }
            Route.Settings -> SettingsScreen(notes, onBack = { back() })
            Route.Trash -> HiddenNotesScreen(notes, trash = true, onOpenNote = { openNote(it) }, onBack = { back() }, snackbar = snackbar)
            Route.Scratch -> HiddenNotesScreen(notes, trash = false, onOpenNote = { openNote(it) }, onBack = { back() }, snackbar = snackbar)
            is Route.Note -> {
                NoteScreen(app, route.path, route.isNew, onBack = { back() }, onTrashed = ::trashed)
            }
        }
    }
}
