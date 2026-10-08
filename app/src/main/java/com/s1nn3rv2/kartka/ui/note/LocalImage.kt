package com.s1nn3rv2.kartka.ui.note

import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decoded images, so scrolling back past an image doesn't decode it again. */
private object ImageCache {
    // ponytail: keyed by path only; an image replaced on disk shows the old one until the app restarts.
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    operator fun get(file: File?): ImageBitmap? = file?.let { cache[it.path] }

    /** Decodes [file] at most [maxWidth] pixels wide; null when it's missing or not an image. */
    fun load(file: File, maxWidth: Int = 1440): ImageBitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val w = info.size.width
            if (w > maxWidth) decoder.setTargetSize(maxWidth, info.size.height * maxWidth / w)
        }.asImageBitmap()
    }.getOrNull()?.also { cache.put(file.path, it) }
}

/** An image from the notes folder, full width; a placeholder while it loads, its path when it can't be shown. */
@Composable
fun LocalImage(file: File?, url: String, alt: String, modifier: Modifier = Modifier) {
    // Unit = still loading, null = failed.
    val image by produceState<Any?>(ImageCache[file] ?: Unit, file) {
        if (value == Unit) value = file?.let { withContext(Dispatchers.IO) { ImageCache.load(it) } }
    }
    val shape = RoundedCornerShape(12.dp)
    when (val i = image) {
        is ImageBitmap -> Image(
            i,
            contentDescription = alt.ifEmpty { null },
            contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth().aspectRatio(i.width.toFloat() / i.height).clip(shape),
        )
        Unit -> Box(modifier.fillMaxWidth().height(160.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer))
        else -> Text(
            "Image not found: $url",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}
