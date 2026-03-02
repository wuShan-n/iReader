package com.ireader.engines.pdf.internal.cache

import com.ireader.engines.pdf.internal.render.zoomBucketMilli
import com.ireader.reader.api.render.RenderPolicy
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TileCacheKeyTest {

    @Test
    fun `zoom bucket is stable`() {
        val a = zoomBucketMilli(1.0f)
        val b = zoomBucketMilli(1.0004f)
        val c = zoomBucketMilli(1.1f)

        assert(a == b)
        assertNotEquals(a, c)
    }

    @Test
    fun `cache key separates quality`() {
        val base = TileCacheKey(
            pageIndex = 1,
            leftPx = 0,
            topPx = 0,
            widthPx = 512,
            heightPx = 512,
            scaleMilli = 1000,
            quality = RenderPolicy.Quality.DRAFT,
            rotationDegrees = 0,
            zoomBucketMilli = 1000
        )
        val final = base.copy(quality = RenderPolicy.Quality.FINAL)
        assertNotEquals(base, final)
    }
}

