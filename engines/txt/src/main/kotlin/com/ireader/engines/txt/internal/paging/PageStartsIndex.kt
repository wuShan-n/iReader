package com.ireader.engines.txt.internal.paging

internal class PageStartsIndex {
    private val lock = Any()
    private val starts = IntArrayList()

    val size: Int
        get() = synchronized(lock) { starts.size }

    fun clear() = synchronized(lock) {
        starts.clear()
    }

    fun isEmpty(): Boolean = synchronized(lock) { starts.size == 0 }

    fun seedIfEmpty(vararg values: Int) = synchronized(lock) {
        if (starts.size > 0) return@synchronized
        values.forEach { value -> addStartLocked(value.coerceAtLeast(0)) }
    }

    fun addStart(v: Int): Boolean = synchronized(lock) {
        addStartLocked(v.coerceAtLeast(0))
    }

    fun floor(value: Int): Int? = synchronized(lock) {
        if (starts.size == 0) return@synchronized null
        val idx = starts.binarySearch(value)
        if (idx >= 0) return@synchronized starts.get(idx)
        val insertion = -idx - 1
        val floorIdx = insertion - 1
        if (floorIdx < 0) null else starts.get(floorIdx)
    }

    fun floorIndexOf(value: Int): Int = synchronized(lock) {
        starts.indexOfFloor(value)
    }

    fun ceiling(value: Int): Int? = synchronized(lock) {
        if (starts.size == 0) return@synchronized null
        val idx = starts.binarySearch(value)
        if (idx >= 0) return@synchronized starts.get(idx)
        val insertion = -idx - 1
        if (insertion >= starts.size) null else starts.get(insertion)
    }

    fun snapshot(): IntArray = synchronized(lock) {
        starts.toIntArrayCopy()
    }

    fun mergeFrom(sortedUnique: IntArray) = synchronized(lock) {
        sortedUnique.forEach { v -> addStartLocked(v.coerceAtLeast(0)) }
    }

    private fun addStartLocked(value: Int): Boolean {
        return starts.addSortedUnique(value)
    }
}
