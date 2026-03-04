package com.ireader.engines.txt.internal.render

import android.util.Log
import com.ireader.engines.common.android.reflow.ReflowPageEndAdjuster
import com.ireader.engines.txt.internal.provider.ChapterDetector

internal class TxtPageEndAdjuster(
    private val detector: ChapterDetector = ChapterDetector()
) : ReflowPageEndAdjuster {

    private val strongEndPunctuation = setOf('。', '！', '？', '.', '!', '?', '…', ';', '；', ':', '：')

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
        val decision = findBoundaryLineStart(raw, safeMeasuredEnd)
            ?: return safeMeasuredEnd
        val adjusted = decision.chapterStart.coerceAtLeast(1)
        if (isDebugLoggingEnabled()) {
            val rewindChars = safeMeasuredEnd - adjusted
            logDebug(
                TAG,
                "chapter-rewind start=$adjusted measuredEnd=$safeMeasuredEnd rewindChars=$rewindChars " +
                    "pageStartOffset=$pageStartOffset title='${decision.titlePreview}' " +
                    "prelude='${decision.preludePreview}'"
            )
        }
        return adjusted
    }

    private fun findBoundaryLineStart(raw: String, measuredEnd: Int): ChapterBoundaryDecision? {
        var lineStart = 0
        var candidate: ChapterBoundaryDecision? = null
        while (lineStart < measuredEnd) {
            val lineEnd = raw.indexOf('\n', lineStart)
                .takeIf { it >= 0 && it < measuredEnd }
                ?: measuredEnd
            val line = raw.substring(lineStart, lineEnd).trim()
            val prelude = previousLine(raw, lineStart).trim()
            if (
                lineStart > 0 &&
                detector.isChapterBoundaryTitle(line) &&
                hasStrongPrelude(raw, lineStart, prelude)
            ) {
                val rewindChars = measuredEnd - lineStart
                if (rewindChars in 1..MAX_CHAPTER_REWIND_CHARS) {
                    candidate = ChapterBoundaryDecision(
                        chapterStart = lineStart,
                        titlePreview = line.take(MAX_DEBUG_PREVIEW),
                        preludePreview = prelude.take(MAX_DEBUG_PREVIEW)
                    )
                }
            }
            if (lineEnd >= measuredEnd) {
                break
            }
            lineStart = lineEnd + 1
        }
        return candidate
    }

    private fun hasStrongPrelude(raw: String, lineStart: Int, previousLine: String): Boolean {
        val beforeNewline = lineStart - 1
        if (beforeNewline !in raw.indices || raw[beforeNewline] != '\n') {
            return false
        }
        if (previousLine.isEmpty()) {
            return true
        }
        val last = previousLine.lastOrNull() ?: return false
        return strongEndPunctuation.contains(last)
    }

    private fun previousLine(raw: String, lineStart: Int): String {
        val beforeNewline = lineStart - 1
        if (beforeNewline !in raw.indices || raw[beforeNewline] != '\n') {
            return ""
        }
        val previousBreak = raw.lastIndexOf('\n', startIndex = beforeNewline - 1)
        val previousStart = previousBreak + 1
        return raw.substring(previousStart, beforeNewline)
    }

    private data class ChapterBoundaryDecision(
        val chapterStart: Int,
        val titlePreview: String,
        val preludePreview: String
    )

    private companion object {
        private const val TAG = "TxtPageEndAdjuster"
        private const val MAX_DEBUG_PREVIEW = 48
        private const val MAX_CHAPTER_REWIND_CHARS = 240

        private fun isDebugLoggingEnabled(): Boolean {
            return runCatching { Log.isLoggable(TAG, Log.DEBUG) }
                .getOrDefault(false)
        }

        private fun logDebug(tag: String, message: String) {
            runCatching { Log.d(tag, message) }
        }
    }
}
