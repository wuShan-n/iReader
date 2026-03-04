package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.PageTurnMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnAnimationPolicyTest {

    @Test
    fun `cover overlay should resolve to overlay animation`() {
        assertEquals(
            PageTurnAnimationKind.COVER_OVERLAY,
            resolvePageTurnAnimationKind(
                mode = PageTurnMode.COVER_HORIZONTAL,
                style = PageTurnStyle.COVER_OVERLAY
            )
        )
    }

    @Test
    fun `simulation should resolve to simulation animation`() {
        assertEquals(
            PageTurnAnimationKind.SIMULATION,
            resolvePageTurnAnimationKind(
                mode = PageTurnMode.COVER_HORIZONTAL,
                style = PageTurnStyle.SIMULATION
            )
        )
    }

    @Test
    fun `no animation style should resolve to none animation`() {
        assertEquals(
            PageTurnAnimationKind.NONE,
            resolvePageTurnAnimationKind(
                mode = PageTurnMode.COVER_HORIZONTAL,
                style = PageTurnStyle.NO_ANIMATION
            )
        )
    }

}
