package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import android.util.LruCache

internal class TileCache(maxBytes: Int) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    @Synchronized
    fun get(key: String): Bitmap? = cache.get(key)

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    @Synchronized
    fun evictAll() {
        cache.evictAll()
    }

    @Synchronized
    fun entryCount(): Int = cache.snapshot().size
}
