package com.s1nn3rv2.kartka.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * A back stack of screens that slide like Android Settings. The top screen's position is driven directly by the
 * predictive back gesture (and on release continues from wherever the finger left it), so quick flicks and slow
 * drags behave the same. Navigation 3 animated this through its own transitions and picked the wrong one on quick
 * swipes, which is why this is hand-rolled.
 */
class SlideStackState<T : Any>(initial: List<T>, private val scope: CoroutineScope) {
    val entries = mutableStateListOf<T>().apply { addAll(initial) }

    /** How far the top screen has slid off to the right: 0 = fully shown, 1 = gone. */
    internal val leave = Animatable(0f)
    internal var moving by mutableStateOf(false)

    fun push(entry: T) {
        entries += entry
        scope.launch {
            moving = true
            leave.snapTo(1f)
            leave.animateTo(0f, tween(400, easing = Emphasized))
            moving = false
        }
    }

    /** Back with an animation (back arrow, Done). Returns false when already on the last screen. */
    fun pop(): Boolean {
        if (entries.size <= 1) return false
        val leaving = entries.last()
        scope.launch { slideOut(leaving) { leave.animateTo(1f, tween(300, easing = Emphasized)) } }
        return true
    }

    /**
     * Runs the slide-out [animation], then removes [leaving] (exactly that entry, not whatever is on top by then).
     * If a push interrupts the animation, the entry is still removed, but the push keeps control of the offset.
     */
    internal suspend fun slideOut(leaving: T, animation: suspend () -> Unit) {
        moving = true
        try {
            animation()
        } catch (e: CancellationException) {
            remove(leaving)
            throw e
        }
        remove(leaving)
        leave.snapTo(0f)
        moving = false
    }

    private fun remove(entry: T) {
        val i = entries.lastIndexOf(entry)
        if (i >= 0) entries.removeAt(i)
    }
}

/**
 * The stack is saved as strings ([save]/[restore]), so it survives activity recreation (dark mode switch, rotation,
 * foldable resize) instead of dropping back to [start].
 */
@Composable
fun <T : Any> rememberSlideStack(start: T, save: (T) -> String, restore: (String) -> T): SlideStackState<T> {
    val scope = rememberCoroutineScope()
    return rememberSaveable(
        saver = listSaver(
            save = { stack -> stack.entries.map(save) },
            restore = { saved -> SlideStackState(saved.map(restore), scope) },
        ),
    ) { SlideStackState(listOf(start), scope) }
}

@Composable
fun <T : Any> SlideStack(state: SlideStackState<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val entries = state.entries

    // The gesture: the top screen follows the finger; release finishes from there, cancel slides it back.
    PredictiveBackHandler(enabled = entries.size > 1) { progress ->
        val leaving = entries.last()
        state.moving = true
        try {
            progress.collect { state.leave.snapTo(it.progress) }
        } catch (e: CancellationException) {
            // Gesture cancelled: slide the page back into place.
            scope.launch {
                state.leave.animateTo(0f, tween(220, easing = Emphasized))
                state.moving = false
            }
            throw e
        }
        state.slideOut(leaving) { state.leave.animateTo(1f, tween(220, easing = Emphasized)) }
    }

    // Saved state (scroll position, text fields) of screens that were popped is dropped. Keys include the position,
    // so the same screen twice in the stack (a note linking to itself and back) never shares a key.
    val keys = entries.mapIndexed { i, e -> "$i:$e" }
    var previous by remember { mutableStateOf(keys) }
    LaunchedEffect(keys) {
        (previous - keys.toSet()).forEach(holder::removeState)
        previous = keys
    }

    val scrim = MaterialTheme.colorScheme.scrim
    Box(modifier.fillMaxSize()) {
        // The screen underneath only exists while something is moving, so a resting stack composes one screen.
        val shown = if (state.moving && entries.size > 1) listOf(entries.lastIndex - 1, entries.lastIndex) else listOf(entries.lastIndex)
        for (index in shown) {
            val entry = entries[index]
            key(keys[index]) {
                val isTop = index == entries.lastIndex
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        // Read in the layer block (draw phase), so sliding never recomposes the screens.
                        translationX = if (isTop) state.leave.value * size.width else -(1f - state.leave.value) * size.width * 0.25f
                    }.drawWithContent {
                        drawContent()
                        // The revealed screen starts slightly dimmed and brightens as the top one leaves.
                        if (!isTop) drawRect(scrim.copy(alpha = 0.15f * (1f - state.leave.value)))
                    },
                ) {
                    holder.SaveableStateProvider(keys[index]) { content(entry) }
                }
            }
        }
    }
}
