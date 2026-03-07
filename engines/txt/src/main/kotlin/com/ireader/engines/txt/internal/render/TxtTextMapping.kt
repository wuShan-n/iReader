@file:Suppress("ReturnCount")

package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.reader.api.render.TextMapping
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange

internal class TxtTextMapping(
    private val pageStart: Long,
    private val pageEnd: Long,
    private val projectedBoundaryToRawOffsets: LongArray? = null
) : TextMapping {

    override fun locatorAt(charIndex: Int): Locator {
        val global = rawOffsetForBoundary(charIndex)
        return TxtBlockLocatorCodec.locatorForOffset(global, pageEnd)
    }

    override fun rangeFor(startChar: Int, endChar: Int): LocatorRange {
        val localStart = startChar.coerceAtLeast(0)
        val localEnd = endChar.coerceAtLeast(0)
        val minLocal = minOf(localStart, localEnd)
        val maxLocal = maxOf(localStart, localEnd)
        val startGlobal = rawOffsetForBoundary(minLocal)
        val endGlobal = rawOffsetForBoundary(maxLocal)
        return TxtBlockLocatorCodec.rangeForOffsets(
            startOffset = startGlobal,
            endOffset = endGlobal,
            maxOffset = pageEnd
        )
    }

    override fun charRangeFor(range: LocatorRange): IntRange? {
        val startGlobal = TxtBlockLocatorCodec.parseOffset(range.start) ?: return null
        val endGlobal = TxtBlockLocatorCodec.parseOffset(range.end) ?: return null
        val minGlobal = minOf(startGlobal, endGlobal)
        val maxGlobal = maxOf(startGlobal, endGlobal)
        if (minGlobal < pageStart || maxGlobal > pageEnd) {
            return null
        }
        if (projectedBoundaryToRawOffsets == null) {
            val localStart = (minGlobal - pageStart).toInt()
            val localEndExclusive = (maxGlobal - pageStart).toInt()
            return localStart until localEndExclusive
        }
        val localStart = lowerBound(projectedBoundaryToRawOffsets, minGlobal)
        val localEndExclusive = lowerBound(projectedBoundaryToRawOffsets, maxGlobal)
        return localStart until localEndExclusive
    }

    private fun rawOffsetForBoundary(charIndex: Int): Long {
        if (projectedBoundaryToRawOffsets == null) {
            val clamped = charIndex.toLong().coerceAtLeast(0L)
            return (pageStart + clamped).coerceAtMost(pageEnd)
        }
        val safe = charIndex.coerceIn(0, projectedBoundaryToRawOffsets.lastIndex)
        return projectedBoundaryToRawOffsets[safe].coerceIn(pageStart, pageEnd)
    }

    private fun lowerBound(boundaries: LongArray, target: Long): Int {
        var low = 0
        var high = boundaries.lastIndex
        var answer = boundaries.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (boundaries[mid] >= target) {
                answer = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return answer.coerceIn(0, boundaries.lastIndex)
    }
}
