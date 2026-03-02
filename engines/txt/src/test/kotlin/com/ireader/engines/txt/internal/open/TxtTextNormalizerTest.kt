package com.ireader.engines.txt.internal.open

import org.junit.Assert.assertEquals
import org.junit.Test

class TxtTextNormalizerTest {

    @Test
    fun normalize_rewrites_line_endings_tabs_and_controls() {
        val input = "\uFEFFline1\r\nline2\rline3\tend\u0001"

        val normalized = TxtTextNormalizer.normalize(input)

        assertEquals("line1\nline2\nline3    end", normalized)
    }
}
