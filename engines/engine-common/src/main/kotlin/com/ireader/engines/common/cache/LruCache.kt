package com.ireader.engines.common.cache

class LruCache<K, V>(
    maxEntries: Int
) {
    private val maxSize = maxEntries.coerceAtLeast(1)
    private val lock = Any()
    private val map = object : LinkedHashMap<K, V>(maxSize + 1, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<K, V>
        ): Boolean = size > maxSize
    }

    operator fun get(key: K): V? = synchronized(lock) {
        map[key]
    }

    operator fun set(key: K, value: V) {
        synchronized(lock) {
            map[key] = value
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }
}
