package com.ireader.engines.common.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LruCacheTest {

    @Test
    fun `least recently used entry should be evicted`() {
        val cache = LruCache<Int, String>(maxEntries = 2)
        cache[1] = "one"
        cache[2] = "two"
        cache[1]
        cache[3] = "three"

        assertEquals("one", cache[1])
        assertNull(cache[2])
        assertEquals("three", cache[3])
    }
}
