package com.ireader.engines.epub.internal.pagination

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PaginationSignatureTest {

    @Test
    fun `signature ignores config extra`() {
        val constraints = LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 2.75f,
            fontScale = 1.0f
        )
        val base = RenderConfig.ReflowText(extra = mapOf("a" to "1"))
        val changed = base.copy(extra = mapOf("b" to "2"))

        assertEquals(
            PaginationSignature.of(base, constraints),
            PaginationSignature.of(changed, constraints)
        )
    }

    @Test
    fun `signature changes when pagination fields change`() {
        val constraints = LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 2.75f,
            fontScale = 1.0f
        )
        val base = RenderConfig.ReflowText(fontSizeSp = 18f)
        val changed = base.copy(fontSizeSp = 20f)

        assertNotEquals(
            PaginationSignature.of(base, constraints),
            PaginationSignature.of(changed, constraints)
        )
    }

    @Test
    fun `signature changes when constraints change`() {
        val config = RenderConfig.ReflowText()
        val first = LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 2.75f,
            fontScale = 1.0f
        )
        val second = first.copy(viewportWidthPx = 1200)

        assertNotEquals(
            PaginationSignature.of(config, first),
            PaginationSignature.of(config, second)
        )
    }
}
