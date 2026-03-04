package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnSupportTest {

    @Test
    fun `legacy page turn values should map to supported modes`() {
        assertEquals(PageTurnMode.COVER_HORIZONTAL, parsePageTurnMode("仿真翻页"))
        assertEquals(PageTurnMode.COVER_HORIZONTAL, parsePageTurnMode("无动效"))
        assertEquals(PageTurnMode.COVER_HORIZONTAL, parsePageTurnMode("左右覆盖"))
        assertEquals(PageTurnMode.SCROLL_VERTICAL, parsePageTurnMode("上下滑动"))
        assertEquals(PageTurnMode.SCROLL_VERTICAL, parsePageTurnMode("上下滚动"))
    }

    @Test
    fun `withPageTurnMode should write canonical raw value`() {
        val config = RenderConfig.ReflowText().withPageTurnMode(PageTurnMode.SCROLL_VERTICAL)
        assertEquals(
            PageTurnMode.SCROLL_VERTICAL.storageValue,
            config.extra[PAGE_TURN_EXTRA_KEY]
        )
    }
}
