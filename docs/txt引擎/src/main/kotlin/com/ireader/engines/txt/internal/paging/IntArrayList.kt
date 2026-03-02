package com.ireader.engines.txt.internal.paging

internal class IntArrayList(initialCapacity: Int = 16) {
    private var arr: IntArray = IntArray(initialCapacity.coerceAtLeast(1))
    private var _size: Int = 0

    val size: Int
        get() = _size

    fun clear() {
        _size = 0
    }

    fun get(index: Int): Int {
        check(index in 0 until _size) { "Index out of bounds: $index, size=$_size" }
        return arr[index]
    }

    fun add(value: Int) {
        ensureCapacity(_size + 1)
        arr[_size] = value
        _size += 1
    }

    fun binarySearch(value: Int): Int {
        var lo = 0
        var hi = _size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val m = arr[mid]
            if (m < value) {
                lo = mid + 1
            } else if (m > value) {
                hi = mid - 1
            } else {
                return mid
            }
        }
        return -(lo + 1)
    }

    fun indexOfFloor(value: Int): Int {
        if (_size == 0) return -1
        val idx = binarySearch(value)
        if (idx >= 0) return idx
        val insertion = -idx - 1
        return insertion - 1
    }

    fun addSortedUnique(value: Int): Boolean {
        if (_size == 0) {
            add(value)
            return true
        }
        val idx = binarySearch(value)
        if (idx >= 0) return false

        val insertAt = -idx - 1
        ensureCapacity(_size + 1)
        if (insertAt < _size) {
            System.arraycopy(arr, insertAt, arr, insertAt + 1, _size - insertAt)
        }
        arr[insertAt] = value
        _size += 1
        return true
    }

    fun toIntArrayCopy(): IntArray = arr.copyOf(_size)

    private fun ensureCapacity(capacity: Int) {
        if (capacity <= arr.size) return
        var next = arr.size * 2
        while (next < capacity) next *= 2
        arr = arr.copyOf(next)
    }
}
