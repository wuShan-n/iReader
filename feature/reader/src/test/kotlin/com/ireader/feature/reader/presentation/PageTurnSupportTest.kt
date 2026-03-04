package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_TURN_STYLE_EXTRA_KEY
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnSupportTest {

    @Test
    fun `page turn mode should fallback to cover horizontal`() {
        assertEquals(PageTurnMode.COVER_HORIZONTAL, parsePageTurnMode("cover_horizontal"))
        assertEquals(PageTurnMode.COVER_HORIZONTAL, parsePageTurnMode("unknown"))
        assertEquals(PageTurnMode.COVER_HORIZONTAL, parsePageTurnMode(null))
    }

    @Test
    fun `withPageTurnMode should write canonical raw value`() {
        val config = RenderConfig.ReflowText().withPageTurnMode(PageTurnMode.COVER_HORIZONTAL)
        assertEquals(
            PageTurnMode.COVER_HORIZONTAL.storageValue,
            config.extra[PAGE_TURN_EXTRA_KEY]
        )
        assertEquals(
            PageTurnStyle.COVER_OVERLAY.storageValue,
            config.extra[PAGE_TURN_STYLE_EXTRA_KEY]
        )
    }

    @Test
    fun `page turn style should map canonical values`() {
        assertEquals(
            PageTurnStyle.SIMULATION,
            parsePageTurnStyle(raw = "simulation", mode = PageTurnMode.COVER_HORIZONTAL)
        )
        assertEquals(
            PageTurnStyle.COVER_OVERLAY,
            parsePageTurnStyle(raw = "cover_overlay", mode = PageTurnMode.COVER_HORIZONTAL)
        )
        assertEquals(
            PageTurnStyle.NO_ANIMATION,
            parsePageTurnStyle(raw = "no_animation", mode = PageTurnMode.COVER_HORIZONTAL)
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
    fun `page turn style should fallback to default when raw is invalid`() {
        val config = RenderConfig.ReflowText(
            extra = mapOf(
                PAGE_TURN_EXTRA_KEY to PageTurnMode.COVER_HORIZONTAL.storageValue,
                PAGE_TURN_STYLE_EXTRA_KEY to "invalid-style"
            )
        )

        assertEquals(PageTurnStyle.COVER_OVERLAY, config.pageTurnStyle())
    }
}
