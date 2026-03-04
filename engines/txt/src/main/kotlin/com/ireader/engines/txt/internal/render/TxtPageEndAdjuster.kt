package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.ReflowPageEndAdjuster
import com.ireader.engines.txt.internal.provider.ChapterDetector

internal class TxtPageEndAdjuster(
    private val detector: ChapterDetector = ChapterDetector()
) : ReflowPageEndAdjuster {

    private val strongEndPunctuation = setOf('。', '！', '？', '.', '!', '?', '…', ';', '；', ':', '：')

    @Suppress("UnusedParameter")
    override fun adjust(
        raw: String,
        measuredEnd: Int,
        rawLength: Int,
        pageStartOffset: Long
    ): Int {
        val safeMeasuredEnd = measuredEnd.coerceIn(0, rawLength)
        if (safeMeasuredEnd <= 0) {
            return safeMeasuredEnd
        }
        val chapterStart = findBoundaryLineStart(raw, safeMeasuredEnd)
            ?: return safeMeasuredEnd
        return chapterStart.coerceAtLeast(1)
    }

    private fun findBoundaryLineStart(raw: String, measuredEnd: Int): Int? {
        var lineStart = 0
        while (lineStart < measuredEnd) {
            val lineEnd = raw.indexOf('\n', lineStart)
                .takeIf { it >= 0 && it < measuredEnd }
                ?: measuredEnd
            val line = raw.substring(lineStart, lineEnd).trim()
            if (
                lineStart > 0 &&
                detector.isChapterBoundaryTitle(line) &&
                hasStrongPrelude(raw, lineStart)
            ) {
                return lineStart
            }
            if (lineEnd >= measuredEnd) {
                break
            }
            lineStart = lineEnd + 1
        }
        return null
    }

    private fun hasStrongPrelude(raw: String, lineStart: Int): Boolean {
        val beforeNewline = lineStart - 1
        if (beforeNewline !in raw.indices || raw[beforeNewline] != '\n') {
            return false
        }
        val previousBreak = raw.lastIndexOf('\n', startIndex = beforeNewline - 1)
        val previousStart = previousBreak + 1
        val previousLine = raw.substring(previousStart, beforeNewline).trim()
        if (previousLine.isEmpty()) {
            return true
        }
        val last = previousLine.lastOrNull() ?: return false
        return strongEndPunctuation.contains(last)
    }
}
