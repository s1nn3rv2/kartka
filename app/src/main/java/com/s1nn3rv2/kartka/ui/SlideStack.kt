package com.s1nn3rv2.kartka.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
class SlideStackState<T : Any>(start: T, private val scope: CoroutineScope) {
    val entries = mutableStateListOf(start)

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
        scope.launch {
            moving = true
            leave.animateTo(1f, tween(300, easing = Emphasized))
            finishPop()
        }
        return true
    }

    internal suspend fun finishPop() {
        entries.removeAt(entries.lastIndex)
        leave.snapTo(0f)
        moving = false
    }
}

@Composable
fun <T : Any> rememberSlideStack(start: T): SlideStackState<T> {
    val scope = rememberCoroutineScope()
    return remember { SlideStackState(start, scope) }
}

@Composable
fun <T : Any> SlideStack(state: SlideStackState<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val entries = state.entries

    // The gesture: the top screen follows the finger; release finishes from there, cancel slides it back.
    PredictiveBackHandler(enabled = entries.size > 1) { progress ->
        state.moving = true
        try {
            progress.collect { state.leave.snapTo(it.progress) }
            state.leave.animateTo(1f, tween(220, easing = Emphasized))
            state.finishPop()
        } catch (e: CancellationException) {
            scope.launch {
                state.leave.animateTo(0f, tween(220, easing = Emphasized))
                state.moving = false
            }
            throw e
        }
    }

    // Saved state (scroll position, text fields) of screens that were popped is dropped.
    val keys = entries.map { it.toString() }
    var previous by remember { mutableStateOf(keys) }
    LaunchedEffect(keys) {
        (previous - keys.toSet()).forEach(holder::removeState)
        previous = keys
    }

    val scrim = MaterialTheme.colorScheme.scrim
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        val top = entries.last()
        // The screen underneath only exists while something is moving, so a resting stack composes one screen.
        val below = if (state.moving) entries.getOrNull(entries.lastIndex - 1) else null
        for (entry in listOfNotNull(below, top)) {
            key(entry) {
                val isTop = entry == top
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationX = if (isTop) state.leave.value * width else -(1f - state.leave.value) * width * 0.25f
                    }.drawWithContent {
                        drawContent()
                        // The revealed screen starts slightly dimmed and brightens as the top one leaves.
                        if (!isTop) drawRect(scrim.copy(alpha = 0.15f * (1f - state.leave.value)))
                    },
                ) {
                    holder.SaveableStateProvider(entry.toString()) { content(entry) }
                }
            }
        }
    }
}
