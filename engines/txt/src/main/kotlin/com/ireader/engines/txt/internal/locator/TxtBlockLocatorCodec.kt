package com.ireader.engines.txt.internal.locator

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

/**
 * TXT locator format:
 * - preferred: scheme=txt.offset, value="<utf16Offset>"
 * - legacy: scheme=txt.block, value="<blockStartOffset>:<charOffsetInBlock>"
 */
internal object TxtBlockLocatorCodec {

    fun locatorForOffset(
        offset: Long,
        maxOffset: Long,
        extras: Map<String, String> = emptyMap()
    ): Locator {
        val safeMax = maxOffset.coerceAtLeast(0L)
        val safe = offset.coerceIn(0L, safeMax)
        return Locator(
            scheme = LocatorSchemes.TXT_OFFSET,
            value = safe.toString(),
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
        return when (locator.scheme) {
            LocatorSchemes.TXT_OFFSET -> parseOffsetValue(locator.value)
            LocatorSchemes.TXT_BLOCK -> parseLegacyBlockOffsetValue(locator.value)
            else -> null
        }
    }

    fun parseOffset(locator: Locator, maxOffset: Long): Long? {
        val parsed = parseOffset(locator) ?: return null
        return parsed.coerceIn(0L, maxOffset.coerceAtLeast(0L))
    }

    fun parseOffsetValue(value: String): Long? {
        val offset = value.toLongOrNull() ?: return null
        return offset.takeIf { it >= 0L }
    }

    fun parseLegacyBlockOffsetValue(value: String): Long? {
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

    private const val BLOCK_CHARS = 2048L
}
