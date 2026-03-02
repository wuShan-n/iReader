package com.ireader.engines.pdf.internal.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBackendCapabilitiesTest {

    @Test
    fun `full capabilities check requires all flags`() {
        val full = PdfBackendCapabilities(
            outline = true,
            links = true,
            textExtraction = true,
            search = true
        )
        val partial = full.copy(search = false)

        assertTrue(full.supportsFullReaderFeatures())
        assertFalse(partial.supportsFullReaderFeatures())
    }
}

