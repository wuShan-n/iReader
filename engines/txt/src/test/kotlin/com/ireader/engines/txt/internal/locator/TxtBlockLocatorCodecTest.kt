package com.ireader.engines.txt.internal.locator

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxtBlockLocatorCodecTest {

    @Test
    fun `locatorForOffset should encode txt offset`() {
        val locator = TxtBlockLocatorCodec.locatorForOffset(offset = 2_121L, maxOffset = 5_000L)
        assertEquals(LocatorSchemes.TXT_OFFSET, locator.scheme)
        assertEquals("2121", locator.value)
    }

    @Test
    fun `parseOffset should decode txt offset and clamp by max`() {
        val locator = Locator(LocatorSchemes.TXT_OFFSET, "2121")
        assertEquals(2_121L, TxtBlockLocatorCodec.parseOffset(locator))
        assertEquals(2_000L, TxtBlockLocatorCodec.parseOffset(locator, maxOffset = 2_000L))
    }

    @Test
    fun `parseOffset should decode legacy txt block`() {
        val legacy = Locator(LocatorSchemes.TXT_BLOCK, "2048:73")
        assertEquals(2_121L, TxtBlockLocatorCodec.parseOffset(legacy))
    }

    @Test
    fun `parseOffset should reject invalid values`() {
        assertNull(TxtBlockLocatorCodec.parseOffset(Locator(LocatorSchemes.TXT_BLOCK, "a:b")))
        assertNull(TxtBlockLocatorCodec.parseOffset(Locator(LocatorSchemes.TXT_BLOCK, "20")))
        assertNull(TxtBlockLocatorCodec.parseOffset(Locator(LocatorSchemes.TXT_BLOCK, "10:-1")))
        assertNull(TxtBlockLocatorCodec.parseOffset(Locator(LocatorSchemes.TXT_BLOCK, "10:4096")))
        assertNull(TxtBlockLocatorCodec.parseOffset(Locator(LocatorSchemes.TXT_OFFSET, "-1")))
    }
}
