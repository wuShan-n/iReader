package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_TURN_STYLE_EXTRA_KEY
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubConfigSupportTest {

    @Test
    fun `epub config normalization should keep supported fields and neutralize unsupported fields`() {
        val current = RenderConfig.ReflowText(
            fontSizeSp = 20f,
            lineHeightMult = 1.8f,
            paragraphSpacingDp = 10f,
            pagePaddingDp = 20f,
            breakStrategy = BreakStrategyMode.BALANCED,
            includeFontPadding = false,
            extra = mapOf(
                PAGE_TURN_EXTRA_KEY to "cover_horizontal",
                PAGE_TURN_STYLE_EXTRA_KEY to "cover_overlay"
            )
        )
        val requested = current.copy(
            fontSizeSp = 22f,
            lineHeightMult = 2.0f,
            breakStrategy = BreakStrategyMode.HIGH_QUALITY,
            includeFontPadding = true,
            extra = current.extra + (PAGE_TURN_STYLE_EXTRA_KEY to "simulation")
        )

        val normalized = normalizeEpubEffectiveReflowConfig(
            requested = requested,
            current = current
        )

        assertEquals(22f, normalized.fontSizeSp)
        assertEquals(2.0f, normalized.lineHeightMult)
        assertEquals(current.breakStrategy, normalized.breakStrategy)
        assertEquals(current.includeFontPadding, normalized.includeFontPadding)
        assertEquals(current.extra[PAGE_TURN_EXTRA_KEY], normalized.extra[PAGE_TURN_EXTRA_KEY])
        assertEquals(current.extra[PAGE_TURN_STYLE_EXTRA_KEY], normalized.extra[PAGE_TURN_STYLE_EXTRA_KEY])
    }
}
