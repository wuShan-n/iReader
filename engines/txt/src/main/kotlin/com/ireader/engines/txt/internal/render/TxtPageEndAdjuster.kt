package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.ReflowPageEndAdjuster
import com.ireader.engines.txt.internal.provider.ChapterDetector

internal class TxtPageEndAdjuster(
    private val detector: ChapterDetector = ChapterDetector()
) : ReflowPageEndAdjuster {

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
            if (lineStart > 0 && detector.isChapterBoundaryTitle(line)) {
                return lineStart
            }
            if (lineEnd >= measuredEnd) {
                break
            }
            lineStart = lineEnd + 1
        }
        return null
    }
}
