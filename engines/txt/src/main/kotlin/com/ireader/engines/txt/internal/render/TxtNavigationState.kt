package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.common.android.reflow.ReflowPageSlice
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression
import java.util.Locale
import kotlin.math.roundToInt

internal class TxtNavigationState(
    initialStart: Long
) {
    var currentStart: Long = initialStart
        private set
    var currentEnd: Long = initialStart
        private set
    private var avgCharsPerPage: Int = 1800

    fun moveTo(start: Long, maxOffset: Long) {
        currentStart = start.coerceIn(0L, maxOffset)
    }

    fun updateFromSlice(slice: ReflowPageSlice) {
        currentStart = slice.startOffset
        currentEnd = slice.endOffset
        val consumed = (slice.endOffset - slice.startOffset).toInt().coerceAtLeast(1)
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

    fun locatorFor(maxOffset: Long): Locator {
        val percent = if (maxOffset == 0L) {
            0.0
        } else {
            currentStart.toDouble() / maxOffset.toDouble()
        }.coerceIn(0.0, 1.0)
        return TxtBlockLocatorCodec.locatorForOffset(
            offset = currentStart.coerceIn(0L, maxOffset),
            maxOffset = maxOffset,
            extras = mapOf("progression" to String.format(Locale.US, "%.6f", percent))
        )
    }

    suspend fun findPreviousStart(
        fromStart: Long,
        maxOffset: Long,
        constraints: LayoutConstraints,
        resolveSlice: suspend (Long, LayoutConstraints) -> ReflowPageSlice
    ): Long {
        if (fromStart <= 0L) {
            return 0L
        }
        val estimateDistance = (avgCharsPerPage * 2L).coerceAtLeast(1_200L)
        var cursor = (fromStart - estimateDistance).coerceAtLeast(0L)
        var previousStart = 0L
        var safety = 0

        while (cursor < fromStart && safety < 256) {
            val slice = resolveSlice(cursor, constraints)
            if (slice.endOffset >= fromStart) {
                return previousStart.coerceAtMost(fromStart)
            }
            previousStart = slice.startOffset
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            safety++
        }
        return cursor.coerceAtMost(fromStart).coerceAtLeast(0L).coerceAtMost(maxOffset)
    }
}
