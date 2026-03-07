package com.ireader.feature.reader.ui.components

import com.ireader.core.common.android.typography.AndroidTextLayoutKind
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
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

    @Test
    fun `resolveTextLayoutKind should mark txt locators as txt`() {
        assertEquals(
            AndroidTextLayoutKind.TXT,
            resolveTextLayoutKind(Locator(scheme = LocatorSchemes.TXT_OFFSET, value = "0"))
        )
        assertEquals(
            AndroidTextLayoutKind.TXT,
            resolveTextLayoutKind(Locator(scheme = LocatorSchemes.TXT_BLOCK, value = "0:12"))
        )
    }

    @Test
    fun `resolveTextLayoutKind should keep non txt locators generic`() {
        assertEquals(
            AndroidTextLayoutKind.GENERIC,
            resolveTextLayoutKind(Locator(scheme = LocatorSchemes.EPUB_CFI, value = "/6/4!"))
        )
    }
}
