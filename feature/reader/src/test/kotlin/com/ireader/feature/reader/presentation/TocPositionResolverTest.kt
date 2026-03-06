package com.ireader.feature.reader.presentation

import com.ireader.reader.model.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TocPositionResolverTest {

    @Test
    fun `should select toc item by matching href and nearest position`() {
        val items = listOf(
            TocItem(
                title = "第1章",
                locatorEncoded = "l1",
                depth = 0,
                href = "chapter1.xhtml",
                position = 1
            ),
            TocItem(
                title = "第2章",
                locatorEncoded = "l2",
                depth = 0,
                href = "chapter2.xhtml",
                position = 5
            ),
            TocItem(
                title = "第2章-中段",
                locatorEncoded = "l3",
                depth = 1,
                href = "chapter2.xhtml",
                position = 8
            )
        )

        val current = Locator(
            scheme = "readium.locator.json",
            value = "current",
            extras = mapOf(
                TOC_EXTRA_HREF to "chapter2.xhtml#frag",
                TOC_EXTRA_POSITION to "9"
            )
        )

        assertEquals(2, resolveActiveTocIndex(items = items, currentLocator = current))
    }

    @Test
    fun `should fallback to first matching href when position is missing`() {
        val items = listOf(
            TocItem("第1章", "l1", 0, href = "chapter1.xhtml"),
            TocItem("第2章", "l2", 0, href = "chapter2.xhtml")
        )
        val current = Locator(
            scheme = "readium.locator.json",
            value = "current",
            extras = mapOf(TOC_EXTRA_HREF to "chapter2.xhtml#anchor")
        )

        assertEquals(1, resolveActiveTocIndex(items = items, currentLocator = current))
    }

    @Test
    fun `should fallback to progression when href is unavailable`() {
        val items = listOf(
            TocItem("第1章", "l1", 0, progression = 0.10),
            TocItem("第2章", "l2", 0, progression = 0.55),
            TocItem("第3章", "l3", 0, progression = 0.90)
        )
        val current = Locator(
            scheme = "readium.locator.json",
            value = "current",
            extras = mapOf(TOC_EXTRA_TOTAL_PROGRESSION to "0.60")
        )

        assertEquals(1, resolveActiveTocIndex(items = items, currentLocator = current))
    }

    @Test
    fun `should fallback to locator value exact match`() {
        val items = listOf(
            TocItem("第1章", "l1", 0, locatorValue = "a"),
            TocItem("第2章", "l2", 0, locatorValue = "b")
        )
        val current = Locator(
            scheme = "readium.locator.json",
            value = "b"
        )

        assertEquals(1, resolveActiveTocIndex(items = items, currentLocator = current))
    }

    @Test
    fun `should return null when no match can be resolved`() {
        val items = listOf(TocItem("第1章", "l1", 0))
        val current = Locator(
            scheme = "readium.locator.json",
            value = ""
        )

        assertNull(resolveActiveTocIndex(items = items, currentLocator = current))
    }
}
