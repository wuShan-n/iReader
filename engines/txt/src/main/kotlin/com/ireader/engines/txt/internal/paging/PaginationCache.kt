package com.ireader.engines.txt.internal.paging

internal class PaginationCache(
    private val maxPages: Int = 24
) {
    private val lru = object : LinkedHashMap<Int, PageSlice>(maxPages, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PageSlice>?): Boolean {
            return size > maxPages
        }
    }

    fun get(startChar: Int): PageSlice? = synchronized(lru) { lru[startChar] }

    fun put(slice: PageSlice) {
        synchronized(lru) {
            lru[slice.startChar] = slice
        }
    }

    fun clear() {
        synchronized(lru) {
            lru.clear()
        }
    }
}
