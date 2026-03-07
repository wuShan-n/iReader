package com.ireader.engines.txt.internal.locator

import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

/**
 * TXT locator format:
 * - scheme=txt.offset, value="<utf16CodeUnitOffset>"
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

    fun anchorForOffset(
        offset: Long,
        maxOffset: Long,
        blockIndex: TxtBlockIndex,
        revision: Int,
        affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD
    ): TextAnchor {
        val safe = offset.coerceIn(0L, maxOffset.coerceAtLeast(0L))
        return blockIndex.anchorForOffset(
            offset = safe,
            revision = revision,
            affinity = affinity
        )
    }

    fun parseAnchor(
        locator: Locator,
        maxOffset: Long,
        blockIndex: TxtBlockIndex,
        revision: Int,
        affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD
    ): TextAnchor? {
        val offset = parseOffset(locator, maxOffset) ?: return null
        return blockIndex.anchorForOffset(
            offset = offset,
            revision = revision,
            affinity = affinity
        )
    }
}
