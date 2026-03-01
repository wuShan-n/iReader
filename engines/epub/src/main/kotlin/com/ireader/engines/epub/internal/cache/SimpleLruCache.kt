package com.ireader.engines.epub.internal.cache

internal class SimpleLruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun getOrPut(key: K, producer: () -> V): V {
        val existing = map[key]
        if (existing != null) {
            return existing
        }
        val created = producer()
        map[key] = created
        return created
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
