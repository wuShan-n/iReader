package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.pagination.ReflowPaginationProfile
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtReflowConfigAdapterTest {

    @Test
    fun `cjk and hanging punctuation toggles should not affect txt effective profile`() {
        val constraints = LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 3f,
            fontScale = 1f
        )
        val base = RenderConfig.ReflowText()
        val toggled = base.copy(
            cjkLineBreakStrict = !base.cjkLineBreakStrict,
            hangingPunctuation = !base.hangingPunctuation
        )

        val baseKey = ReflowPaginationProfile.keyFor("doc", constraints, base.toTxtEffectiveConfig())
        val toggledKey = ReflowPaginationProfile.keyFor("doc", constraints, toggled.toTxtEffectiveConfig())

        assertEquals(baseKey, toggledKey)
    }
}
