package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.locator.TxtLocatorResolver
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.Progression

internal class TxtNavigationService(
    initialStart: Long,
    private val blockIndex: TxtBlockIndex,
    private val contentFingerprint: String,
    private val projectionEngine: TextProjectionEngine,
    private val maxOffset: Long,
    private val projectionVersionProvider: () -> String
) {
    private val state = TxtNavigationState(
        initialStart = initialStart,
        blockIndex = blockIndex,
        contentFingerprint = contentFingerprint,
        projectionEngine = projectionEngine
    )

    val currentStart: Long
        get() = state.currentStart

    fun moveTo(start: Long) {
        state.moveTo(start = start, maxOffset = maxOffset)
    }

    fun moveToLocator(locator: Locator): Long? {
        return TxtLocatorResolver.parseOffset(
            locator = locator,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = maxOffset,
            projectionEngine = projectionEngine,
            expectedProjectionVersion = projectionVersionProvider()
        )
    }

    fun updateFromSlice(slice: TxtPageSlice) {
        state.updateFromSlice(
            startOffset = slice.startOffset,
            endOffset = slice.endOffset
        )
    }

    fun canGoPrev(): Boolean = state.canGoPrev()

    fun canGoNext(): Boolean = state.canGoNext(maxOffset)

    fun progression(): Progression = state.progressionFor(maxOffset)

    fun locator(extras: Map<String, String> = emptyMap()): Locator {
        return state.locatorFor(maxOffset = maxOffset, extras = extras)
    }

    fun rangeForSlice(slice: TxtPageSlice): LocatorRange {
        return TxtLocatorResolver.rangeForOffsets(
            startOffset = slice.startOffset,
            endOffset = slice.endOffset,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = maxOffset,
            projectionEngine = projectionEngine
        )
    }
}
