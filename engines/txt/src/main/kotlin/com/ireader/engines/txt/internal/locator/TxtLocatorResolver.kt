package com.ireader.engines.txt.internal.locator

import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

internal object TxtLocatorResolver {
    fun locatorForOffset(
        offset: Long,
        blockIndex: TxtBlockIndex,
        contentFingerprint: String,
        maxOffset: Long,
        projectionEngine: TextProjectionEngine,
        extras: Map<String, String> = emptyMap()
    ): Locator {
        return TxtStableLocatorCodec.locatorForOffset(
            offset = offset,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = maxOffset,
            projectionEngine = projectionEngine,
            extras = extras
        )
    }

    fun rangeForOffsets(
        startOffset: Long,
        endOffset: Long,
        blockIndex: TxtBlockIndex,
        contentFingerprint: String,
        maxOffset: Long,
        projectionEngine: TextProjectionEngine
    ): LocatorRange {
        return TxtStableLocatorCodec.rangeForOffsets(
            startOffset = startOffset,
            endOffset = endOffset,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = maxOffset,
            projectionEngine = projectionEngine
        )
    }

    fun parsePublicOffset(
        locator: Locator,
        blockIndex: TxtBlockIndex,
        contentFingerprint: String,
        maxOffset: Long,
        projectionEngine: TextProjectionEngine
    ): Long? {
        if (locator.scheme != LocatorSchemes.TXT_STABLE_ANCHOR) {
            return null
        }
        return TxtStableLocatorCodec.parseOffset(
            locator = locator,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = maxOffset,
            projectionEngine = projectionEngine
        )
    }

    fun parseOffset(
        locator: Locator,
        blockIndex: TxtBlockIndex,
        contentFingerprint: String,
        maxOffset: Long,
        projectionEngine: TextProjectionEngine,
        expectedProjectionVersion: String
    ): Long? {
        return when (locator.scheme) {
            LocatorSchemes.TXT_STABLE_ANCHOR -> TxtStableLocatorCodec.parseOffset(
                locator = locator,
                blockIndex = blockIndex,
                contentFingerprint = contentFingerprint,
                maxOffset = maxOffset,
                projectionEngine = projectionEngine
            )

            TxtRenderLocatorCodec.SCHEME -> TxtRenderLocatorCodec.parseOffset(
                locator = locator,
                blockIndex = blockIndex,
                expectedProjectionVersion = expectedProjectionVersion,
                maxOffset = maxOffset
            )

            else -> null
        }
    }
}
