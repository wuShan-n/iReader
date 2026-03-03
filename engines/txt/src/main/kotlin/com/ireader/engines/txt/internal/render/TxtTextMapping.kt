@file:Suppress("ReturnCount")

package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.reader.api.render.TextMapping
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange

internal class TxtTextMapping(
    private val pageStart: Long,
    private val pageEnd: Long
) : TextMapping {

    override fun locatorAt(charIndex: Int): Locator {
        val clamped = charIndex.toLong().coerceAtLeast(0L)
        val global = (pageStart + clamped).coerceAtMost(pageEnd)
        return TxtBlockLocatorCodec.locatorForOffset(global, pageEnd)
    }

    override fun rangeFor(startChar: Int, endChar: Int): LocatorRange {
        val localStart = startChar.coerceAtLeast(0)
        val localEnd = endChar.coerceAtLeast(0)
        val minLocal = minOf(localStart, localEnd)
        val maxLocal = maxOf(localStart, localEnd)
        val startGlobal = (pageStart + minLocal.toLong()).coerceAtMost(pageEnd)
        val endGlobal = (pageStart + maxLocal.toLong()).coerceAtMost(pageEnd)
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
        val localStart = (minGlobal - pageStart).toInt()
        val localEndExclusive = (maxGlobal - pageStart).toInt()
        return localStart until localEndExclusive
    }
}
