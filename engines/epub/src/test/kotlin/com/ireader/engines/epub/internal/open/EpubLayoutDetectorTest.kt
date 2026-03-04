package com.ireader.engines.epub.internal.open

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubLayoutDetectorTest {

    @Test
    fun `metadata presentation fixed should be detected as fixed-layout`() {
        val fixed = detectEpubFixedLayout(
            metadataOther = mapOf("presentation" to mapOf("layout" to "fixed")),
            readingOrderLayoutHints = emptyList()
        )

        assertTrue(fixed)
    }

    @Test
    fun `metadata presentation reflowable should be detected as reflow`() {
        val fixed = detectEpubFixedLayout(
            metadataOther = mapOf("presentation" to mapOf("layout" to "reflowable")),
            readingOrderLayoutHints = listOf("fixed", "fixed")
        )

        assertFalse(fixed)
    }

    @Test
    fun `legacy rendition layout pre-paginated should be detected as fixed-layout`() {
        val fixed = detectEpubFixedLayout(
            metadataOther = mapOf("rendition:layout" to "pre-paginated"),
            readingOrderLayoutHints = emptyList()
        )

        assertTrue(fixed)
    }

    @Test
    fun `all reading-order links fixed should be treated as fixed-layout when metadata missing`() {
        val fixed = detectEpubFixedLayout(
            metadataOther = emptyMap(),
            readingOrderLayoutHints = listOf("fixed", "fixed")
        )

        assertTrue(fixed)
    }

    @Test
    fun `mixed or missing link layout should not be treated as fixed-layout`() {
        val mixed = detectEpubFixedLayout(
            metadataOther = emptyMap(),
            readingOrderLayoutHints = listOf("fixed", "reflowable")
        )
        val missing = detectEpubFixedLayout(
            metadataOther = emptyMap(),
            readingOrderLayoutHints = listOf("fixed", null)
        )

        assertFalse(mixed)
        assertFalse(missing)
    }
}
