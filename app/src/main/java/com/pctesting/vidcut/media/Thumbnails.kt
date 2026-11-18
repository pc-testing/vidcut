package com.pctesting.vidcut.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small frame grabs for the timeline and project list, cached in memory. */
object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(path: String, isImage: Boolean, timeMs: Long, maxSide: Int = 256): Bitmap? {
        val key = "$path@$timeMs@$maxSide"
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching { if (isImage) decodeImage(path, maxSide) else grabFrame(path, timeMs, maxSide) }.getOrNull()
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun decodeImage(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun grabFrame(path: String, timeMs: Long, maxSide: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val frame = r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val scale = maxSide.toFloat() / maxOf(frame.width, frame.height)
            if (scale >= 1f) frame else Bitmap.createScaledBitmap(
                frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true,
            ).also { if (it !== frame) frame.recycle() }
        } finally {
            runCatching { r.release() }
        }
    }
}

@Composable
fun rememberThumbnail(path: String, isImage: Boolean, timeMs: Long, maxSide: Int = 256): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, path, isImage, timeMs, maxSide) {
        value = Thumbnails.load(path, isImage, timeMs, maxSide)?.asImageBitmap()
    }
