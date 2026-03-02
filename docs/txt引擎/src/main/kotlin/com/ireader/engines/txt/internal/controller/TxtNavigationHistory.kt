package com.ireader.engines.txt.internal.controller

internal class TxtNavigationHistory(
    private val maxSize: Int = 128
) {
    private val entries = ArrayDeque<Int>()

    fun isEmpty(): Boolean = entries.isEmpty()

    fun push(startChar: Int) {
        val safeStart = startChar.coerceAtLeast(0)
        if (entries.isNotEmpty() && entries.last() == safeStart) return
        if (entries.size >= maxSize.coerceAtLeast(1)) {
            entries.removeFirst()
        }
        entries.addLast(safeStart)
    }

    fun popOrNull(): Int? {
        if (entries.isEmpty()) return null
        return entries.removeLast()
    }

    fun clear() {
        entries.clear()
    }
}
