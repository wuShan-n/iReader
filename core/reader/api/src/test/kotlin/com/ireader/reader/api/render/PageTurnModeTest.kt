package com.ireader.reader.api.render

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnModeTest {

    @Test
    fun `fromStorageValue should decode known values`() {
        assertEquals(PageTurnMode.COVER_HORIZONTAL, PageTurnMode.fromStorageValue("cover_horizontal"))
        assertEquals(PageTurnMode.SCROLL_VERTICAL, PageTurnMode.fromStorageValue("scroll_vertical"))
    }

    @Test
    fun `fromStorageValue should fallback for unknown values`() {
        assertEquals(PageTurnMode.COVER_HORIZONTAL, PageTurnMode.fromStorageValue("unknown"))
        assertEquals(PageTurnMode.COVER_HORIZONTAL, PageTurnMode.fromStorageValue(null))
    }
}
