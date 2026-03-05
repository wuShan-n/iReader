package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import com.ireader.reader.api.render.RenderPolicy
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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

    private val lock = ReentrantLock()
    private var sizeBytes: Long = 0L
    private val map = LinkedHashMap<TileCacheKey, Entry>(32, 0.75f, true)

    fun get(key: TileCacheKey): Bitmap? {
        return lock.withLock {
            val entry = map[key] ?: return@withLock null
            val bitmap = entry.bitmap
            if (bitmap.isRecycled) {
                map.remove(key)
                sizeBytes -= entry.bytes.toLong()
                if (sizeBytes < 0L) sizeBytes = 0L
                return@withLock null
            }
            bitmap
        }
    }

    fun put(key: TileCacheKey, bitmap: Bitmap) {
        lock.withLock {
            if (maxBytes <= 0) return@withLock
            if (bitmap.isRecycled) return@withLock

            val bytes = bitmap.byteCount.coerceAtLeast(0)
            map.put(key, Entry(bitmap, bytes))?.let { prev ->
                sizeBytes -= prev.bytes.toLong()
            }
            sizeBytes += bytes.toLong()
            trimLocked()
        }
    }

    fun remove(key: TileCacheKey) = lock.withLock {
        map.remove(key)?.let { removed ->
            sizeBytes -= removed.bytes.toLong()
            if (sizeBytes < 0L) sizeBytes = 0L
        }
    }

    fun clear(recycleBitmaps: Boolean = false) = lock.withLock {
        if (recycleBitmaps) {
            map.values.forEach { entry ->
                runCatching {
                    if (!entry.bitmap.isRecycled) entry.bitmap.recycle()
                }
            }
        }
        map.clear()
        sizeBytes = 0L
    }

    private fun trimLocked() {
        if (maxBytes <= 0) {
            map.clear()
            sizeBytes = 0L
            return
        }

        val max = maxBytes.toLong().coerceAtLeast(0L)
        while (sizeBytes > max && map.isNotEmpty()) {
            val iterator = map.entries.iterator()
            if (!iterator.hasNext()) break
            val eldest = iterator.next()
            iterator.remove()
            sizeBytes -= eldest.value.bytes.toLong()
        }
        if (sizeBytes < 0L) sizeBytes = 0L
    }
}
