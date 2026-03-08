package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.ReadiumLocatorExtras
import com.ireader.engines.epub.internal.locator.ReadiumLocatorSchemes
import com.ireader.reader.model.Locator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubSearchProviderContractTest {

    @Test
    fun `isAtOrAfter should compare by position when available`() {
        val start = readiumLocator(position = 10, progression = 0.2)
        val next = readiumLocator(position = 11, progression = 0.21)
        val previous = readiumLocator(position = 9, progression = 0.19)

        assertTrue(isAtOrAfter(next, start))
        assertFalse(isAtOrAfter(previous, start))
    }

    @Test
    fun `isAtOrAfter should fallback to progression when position missing`() {
        val start = readiumLocator(position = null, progression = 0.5)
        val next = readiumLocator(position = null, progression = 0.51)
        val previous = readiumLocator(position = null, progression = 0.49)

        assertTrue(isAtOrAfter(next, start))
        assertFalse(isAtOrAfter(previous, start))
    }

    @Test
    fun `isAtOrAfter should not block when schemes differ`() {
        val candidate = readiumLocator(position = 1, progression = 0.1)
        val start = Locator(
            scheme = "txt.stable.anchor",
            value = "100:3"
        )

        assertTrue(isAtOrAfter(candidate, start))
    }

    private fun readiumLocator(position: Int?, progression: Double): Locator {
        val extras = buildMap {
            position?.let { put(ReadiumLocatorExtras.POSITION, it.toString()) }
            put(ReadiumLocatorExtras.TOTAL_PROGRESSION, progression.toString())
        }
        return Locator(
            scheme = ReadiumLocatorSchemes.READIUM_LOCATOR_JSON,
            value = """{"position":"${position ?: "none"}","progression":"$progression"}""",
            extras = extras
        )
    }
}
