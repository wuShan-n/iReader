package com.ireader.engines.txt.internal.render

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TxtTextMappingTest {

    @Test
    fun `locatorAt maps page local index to global offset`() {
        val mapping = TxtTextMapping(pageStart = 100L, pageEnd = 150L)
        val locator = mapping.locatorAt(12)
        assertEquals(LocatorSchemes.TXT_OFFSET, locator.scheme)
        assertEquals("112", locator.value)
    }

    @Test
    fun `charRangeFor returns local range when locator range is on page`() {
        val mapping = TxtTextMapping(pageStart = 200L, pageEnd = 260L)
        val range = LocatorRange(
            start = Locator(LocatorSchemes.TXT_OFFSET, "205"),
            end = Locator(LocatorSchemes.TXT_OFFSET, "210")
        )
        val local = mapping.charRangeFor(range)
        assertNotNull(local)
        assertEquals(5, local!!.first)
        assertEquals(9, local.last)
    }

    @Test
    fun `charRangeFor returns null when range is outside page`() {
        val mapping = TxtTextMapping(pageStart = 20L, pageEnd = 40L)
        val range = LocatorRange(
            start = Locator(LocatorSchemes.TXT_OFFSET, "18"),
            end = Locator(LocatorSchemes.TXT_OFFSET, "25")
        )
        assertNull(mapping.charRangeFor(range))
    }
}

