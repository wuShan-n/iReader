package com.ireader.engines.pdf.internal.util

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfLocatorsTest {

    @Test
    fun `progression maps page indices to percent`() {
        val first = progressionForPage(pageIndex = 0, pageCount = 10)
        val middle = progressionForPage(pageIndex = 5, pageCount = 10)
        val last = progressionForPage(pageIndex = 9, pageCount = 10)

        assertEquals(0.0, first.percent, 0.0001)
        assertEquals(5.0 / 9.0, middle.percent, 0.0001)
        assertEquals(1.0, last.percent, 0.0001)
    }

    @Test
    fun `locator parser rejects non-pdf scheme`() {
        val locator = Locator(scheme = LocatorSchemes.TXT_OFFSET, value = "10")
        assertNull(locator.toPdfPageIndexOrNull(pageCount = 100))
    }

    @Test
    fun `locator parser clamps page index`() {
        val locator = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "999")
        assertEquals(4, locator.toPdfPageIndexOrNull(pageCount = 5))
    }

    @Test
    fun `char locator helpers encode offsets`() {
        val start = startCharLocator(pageIndex = 2, pageCount = 10, charStart = 11)
        val end = endCharLocator(pageIndex = 2, pageCount = 10, charEnd = 19)

        assertEquals("11", start.extras[PdfLocatorExtras.CharStart])
        assertEquals("11", start.extras[PdfLocatorExtras.CharIndex])
        assertEquals("19", end.extras[PdfLocatorExtras.CharEnd])
        assertEquals("19", end.extras[PdfLocatorExtras.CharIndex])
        assertEquals(11, start.charStartOrDefault(defaultValue = 0))
        assertEquals(19, end.charEndOrDefault(defaultValue = 0))
    }
}
