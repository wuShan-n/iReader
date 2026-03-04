package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_TURN_STYLE_EXTRA_KEY
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
        assertEquals(
            PageTurnStyle.SCROLL_VERTICAL.storageValue,
            config.extra[PAGE_TURN_STYLE_EXTRA_KEY]
        )
    }

    @Test
    fun `legacy page turn style values should map to supported styles`() {
        assertEquals(
            PageTurnStyle.SIMULATION,
            parsePageTurnStyle(raw = "仿真翻页", mode = PageTurnMode.COVER_HORIZONTAL)
        )
        assertEquals(
            PageTurnStyle.COVER_OVERLAY,
            parsePageTurnStyle(raw = "左右覆盖", mode = PageTurnMode.COVER_HORIZONTAL)
        )
        assertEquals(
            PageTurnStyle.NO_ANIMATION,
            parsePageTurnStyle(raw = "无动效", mode = PageTurnMode.COVER_HORIZONTAL)
        )
        assertEquals(
            PageTurnStyle.SCROLL_VERTICAL,
            parsePageTurnStyle(raw = "上下滑动", mode = PageTurnMode.SCROLL_VERTICAL)
        )
    }

    @Test
    fun `withPageTurnStyle should write canonical mode and style raw values`() {
        val config = RenderConfig.ReflowText().withPageTurnStyle(PageTurnStyle.NO_ANIMATION)
        assertEquals(
            PageTurnMode.COVER_HORIZONTAL.storageValue,
            config.extra[PAGE_TURN_EXTRA_KEY]
        )
        assertEquals(
            PageTurnStyle.NO_ANIMATION.storageValue,
            config.extra[PAGE_TURN_STYLE_EXTRA_KEY]
        )
    }

    @Test
    fun `page turn style should fallback to mode default when style mismatches mode`() {
        val config = RenderConfig.ReflowText(
            extra = mapOf(
                PAGE_TURN_EXTRA_KEY to PageTurnMode.SCROLL_VERTICAL.storageValue,
                PAGE_TURN_STYLE_EXTRA_KEY to PageTurnStyle.NO_ANIMATION.storageValue
            )
        )

        assertEquals(PageTurnStyle.SCROLL_VERTICAL, config.pageTurnStyle())
    }
}
