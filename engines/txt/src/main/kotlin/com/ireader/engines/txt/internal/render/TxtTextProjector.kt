package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.ReflowTextWindow
import com.ireader.engines.txt.internal.softbreak.BreakMapState
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex

internal object TxtTextProjector {

    fun projectWindow(
        rawText: String,
        startOffset: Long,
        breakIndex: SoftBreakIndex?
    ): ReflowTextWindow {
        if (rawText.isEmpty() || breakIndex == null || rawText.indexOf('\n') < 0) {
            return ReflowTextWindow.identity(rawText)
        }

        val states = HashMap<Long, BreakMapState>(rawText.count { it == '\n' }.coerceAtLeast(4))
        breakIndex.forEachStateInRange(startOffset, startOffset + rawText.length.toLong()) { offset, state ->
            states[offset] = state
        }
        if (states.isEmpty()) {
            return ReflowTextWindow.identity(rawText)
        }

        val display = StringBuilder(rawText.length)
        val projectedBoundaryToRaw = IntArray(rawText.length + 1)
        val rawBoundaryToProjected = IntArray(rawText.length + 1)
        var projectedLength = 0
        var boundaryRawIndex = 0
        projectedBoundaryToRaw[0] = 0
        rawBoundaryToProjected[0] = 0

        rawText.forEachIndexed { rawIndex, char ->
            val state = if (char == '\n') {
                states[startOffset + rawIndex.toLong()]
            } else {
                null
            }
            when (state) {
                BreakMapState.SOFT_JOIN -> {
                    boundaryRawIndex = rawIndex + 1
                    projectedBoundaryToRaw[projectedLength] = boundaryRawIndex
                }

                BreakMapState.SOFT_SPACE -> {
                    display.append(' ')
                    projectedLength++
                    boundaryRawIndex = rawIndex + 1
                    projectedBoundaryToRaw[projectedLength] = boundaryRawIndex
                }

                BreakMapState.PRESERVE,
                BreakMapState.HARD_PARAGRAPH,
                BreakMapState.UNKNOWN,
                null -> {
                    display.append(char)
                    projectedLength++
                    boundaryRawIndex = rawIndex + 1
                    projectedBoundaryToRaw[projectedLength] = boundaryRawIndex
                }
            }
            rawBoundaryToProjected[rawIndex + 1] = projectedLength
        }

        return ReflowTextWindow(
            rawText = rawText,
            displayText = display.toString(),
            projectedBoundaryToRawIndex = projectedBoundaryToRaw.copyOf(projectedLength + 1),
            rawBoundaryToProjectedIndex = rawBoundaryToProjected
        )
    }
}
