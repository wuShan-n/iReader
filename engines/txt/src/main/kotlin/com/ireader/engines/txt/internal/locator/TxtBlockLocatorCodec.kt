package com.ireader.engines.txt.internal.locator

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

/**
 * TXT locator format:
 * value = "<blockStartOffset>:<charOffsetInBlock>"
 */
internal object TxtBlockLocatorCodec {

    fun locatorForOffset(
        offset: Long,
        maxOffset: Long,
        extras: Map<String, String> = emptyMap()
    ): Locator {
        val safeMax = maxOffset.coerceAtLeast(0L)
        val safe = offset.coerceIn(0L, safeMax)
        val blockStart = safe.floorToBlock()
        val inBlock = (safe - blockStart).coerceAtLeast(0L)
        return Locator(
            scheme = LocatorSchemes.TXT_BLOCK,
            value = "$blockStart:$inBlock",
            extras = extras
        )
    }

    fun rangeForOffsets(
        startOffset: Long,
        endOffset: Long,
        maxOffset: Long
    ): LocatorRange {
        return LocatorRange(
            start = locatorForOffset(startOffset, maxOffset),
            end = locatorForOffset(endOffset, maxOffset)
        )
    }

    fun parseOffset(locator: Locator): Long? {
        if (locator.scheme != LocatorSchemes.TXT_BLOCK) {
            return null
        }
        return parseOffsetValue(locator.value)
    }

    fun parseOffset(locator: Locator, maxOffset: Long): Long? {
        val parsed = parseOffset(locator) ?: return null
        return parsed.coerceIn(0L, maxOffset.coerceAtLeast(0L))
    }

    fun parseOffsetValue(value: String): Long? {
        val separator = value.indexOf(':')
        if (separator <= 0 || separator >= value.lastIndex) {
            return null
        }
        val blockStart = value.substring(0, separator).toLongOrNull() ?: return null
        val inBlock = value.substring(separator + 1).toLongOrNull() ?: return null
        if (blockStart < 0L || inBlock < 0L || inBlock >= BLOCK_CHARS) {
            return null
        }
        val sum = blockStart + inBlock
        if (sum < blockStart) {
            return null
        }
        return sum
    }

    private fun Long.floorToBlock(): Long = (this / BLOCK_CHARS) * BLOCK_CHARS

    private const val BLOCK_CHARS = 2048L
}
