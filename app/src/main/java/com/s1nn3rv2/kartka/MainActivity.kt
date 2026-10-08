package com.s1nn3rv2.kartka

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.s1nn3rv2.kartka.ui.home.HomeScreen
import com.s1nn3rv2.kartka.ui.note.NoteScreen
import com.s1nn3rv2.kartka.ui.onboarding.OnboardingScreen
import com.s1nn3rv2.kartka.ui.theme.KartkaTheme
import kotlinx.coroutines.launch

/** Screens. The back stack is a plain list; Navigation 3 animates between entries, including predictive back. */
sealed interface Route {
    data object Home : Route
    data class Folder(val path: String) : Route
    /** [isNew]: just created, so it opens in Edit mode with the title field focused. */
    data class Note(val path: String, val isNew: Boolean = false) : Route
}

/**
 * Settings-style horizontal slide. Going deeper, the new screen slides in from the right over the old one, which
 * shifts a little left. Going back (from any edge, with the predictive gesture following the finger), the leaving
 * screen slides right on top of the one revealed beneath it. Direction comes from back stack depth.
 */
private fun AnimatedContentTransitionScope<Scene<Route>>.slide(spec: FiniteAnimationSpec<IntOffset>): ContentTransform {
    val back = targetState.previousEntries.size < initialState.previousEntries.size
    return if (back) {
        (slideInHorizontally(spec) { -it / 4 } togetherWith slideOutHorizontally(spec) { it }).apply { targetContentZIndex = -1f }
    } else {
        slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 4 }
    }
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
    val backStack = remember { mutableStateListOf<Route>(Route.Home) }
    var opening by remember { mutableStateOf(false) }
    fun openNote(path: String, isNew: Boolean = false) {
        if (opening) return // a quick double tap must not open the note twice
        opening = true
        scope.launch {
            notes.prepare(path)
            backStack += Route.Note(path, isNew)
            opening = false
        }
    }
    val activity = LocalActivity.current
    // The back stack must never become empty: back on the last screen leaves the app instead.
    val back = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else activity?.finish() }
    // Settings-style horizontal slides: a new screen slides in over the old one, which shifts a little to the left;
    // going back (and the predictive back gesture, following the finger) reverses it, with the leaving screen on top.
    // Not the expressive spring: predictive back drives this animation from the finger, and a bouncy spring
    // overshoots and swings back, so a held swipe made the page wobble the "wrong" way. Full-screen motion uses a
    // smooth, monotonic curve; bouncy springs stay for small elements like the FAB.
    val slide = tween<IntOffset>(durationMillis = 400, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    NavDisplay(
        backStack = backStack,
        onBack = { back() },
        // All three use the same direction-aware slide: on quick back swipes Navigation 3 sometimes runs the
        // forward spec for a back navigation (seen in a screen recording), so each picks its direction itself.
        transitionSpec = { slide(slide) },
        popTransitionSpec = { slide(slide) },
        predictivePopTransitionSpec = { slide(slide) },
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(
                    notes = notes,
                    folder = null,
                    onOpenNote = { openNote(it) },
                    onOpenFolder = { backStack += Route.Folder(it) },
                    onNewNote = { folder -> scope.launch { notes.create(folder)?.let { openNote(it, isNew = true) } } },
                    onNewScratch = { scope.launch { notes.create("", scratch = true)?.let { openNote(it, isNew = true) } } },
                    onBack = null,
                )
            }
            entry<Route.Folder> { route ->
                HomeScreen(
                    notes = notes,
                    folder = route.path,
                    onOpenNote = { openNote(it) },
                    onOpenFolder = { backStack += Route.Folder(it) },
                    onNewNote = { folder -> scope.launch { notes.create(folder)?.let { openNote(it, isNew = true) } } },
                    onNewScratch = { scope.launch { notes.create("", scratch = true)?.let { openNote(it, isNew = true) } } },
                    onBack = { back() },
                )
            }
            entry<Route.Note> { route ->
                NoteScreen(app, route.path, route.isNew, onBack = { back() })
            }
        },
    )
}
