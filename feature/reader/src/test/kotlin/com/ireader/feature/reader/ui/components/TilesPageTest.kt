package com.ireader.feature.reader.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesPageTest {

    @Test
    fun `fixed page turn should be enabled at rest`() {
        assertTrue(
            isFixedPageTurnAllowed(
                zoom = 1f,
                offset = Offset.Zero,
                isGesturing = false,
                panTolerancePx = 8f
            )
        )
    }

    @Test
    fun `fixed page turn should be disabled while gesturing or transformed`() {
        assertFalse(
            isFixedPageTurnAllowed(
                zoom = 1f,
                offset = Offset.Zero,
                isGesturing = true,
                panTolerancePx = 8f
            )
        )
        assertFalse(
            isFixedPageTurnAllowed(
                zoom = 1.05f,
                offset = Offset.Zero,
                isGesturing = false,
                panTolerancePx = 8f
            )
        )
        assertFalse(
            isFixedPageTurnAllowed(
                zoom = 1f,
                offset = Offset(12f, 0f),
                isGesturing = false,
                panTolerancePx = 8f
            )
        )
    }
}
