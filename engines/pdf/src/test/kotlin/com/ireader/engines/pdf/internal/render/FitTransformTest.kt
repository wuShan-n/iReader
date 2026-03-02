package com.ireader.engines.pdf.internal.render

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class FitTransformTest {

    @Test
    fun `fit width keeps viewport width`() {
        val transform = computePageTransform(
            pageWidthPt = 600,
            pageHeightPt = 900,
            config = RenderConfig.FixedPage(
                fitMode = RenderConfig.FitMode.FIT_WIDTH,
                zoom = 1f
            ),
            constraints = LayoutConstraints(
                viewportWidthPx = 1080,
                viewportHeightPx = 1920,
                density = 3f,
                fontScale = 1f
            )
        )

        assertEquals(1080, transform.pageWidthPx)
        assertEquals(1620, transform.pageHeightPx)
    }

    @Test
    fun `rotation swaps width and height before fit`() {
        val transform = computePageTransform(
            pageWidthPt = 600,
            pageHeightPt = 900,
            config = RenderConfig.FixedPage(
                fitMode = RenderConfig.FitMode.FIT_WIDTH,
                rotationDegrees = 90
            ),
            constraints = LayoutConstraints(
                viewportWidthPx = 1000,
                viewportHeightPx = 1600,
                density = 2f,
                fontScale = 1f
            )
        )

        assertEquals(1000, transform.pageWidthPx)
        assertEquals(667, transform.pageHeightPx)
    }
}

