@file:Suppress("ReturnCount")

package com.ireader.engines.txt.internal.render

import com.ireader.reader.api.render.TextMapping
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

internal class TxtTextMapping(
    private val pageStart: Long,
    private val pageEnd: Long
) : TextMapping {

    override fun locatorAt(charIndex: Int): Locator {
        val clamped = charIndex.toLong().coerceAtLeast(0L)
        val global = (pageStart + clamped).coerceAtMost(pageEnd)
        return Locator(
            scheme = LocatorSchemes.TXT_OFFSET,
            value = global.toString()
        )
    }

    override fun rangeFor(startChar: Int, endChar: Int): LocatorRange {
        val localStart = startChar.coerceAtLeast(0)
        val localEnd = endChar.coerceAtLeast(0)
        val minLocal = minOf(localStart, localEnd)
        val maxLocal = maxOf(localStart, localEnd)
        val startGlobal = (pageStart + minLocal.toLong()).coerceAtMost(pageEnd)
        val endGlobal = (pageStart + maxLocal.toLong()).coerceAtMost(pageEnd)
        return LocatorRange(
            start = Locator(LocatorSchemes.TXT_OFFSET, startGlobal.toString()),
            end = Locator(LocatorSchemes.TXT_OFFSET, endGlobal.toString())
        )
    }

    override fun charRangeFor(range: LocatorRange): IntRange? {
        if (range.start.scheme != LocatorSchemes.TXT_OFFSET || range.end.scheme != LocatorSchemes.TXT_OFFSET) {
            return null
        }
        val startGlobal = range.start.value.toLongOrNull() ?: return null
        val endGlobal = range.end.value.toLongOrNull() ?: return null
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
