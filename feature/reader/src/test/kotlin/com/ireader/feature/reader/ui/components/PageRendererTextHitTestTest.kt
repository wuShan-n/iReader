package com.ireader.feature.reader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRendererTextHitTestTest {

    @Test
    fun `coerceVisibleTextOffset should clamp offsets beyond visible text`() {
        assertEquals(12, coerceVisibleTextOffset(charOffset = 40, visibleTextLength = 12))
    }

    @Test
    fun `coerceVisibleTextOffset should reject negative offsets`() {
        assertNull(coerceVisibleTextOffset(charOffset = -1, visibleTextLength = 12))
    }
}
