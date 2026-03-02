package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import com.ireader.reader.api.render.RenderPolicy

internal class TieredTileCache(
    private val draftCache: TileCache,
    private val finalCache: TileCache
) {
    fun get(key: String, quality: RenderPolicy.Quality): Bitmap? {
        return when (quality) {
            RenderPolicy.Quality.DRAFT -> draftCache.get(key)
            RenderPolicy.Quality.FINAL -> finalCache.get(key)
        }
    }

    fun put(key: String, bitmap: Bitmap, quality: RenderPolicy.Quality) {
        when (quality) {
            RenderPolicy.Quality.DRAFT -> draftCache.put(key, bitmap)
            RenderPolicy.Quality.FINAL -> finalCache.put(key, bitmap)
        }
    }

    fun evictAll() {
        draftCache.evictAll()
        finalCache.evictAll()
    }

    fun entryCount(quality: RenderPolicy.Quality): Int {
        return when (quality) {
            RenderPolicy.Quality.DRAFT -> draftCache.entryCount()
            RenderPolicy.Quality.FINAL -> finalCache.entryCount()
        }
    }
}
