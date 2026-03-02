package com.ireader.engines.txt.internal.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageStartsIndexTest {

    @Test
    fun getAtOrNull_returns_expected_values() {
        val index = PageStartsIndex()
        index.seedIfEmpty(0)
        index.addStart(120)
        index.addStart(260)

        assertEquals(0, index.getAtOrNull(0))
        assertEquals(120, index.getAtOrNull(1))
        assertEquals(260, index.getAtOrNull(2))
        assertNull(index.getAtOrNull(-1))
        assertNull(index.getAtOrNull(3))
    }
}

