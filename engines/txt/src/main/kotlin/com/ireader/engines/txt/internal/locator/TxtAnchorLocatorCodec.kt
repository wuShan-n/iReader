package com.ireader.engines.txt.internal.locator

import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

internal object TxtAnchorLocatorCodec {

    fun locatorForAnchor(
        anchor: TextAnchor,
        extras: Map<String, String> = emptyMap()
    ): Locator {
        return Locator(
            scheme = LocatorSchemes.TXT_ANCHOR,
            value = buildString {
                append(anchor.utf16Offset)
                append(':')
                append(anchor.blockId)
                append(':')
                append(anchor.affinity.storageCode)
                append(':')
                append(anchor.revision)
            },
            extras = extras
        )
    }

    fun locatorForOffset(
        offset: Long,
        blockIndex: TxtBlockIndex,
        revision: Int,
        affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD,
        extras: Map<String, String> = emptyMap()
    ): Locator {
        return locatorForAnchor(
            anchor = blockIndex.anchorForOffset(
                offset = offset.coerceIn(0L, blockIndex.lengthCodeUnits),
                revision = revision,
                affinity = affinity
            ),
            extras = extras
        )
    }

    fun rangeForOffsets(
        startOffset: Long,
        endOffset: Long,
        blockIndex: TxtBlockIndex,
        revision: Int
    ): LocatorRange {
        return LocatorRange(
            start = locatorForOffset(
                offset = startOffset,
                blockIndex = blockIndex,
                revision = revision,
                affinity = TextAnchorAffinity.FORWARD
            ),
            end = locatorForOffset(
                offset = endOffset,
                blockIndex = blockIndex,
                revision = revision,
                affinity = TextAnchorAffinity.BACKWARD
            )
        )
    }

    fun parseAnchor(
        locator: Locator,
        blockIndex: TxtBlockIndex,
        expectedRevision: Int,
        maxOffset: Long = blockIndex.lengthCodeUnits
    ): TextAnchor? {
        if (locator.scheme != LocatorSchemes.TXT_ANCHOR) {
            return null
        }
        val parts = locator.value.split(':')
        if (parts.size != 4) {
            return null
        }
        val offset = parts[0].toLongOrNull() ?: return null
        val blockId = parts[1].toIntOrNull() ?: return null
        val affinity = TextAnchorAffinity.fromStorageCode(parts[2]) ?: return null
        val revision = parts[3].toIntOrNull() ?: return null
        if (offset < 0L || revision != expectedRevision) {
            return null
        }
        val safeOffset = offset.coerceIn(0L, maxOffset.coerceAtLeast(0L))
        val expectedBlockId = blockIndex.blockIdForOffset(safeOffset)
        if (blockId != expectedBlockId) {
            return null
        }
        return TextAnchor(
            utf16Offset = safeOffset,
            blockId = blockId,
            affinity = affinity,
            revision = revision
        )
    }

    fun parseOffset(
        locator: Locator,
        blockIndex: TxtBlockIndex,
        expectedRevision: Int,
        maxOffset: Long = blockIndex.lengthCodeUnits
    ): Long? {
        return parseAnchor(
            locator = locator,
            blockIndex = blockIndex,
            expectedRevision = expectedRevision,
            maxOffset = maxOffset
        )?.utf16Offset
    }
}
