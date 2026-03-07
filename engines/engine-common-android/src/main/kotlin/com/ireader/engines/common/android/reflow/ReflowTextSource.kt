package com.ireader.engines.common.android.reflow

interface ReflowTextSource {
    val lengthChars: Long
    fun readString(start: Long, count: Int): String

    fun readWindow(start: Long, count: Int): ReflowTextWindow {
        val raw = readString(start, count)
        return ReflowTextWindow.identity(raw)
    }
}

data class ReflowTextWindow(
    val rawText: String,
    val displayText: String,
    val projectedBoundaryToRawIndex: IntArray,
    val rawBoundaryToProjectedIndex: IntArray
) {
    companion object {
        fun identity(rawText: String): ReflowTextWindow {
            val projected = IntArray(rawText.length + 1) { it }
            return ReflowTextWindow(
                rawText = rawText,
                displayText = rawText,
                projectedBoundaryToRawIndex = projected,
                rawBoundaryToProjectedIndex = projected.copyOf()
            )
        }
    }
}
