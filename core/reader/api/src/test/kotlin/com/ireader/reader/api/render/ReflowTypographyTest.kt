package com.ireader.reader.api.render

import org.junit.Assert.assertEquals
import org.junit.Test

class ReflowTypographyTest {

    @Test
    fun `compact inset should reduce page padding with floor`() {
        val compact = RenderConfig.ReflowText(
            pagePaddingDp = 4f,
            pageInsetMode = PageInsetMode.COMPACT
        )

        assertEquals(6f, compact.effectivePagePaddingDp(), 0f)
    }

    @Test
    fun `compact inset should reduce paragraph spacing with floor`() {
        val compact = RenderConfig.ReflowText(
            paragraphSpacingDp = 2f,
            pageInsetMode = PageInsetMode.COMPACT
        )

        assertEquals(1.6f, compact.effectiveParagraphSpacingDp(), 0.0001f)
    }

    @Test
    fun `typography spec should use effective values`() {
        val config = RenderConfig.ReflowText(
            paragraphSpacingDp = 2f,
            pagePaddingDp = 10f,
            pageInsetMode = PageInsetMode.COMPACT
        )

        val spec = config.toTypographySpec()

        assertEquals(7.5f, spec.pagePaddingDp, 0f)
        assertEquals(1.6f, spec.paragraphSpacingDp, 0.0001f)
    }
}
