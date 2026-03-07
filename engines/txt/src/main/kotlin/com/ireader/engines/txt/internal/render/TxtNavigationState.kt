package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.locator.TxtAnchorLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.Progression
import java.util.Locale
import kotlin.math.roundToInt

internal class TxtNavigationState(
    initialStart: Long,
    private val blockIndex: TxtBlockIndex,
    private val revision: Int
) {
    var currentStart: Long = initialStart
        private set
    var currentEnd: Long = initialStart
        private set
    private var avgCharsPerPage: Int = 1800

    fun moveTo(start: Long, maxOffset: Long) {
        currentStart = start.coerceIn(0L, maxOffset)
    }

    fun updateFromSlice(startOffset: Long, endOffset: Long) {
        currentStart = startOffset
        currentEnd = endOffset
        val consumed = (endOffset - startOffset).toInt().coerceAtLeast(1)
        avgCharsPerPage = ((avgCharsPerPage * 3) + consumed) / 4
    }

    fun canGoPrev(): Boolean = currentStart > 0L

    fun canGoNext(maxOffset: Long): Boolean = currentEnd < maxOffset

    fun progressionFor(maxOffset: Long): Progression {
        val percent = if (maxOffset == 0L) {
            0.0
        } else {
            currentStart.toDouble() / maxOffset.toDouble()
        }.coerceIn(0.0, 1.0)
        return Progression(
            percent = percent,
            label = "${(percent * 100.0).roundToInt()}%"
        )
    }

    fun locatorFor(maxOffset: Long, extras: Map<String, String> = emptyMap()): Locator {
        val percent = if (maxOffset == 0L) {
            0.0
        } else {
            currentStart.toDouble() / maxOffset.toDouble()
        }.coerceIn(0.0, 1.0)
        val mergedExtras = extras + mapOf(
            LocatorExtraKeys.PROGRESSION to String.format(Locale.US, "%.6f", percent)
        )
        return TxtAnchorLocatorCodec.locatorForOffset(
            offset = currentStart.coerceIn(0L, maxOffset),
            blockIndex = blockIndex,
            revision = revision,
            extras = mergedExtras
        )
    }
}
