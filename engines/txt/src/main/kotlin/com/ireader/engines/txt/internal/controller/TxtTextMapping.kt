package com.ireader.engines.txt.internal.controller

import com.ireader.reader.api.render.TextMapping
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

internal class TxtTextMapping(
    private val pageStartChar: Int
) : TextMapping {

    override fun locatorAt(charIndex: Int): Locator {
        val globalOffset = (pageStartChar + charIndex).coerceAtLeast(0)
        return Locator(LocatorSchemes.TXT_OFFSET, globalOffset.toString())
    }

    override fun rangeFor(startChar: Int, endChar: Int): LocatorRange {
        return LocatorRange(
            start = locatorAt(startChar),
            end = locatorAt(endChar)
        )
    }

    override fun charRangeFor(range: LocatorRange): IntRange? {
        if (range.start.scheme != LocatorSchemes.TXT_OFFSET || range.end.scheme != LocatorSchemes.TXT_OFFSET) {
            return null
        }
        val start = range.start.value.toIntOrNull() ?: return null
        val end = range.end.value.toIntOrNull() ?: return null
        val localStart = (start - pageStartChar).coerceAtLeast(0)
        val localEnd = (end - pageStartChar).coerceAtLeast(localStart)
        return localStart until localEnd
    }
}
