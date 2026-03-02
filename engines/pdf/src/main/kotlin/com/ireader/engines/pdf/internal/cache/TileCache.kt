package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import com.ireader.reader.api.render.RenderPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class TileCacheKey(
    val pageIndex: Int,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val scaleMilli: Int,
    val quality: RenderPolicy.Quality,
    val rotationDegrees: Int,
    val zoomBucketMilli: Int
)

internal class TileCache(
    private val maxBytes: Int
) {
    private data class Entry(
        val bitmap: Bitmap,
        val bytes: Int
    )

    private val mutex = Mutex()
    private var sizeBytes: Int = 0
    private val map = LinkedHashMap<TileCacheKey, Entry>(32, 0.75f, true)

    suspend fun get(key: TileCacheKey): Bitmap? = mutex.withLock {
        map[key]?.bitmap
    }

    suspend fun put(key: TileCacheKey, bitmap: Bitmap) = mutex.withLock {
        val bytes = bitmap.byteCount.coerceAtLeast(0)
        map.put(key, Entry(bitmap, bytes))?.let { prev ->
            sizeBytes -= prev.bytes
            if (prev.bitmap != bitmap && !prev.bitmap.isRecycled) {
                prev.bitmap.recycle()
            }
        }
        sizeBytes += bytes
        trimLocked()
    }

    suspend fun clear() = mutex.withLock {
        map.values.forEach { entry ->
            if (!entry.bitmap.isRecycled) entry.bitmap.recycle()
        }
        map.clear()
        sizeBytes = 0
    }

    private fun trimLocked() {
        while (sizeBytes > maxBytes && map.isNotEmpty()) {
            val iterator = map.entries.iterator()
            if (!iterator.hasNext()) break
            val eldest = iterator.next()
            iterator.remove()
            sizeBytes -= eldest.value.bytes
            if (!eldest.value.bitmap.isRecycled) {
                eldest.value.bitmap.recycle()
            }
        }
    }
}
