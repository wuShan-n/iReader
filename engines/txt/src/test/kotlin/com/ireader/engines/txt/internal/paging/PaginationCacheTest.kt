package com.ireader.engines.txt.internal.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaginationCacheTest {

    @Test
    fun cache_isolated_by_namespace() {
        val cache = PaginationCache(maxPages = 8)
        val slice = PageSlice(startChar = 100, endChar = 200, text = "demo")

        cache.put(slice, namespace = "sig-a")

        assertEquals(slice, cache.get(100, namespace = "sig-a"))
        assertNull(cache.get(100, namespace = "sig-b"))
        val stats = cache.stats()
        assertEquals(1L, stats.hits)
        assertEquals(1L, stats.misses)
    }
}
