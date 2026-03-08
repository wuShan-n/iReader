package com.ireader.engines.txt.internal.locator

import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.reader.model.Locator

internal object TxtRenderLocatorCodec {
    const val SCHEME: String = "txt.render.anchor"

    fun locatorForOffset(
        offset: Long,
        blockIndex: TxtBlockIndex,
        projectionVersion: String,
        affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD,
        extras: Map<String, String> = emptyMap()
    ): Locator {
        val safeOffset = offset.coerceIn(0L, blockIndex.lengthCodeUnits)
        return Locator(
            scheme = SCHEME,
            value = buildString {
                append(safeOffset)
                append(':')
                append(blockIndex.blockIdForOffset(safeOffset))
                append(':')
                append(affinity.storageCode)
                append(':')
                append(projectionVersion)
            },
            extras = extras
        )
    }

    fun parseOffset(
        locator: Locator,
        blockIndex: TxtBlockIndex,
        expectedProjectionVersion: String,
        maxOffset: Long = blockIndex.lengthCodeUnits
    ): Long? {
        if (locator.scheme != SCHEME) {
            return null
        }
        val parts = locator.value.split(':')
        if (parts.size != 4) {
            return null
        }
        val offset = parts[0].toLongOrNull() ?: return null
        val blockId = parts[1].toIntOrNull() ?: return null
        val affinity = TextAnchorAffinity.fromStorageCode(parts[2]) ?: return null
        val projectionVersion = parts[3]
        if (projectionVersion != expectedProjectionVersion || offset < 0L) {
            return null
        }
        val safeOffset = offset.coerceIn(0L, maxOffset.coerceAtLeast(0L))
        val expectedBlockId = blockIndex.blockIdForOffset(safeOffset)
        if (blockId != expectedBlockId) {
            return null
        }
        affinity.hashCode()
        return safeOffset
    }
}
