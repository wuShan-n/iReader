package com.ireader.engines.pdf.internal.cache

import java.util.LinkedHashMap

internal class IntLruCache<V>(
    private val maxEntries: Int
) {
    private val map = object : LinkedHashMap<Int, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, V>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: Int): V? = map[key]

    @Synchronized
    fun put(key: Int, value: V) {
        map[key] = value
    }

    @Synchronized
    fun evictAll() {
        map.clear()
    }
}
