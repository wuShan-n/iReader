package com.ireader.engines.common.android.layout

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextPaintFactoryTest {

    private val constraints = LayoutConstraints(
        viewportWidthPx = 1080,
        viewportHeightPx = 2400,
        density = 3f,
        fontScale = 1f
    )

    @Test
    fun `create should apply text color from extra config`() {
        val config = RenderConfig.ReflowText(
            extra = mapOf(READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY to 0xFF123456.toInt().toString())
        )

        val paint = TextPaintFactory.create(config = config, constraints = constraints)

        assertEquals(0xFF123456.toInt(), paint.color)
    }
}
