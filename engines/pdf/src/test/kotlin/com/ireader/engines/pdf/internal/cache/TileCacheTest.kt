package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import com.ireader.reader.api.render.RenderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TileCacheTest {

    @Test
    fun `clear without recycle keeps bitmap alive`() {
        val cache = TileCache(maxBytes = 1024 * 1024)
        val key = key()
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        cache.put(key, bitmap)
        cache.clear(recycleBitmaps = false)

        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun `clear with recycle recycles bitmap`() {
        val cache = TileCache(maxBytes = 1024 * 1024)
        val key = key()
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        cache.put(key, bitmap)
        cache.clear(recycleBitmaps = true)

        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun `get removes recycled entries`() {
        val cache = TileCache(maxBytes = 1024 * 1024)
        val key = key()
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        cache.put(key, bitmap)
        bitmap.recycle()

        assertNull(cache.get(key))
        assertNull(cache.get(key))
    }

    private fun key(): TileCacheKey {
        return TileCacheKey(
            pageIndex = 1,
            leftPx = 0,
            topPx = 0,
            widthPx = 128,
            heightPx = 128,
            scaleMilli = 1000,
            quality = RenderPolicy.Quality.DRAFT,
            rotationDegrees = 0,
            zoomBucketMilli = 1000
        )
    }
}
