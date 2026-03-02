package com.ireader.feature.library.ui.components

import android.graphics.Bitmap
import android.util.LruCache

internal object CoverBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(48) {}

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}
