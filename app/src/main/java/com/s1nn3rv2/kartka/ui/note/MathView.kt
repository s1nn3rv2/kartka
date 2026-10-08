package com.s1nn3rv2.kartka.ui.note

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import ru.noties.jlatexmath.JLatexMathDrawable

/** LaTeX drawn to bitmaps, cached; null when the formula has an error. */
object MathRender {
    private class Rendered(val image: ImageBitmap?)

    private val cache = LruCache<String, Rendered>(300)

    // ponytail: renders on the main thread during composition; move off it if notes with many formulas stutter.
    fun render(latex: String, textSizePx: Float, color: Int): ImageBitmap? {
        val key = "$textSizePx|$color|$latex"
        cache[key]?.let { return it.image }
        val image = runCatching {
            val d = JLatexMathDrawable.builder(latex).textSize(textSizePx).color(color).build()
            val w = d.intrinsicWidth.coerceAtLeast(1)
            val h = d.intrinsicHeight.coerceAtLeast(1)
            d.setBounds(0, 0, w, h)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { d.draw(Canvas(it)) }.asImageBitmap()
        }.getOrNull()
        cache.put(key, Rendered(image))
        return image
    }
}

/** A `$$` block: centered, scrolling sideways when wider than the screen; the source when it can't be drawn. */
@Composable
fun MathBlockView(latex: String, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography.bodyLarge
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val px = with(LocalDensity.current) { type.fontSize.toPx() * 1.15f }
    val image = remember(latex, px, color) { MathRender.render(latex, px, color) }
    Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = latex)
        } else {
            Text(latex, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.error)
        }
    }
}
