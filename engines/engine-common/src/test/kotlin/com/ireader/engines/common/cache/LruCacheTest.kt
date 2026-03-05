package com.ireader.engines.common.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Test
    fun `concurrent access should not crash`() {
        val cache = LruCache<Int, Int>(maxEntries = 64)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val pool = Executors.newFixedThreadPool(4)
        repeat(4) { worker ->
            pool.submit {
                runCatching {
                    repeat(10_000) { index ->
                        val key = (worker * 31 + index) % 128
                        cache[key] = index
                        cache[key]
                    }
                }.onFailure { failures.add(it) }
            }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertTrue(failures.isEmpty())
    }
}
