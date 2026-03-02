package com.ireader.engines.txt.internal.paging

internal class PaginationCache(
    private val maxPages: Int = 24
) {
    data class Stats(
        val hits: Long,
        val misses: Long
    )

    private var namespace: String = ""
    private var hitCount: Long = 0L
    private var missCount: Long = 0L

    private val lru = object : LinkedHashMap<Int, PageSlice>(maxPages, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PageSlice>?): Boolean {
            return size > maxPages
        }
    }

    fun get(startChar: Int, namespace: String): PageSlice? = synchronized(lru) {
        if (this.namespace != namespace) {
            missCount += 1
            return@synchronized null
        }
        val hit = lru[startChar]
        if (hit == null) {
            missCount += 1
        } else {
            hitCount += 1
        }
        hit
    }

    fun put(slice: PageSlice, namespace: String) {
        synchronized(lru) {
            if (this.namespace != namespace) {
                lru.clear()
                this.namespace = namespace
            }
            lru[slice.startChar] = slice
        }
    }

    fun clear() {
        synchronized(lru) {
            lru.clear()
            namespace = ""
        }
    }

    fun stats(): Stats = synchronized(lru) {
        Stats(
            hits = hitCount,
            misses = missCount
        )
    }

    fun resetStats() {
        synchronized(lru) {
            hitCount = 0L
            missCount = 0L
        }
    }
}
