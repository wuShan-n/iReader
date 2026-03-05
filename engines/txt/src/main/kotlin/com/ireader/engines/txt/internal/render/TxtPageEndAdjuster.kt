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

        val debug = isDebugLoggingEnabled()
        val decision = findBoundaryLineStartNearEnd(
            raw = raw,
            measuredEnd = safeMeasuredEnd,
            debug = debug
        ) ?: return safeMeasuredEnd

        val adjusted = decision.chapterStart.coerceAtLeast(1)
        if (debug) {
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

    private fun findBoundaryLineStartNearEnd(
        raw: String,
        measuredEnd: Int,
        debug: Boolean
    ): ChapterBoundaryDecision? {
        val scanStart = (measuredEnd - MAX_CHAPTER_REWIND_CHARS).coerceAtLeast(0)
        val alignedStart = if (scanStart == 0) {
            0
        } else {
            val prevBreak = raw.lastIndexOf('\n', startIndex = scanStart - 1)
            if (prevBreak < 0) 0 else prevBreak + 1
        }

        var lineStart = alignedStart
        var candidate: ChapterBoundaryDecision? = null
        while (lineStart < measuredEnd) {
            val lineEnd = raw.indexOf('\n', lineStart).let { idx ->
                if (idx < 0 || idx > measuredEnd) measuredEnd else idx
            }

            val rewindChars = measuredEnd - lineStart
            if (rewindChars in 1..MAX_CHAPTER_REWIND_CHARS && lineStart > 0) {
                val line = raw.substring(lineStart, lineEnd).trim()
                if (line.isNotEmpty() && detector.isChapterBoundaryTitle(line)) {
                    val preludeInfo = strongPreludeInfo(raw, lineStart, debug)
                    if (preludeInfo != null) {
                        candidate = ChapterBoundaryDecision(
                            chapterStart = lineStart,
                            titlePreview = if (debug) line.take(MAX_DEBUG_PREVIEW) else "",
                            preludePreview = preludeInfo.preview
                        )
                    }
                }
            }

            if (lineEnd >= measuredEnd) {
                break
            }
            lineStart = lineEnd + 1
        }
        return candidate
    }

    private fun strongPreludeInfo(raw: String, lineStart: Int, debug: Boolean): PreludeInfo? {
        val beforeNewline = lineStart - 1
        if (beforeNewline !in raw.indices || raw[beforeNewline] != '\n') {
            return null
        }

        var i = beforeNewline - 1
        val previousBreak = if (i >= 0) raw.lastIndexOf('\n', startIndex = i) else -1
        val previousStart = previousBreak + 1

        while (i >= previousStart && raw[i].isWhitespace()) {
            i--
        }
        if (i < previousStart) {
            return PreludeInfo(preview = "")
        }

        val last = raw[i]
        if (!strongEndPunctuation.contains(last)) {
            return null
        }
        if (!debug) {
            return PreludeInfo(preview = "")
        }

        val preview = raw.substring(previousStart, beforeNewline).trim().take(MAX_DEBUG_PREVIEW)
        return PreludeInfo(preview = preview)
    }

    private data class PreludeInfo(
        val preview: String
    )

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
            return runCatching { Log.isLoggable(TAG, Log.DEBUG) }.getOrDefault(false)
        }

        private fun logDebug(tag: String, message: String) {
            runCatching { Log.d(tag, message) }
        }
    }
}
