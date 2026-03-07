@file:Suppress("LongMethod", "MagicNumber", "ReturnCount")

package com.ireader.engines.common.android.reflow

import android.os.Trace
import android.util.Log
import com.ireader.core.common.android.typography.resolvePagePaddingDp
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.TextLayoutInput
import com.ireader.reader.api.render.TextLayouter
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.toTypographySpec
import kotlin.math.min
import kotlin.math.roundToInt

fun interface ReflowPageEndAdjuster {
    fun adjust(
        raw: String,
        measuredEnd: Int,
        rawLength: Int,
        pageStartOffset: Long
    ): Int

    companion object {
        val NONE: ReflowPageEndAdjuster = ReflowPageEndAdjuster { _, measuredEnd, _, _ -> measuredEnd }
    }
}

class ReflowPaginator(
    private val source: ReflowTextSource,
    private val hardWrapLikely: Boolean,
    private var softBreakIndex: ReflowSoftBreakIndex? = null,
    private val pageEndAdjuster: ReflowPageEndAdjuster = ReflowPageEndAdjuster.NONE
) {
    private var textLayouterFactory: TextLayouterFactory? = null
    private var textLayouter: TextLayouter? = null

    fun setSoftBreakIndex(index: ReflowSoftBreakIndex?) {
        softBreakIndex = index
    }

    fun setTextLayouterFactory(factory: TextLayouterFactory?) {
        textLayouterFactory = factory
        textLayouter = null
    }

    suspend fun pageAt(
        startOffset: Long,
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): ReflowPageSlice {
        Trace.beginSection("ReflowPaginator#pageAt")
        try {
            return pageAtInternal(startOffset = startOffset, config = config, constraints = constraints)
        } finally {
            Trace.endSection()
        }
    }

    private suspend fun pageAtInternal(
        startOffset: Long,
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): ReflowPageSlice {
        val startedNs = System.nanoTime()
        if (source.lengthChars <= 0L) {
            return ReflowPageSlice(
                startOffset = 0L,
                endOffset = 0L,
                text = "",
                continuesParagraph = false
            )
        }

        val start = startOffset.coerceIn(0L, source.lengthChars)
        if (start >= source.lengthChars) {
            return ReflowPageSlice(
                startOffset = source.lengthChars,
                endOffset = source.lengthChars,
                text = "",
                continuesParagraph = false
            )
        }

        val startsAtParagraphBoundary = startsAtParagraphBoundary(start)
        val typography = config.toTypographySpec()
        val pagePadding = config.resolvePagePaddingDp()
        val horizontalPaddingPx = (pagePadding.horizontal * constraints.density).roundToInt()
        val topPaddingPx = (pagePadding.top * constraints.density).roundToInt()
        val bottomPaddingPx = (pagePadding.bottom * constraints.density).roundToInt()
        val width = (constraints.viewportWidthPx - horizontalPaddingPx * 2).coerceAtLeast(1)
        val height = (constraints.viewportHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(1)
        val paragraphSpacingPx = (typography.paragraphSpacingDp * constraints.density).roundToInt()
        val paragraphIndentPx = 0
        val softBreakProfile = SoftBreakTuningProfile.fromStorageValue(config.extra[SOFT_BREAK_PROFILE_EXTRA_KEY])
        val softBreakRules = SoftBreakRuleConfig.forProfile(softBreakProfile)
        val softBreakSource = when {
            !hardWrapLikely -> "raw"
            softBreakIndex != null -> "index"
            else -> "runtime"
        }

        val initialWindow = initialWindowChars(config, constraints)
        val softWindowCap = (initialWindow * SOFT_WINDOW_CAP_MULTIPLIER)
            .coerceIn(initialWindow, MAX_WINDOW_CHARS)
        var windowChars = initialWindow
        var measuredEnd = 0
        var measuredText = ""
        var rawLength = 0
        var measuredRaw = ""
        var measuredWindow = ReflowTextWindow.identity("")
        val textLayouter = textLayouter()

        while (true) {
            val toRead = min(windowChars.toLong(), source.lengthChars - start).toInt()
            val window = source.readWindow(start, toRead)
            measuredWindow = window
            val raw = window.rawText
            rawLength = raw.length
            measuredRaw = raw
            if (rawLength == 0) {
                return ReflowPageSlice(
                    startOffset = start,
                    endOffset = start,
                    text = "",
                    continuesParagraph = false
                )
            }

            val display = when {
                window.displayText != window.rawText -> window.displayText
                !hardWrapLikely -> SoftBreakProcessor.renderRawPreservingBreaks(
                    rawText = raw,
                    paragraphSpacingPx = paragraphSpacingPx,
                    paragraphIndentPx = paragraphIndentPx,
                    startsAtParagraphBoundary = startsAtParagraphBoundary
                ).toString()

                softBreakIndex != null -> applySoftBreakIndexPlainText(
                    start = start,
                    raw = raw,
                    startsAtParagraphBoundary = startsAtParagraphBoundary
                )

                else -> SoftBreakProcessor.processForLayout(
                    rawText = raw,
                    hardWrapLikely = hardWrapLikely,
                    ruleConfig = softBreakRules
                ).text
            }
            measuredText = display

            val measure = textLayouter.measure(
                text = display,
                input = TextLayoutInput(
                    widthPx = width,
                    heightPx = height,
                    fontSizeSp = typography.fontSizeSp,
                    lineHeightMult = typography.lineHeightMult,
                    textAlign = TextAlignMode.JUSTIFY,
                    breakStrategy = typography.breakStrategy,
                    hyphenationMode = typography.hyphenationMode,
                    includeFontPadding = typography.includeFontPadding,
                    fontFamilyName = typography.fontFamilyName,
                    paragraphSpacingPx = paragraphSpacingPx
                )
            )
            measuredEnd = when {
                window.displayText != window.rawText -> {
                    val projectedEnd = measure.endChar.coerceIn(0, window.displayText.length)
                    window.projectedBoundaryToRawIndex[projectedEnd].coerceIn(0, rawLength)
                }

                else -> measure.endChar.coerceIn(0, rawLength)
            }

            val consumedAllWindow = measuredEnd >= rawLength
            val reachedDocumentEnd = start + rawLength >= source.lengthChars
            if (!consumedAllWindow || reachedDocumentEnd || windowChars >= softWindowCap) {
                break
            }
            windowChars = (windowChars * 2).coerceAtMost(softWindowCap)
        }

        val measuredEndBeforeAdjust = measuredEnd
        if (measuredEnd in 1 until rawLength) {
            measuredEnd = pageEndAdjuster.adjust(
                raw = measuredRaw,
                measuredEnd = measuredEnd,
                rawLength = rawLength,
                pageStartOffset = start
            ).coerceIn(1, measuredEnd)
        }
        var projectedEnd = measuredEnd
        if (measuredWindow.rawBoundaryToProjectedIndex.isNotEmpty()) {
            projectedEnd = measuredWindow.rawBoundaryToProjectedIndex[measuredEnd.coerceIn(
                0,
                measuredWindow.rawBoundaryToProjectedIndex.lastIndex
            )]
        }
        var end = start + measuredEnd.toLong()
        if (end <= start) {
            end = (start + 1L).coerceAtMost(source.lengthChars)
            measuredEnd = (end - start).toInt()
            projectedEnd = minOf(projectedEnd, measuredText.length)
            measuredText = measuredText.substring(0, projectedEnd)
        } else {
            projectedEnd = projectedEnd.coerceIn(0, measuredText.length)
            measuredText = measuredText.substring(0, projectedEnd)
        }

        if (isDebugLoggingEnabled()) {
            val newlineStats = collectNewlineStats(
                raw = measuredRaw,
                display = measuredText,
                endExclusive = measuredEnd
            )
            logDebug(
                TAG,
                    "pageAt start=$start end=$end source=$softBreakSource " +
                    "hardWrapLikely=$hardWrapLikely softBreakProfile=${softBreakProfile.storageValue} " +
                    "newlineSoft=${newlineStats.softBreaks} newlineHard=${newlineStats.hardBreaks} " +
                    "windowInitial=$initialWindow windowFinal=$windowChars softCap=$softWindowCap " +
                    "measuredEnd=$measuredEndBeforeAdjust adjustedEnd=$measuredEnd " +
                    "rewind=${measuredEndBeforeAdjust - measuredEnd} " +
                    "durationMs=${(System.nanoTime() - startedNs) / 1_000_000L}"
            )
        }

        val projectedBoundaryToRawOffsets = LongArray(projectedEnd + 1)
        for (index in 0..projectedEnd) {
            val localRaw = measuredWindow.projectedBoundaryToRawIndex
                .getOrElse(index) { measuredWindow.projectedBoundaryToRawIndex.lastOrNull() ?: 0 }
            projectedBoundaryToRawOffsets[index] = start + localRaw.toLong()
        }

        return ReflowPageSlice(
            startOffset = start,
            endOffset = end,
            text = measuredText,
            continuesParagraph = reflowPageContinuesParagraph(
                sourceLength = source.lengthChars,
                endOffset = end,
                raw = measuredRaw,
                display = measuredText,
                measuredEnd = measuredEnd,
                projectedEnd = projectedEnd,
                rawBoundaryToProjectedIndex = measuredWindow.rawBoundaryToProjectedIndex
            ),
            projectedBoundaryToRawOffsets = projectedBoundaryToRawOffsets
        )
    }

    private fun applySoftBreakIndexPlainText(
        start: Long,
        raw: String,
        startsAtParagraphBoundary: Boolean
    ): String {
        if (!startsAtParagraphBoundary && raw.isEmpty()) {
            return raw
        }
        val chars = raw.toCharArray()
        val end = start + raw.length.toLong()
        softBreakIndex?.forEachNewlineInRange(start, end) { offset, isSoft ->
            val local = (offset - start).toInt()
            if (local !in chars.indices) {
                return@forEachNewlineInRange
            }
            if (isSoft) {
                chars[local] = ' '
            }
        }
        return String(chars)
    }

    private fun textLayouter(): TextLayouter {
        val cached = textLayouter
        if (cached != null) {
            return cached
        }
        val factory = textLayouterFactory
            ?: error("TextLayouterFactory not set for TXT pagination")
        return factory.create(cacheSize = 0).also { created ->
            textLayouter = created
        }
    }

    private suspend fun startsAtParagraphBoundary(offset: Long): Boolean {
        if (offset <= 0L) {
            return true
        }
        val previous = source.readString(offset - 1L, 1)
        return previous.firstOrNull() == '\n'
    }

    private fun initialWindowChars(
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): Int {
        val area = constraints.viewportWidthPx.toLong() * constraints.viewportHeightPx.toLong()
        val scale = (config.fontSizeSp * constraints.fontScale).coerceAtLeast(10f)
        val rough = (area / (scale * 22f)).toInt()
        return rough.coerceIn(2_500, 16_000)
    }

    private fun collectNewlineStats(
        raw: String,
        display: CharSequence,
        endExclusive: Int
    ): NewlineStats {
        if (raw.isEmpty() || display.isEmpty() || endExclusive <= 0) {
            return NewlineStats(softBreaks = 0, hardBreaks = 0)
        }
        val limit = minOf(raw.length, display.length, endExclusive)
        var soft = 0
        var hard = 0
        for (i in 0 until limit) {
            if (raw[i] != '\n') {
                continue
            }
            if (display[i] == ' ') {
                soft++
            } else {
                hard++
            }
        }
        return NewlineStats(softBreaks = soft, hardBreaks = hard)
    }

    private data class NewlineStats(
        val softBreaks: Int,
        val hardBreaks: Int
    )

    companion object {
        private const val TAG = "ReflowPaginator"
        private const val MAX_WINDOW_CHARS = 96_000
        private const val SOFT_WINDOW_CAP_MULTIPLIER = 3
        private const val MIN_TAIL_CHARS_FOR_REWIND = 14
        private const val MIN_SENTENCE_TAIL_CHARS_FOR_REWIND = 12
        private const val MIN_PARAGRAPH_CHARS_FOR_REWIND = 24
        private const val MAX_REWIND_CHARS = 240
        private val STRONG_SENTENCE_BREAKS = charArrayOf('。', '！', '？', '.', '!', '?', ';', '；', ':', '：')

        fun adjustMeasuredEndForParagraphTail(
            raw: String,
            measuredEnd: Int,
            rawLength: Int
        ): Int {
            val clampedEnd = measuredEnd.coerceIn(0, rawLength)
            if (clampedEnd <= 0 || clampedEnd >= rawLength) {
                return clampedEnd
            }
            val lineBreak = raw.lastIndexOf('\n', startIndex = clampedEnd - 1)
            if (lineBreak <= 0 || lineBreak >= clampedEnd) {
                return adjustMeasuredEndForSentenceTail(
                    raw = raw,
                    clampedEnd = clampedEnd,
                    rawLength = rawLength
                )
            }
            val previousBreak = raw.lastIndexOf('\n', startIndex = lineBreak - 1)
            val paragraphStart = previousBreak + 1
            val tailChars = clampedEnd - (lineBreak + 1)
            if (tailChars <= 0 || tailChars >= MIN_TAIL_CHARS_FOR_REWIND) {
                return adjustMeasuredEndForSentenceTail(
                    raw = raw,
                    clampedEnd = clampedEnd,
                    rawLength = rawLength
                )
            }
            val paragraphChars = clampedEnd - paragraphStart
            if (paragraphChars < MIN_PARAGRAPH_CHARS_FOR_REWIND) {
                return adjustMeasuredEndForSentenceTail(
                    raw = raw,
                    clampedEnd = clampedEnd,
                    rawLength = rawLength
                )
            }
            val candidate = lineBreak + 1
            if (clampedEnd - candidate > MAX_REWIND_CHARS) {
                return adjustMeasuredEndForSentenceTail(
                    raw = raw,
                    clampedEnd = clampedEnd,
                    rawLength = rawLength
                )
            }
            return candidate
        }

        private fun adjustMeasuredEndForSentenceTail(
            raw: String,
            clampedEnd: Int,
            rawLength: Int
        ): Int {
            if (clampedEnd <= 0 || clampedEnd >= rawLength) {
                return clampedEnd
            }
            val sentenceBreak = raw.lastIndexOfAny(
                chars = STRONG_SENTENCE_BREAKS,
                startIndex = clampedEnd - 1
            )
            if (sentenceBreak <= 0 || sentenceBreak >= clampedEnd) {
                return clampedEnd
            }
            val candidate = sentenceBreak + 1
            val tailChars = clampedEnd - candidate
            if (tailChars <= 0 || tailChars >= MIN_SENTENCE_TAIL_CHARS_FOR_REWIND) {
                return clampedEnd
            }
            val previousBreak = raw.lastIndexOf('\n', startIndex = sentenceBreak - 1)
            val blockStart = if (previousBreak >= 0) previousBreak + 1 else 0
            val blockChars = clampedEnd - blockStart
            if (blockChars < MIN_PARAGRAPH_CHARS_FOR_REWIND) {
                return clampedEnd
            }
            if (clampedEnd - candidate > MAX_REWIND_CHARS) {
                return clampedEnd
            }
            return candidate
        }

        private fun isDebugLoggingEnabled(): Boolean {
            return runCatching { Log.isLoggable(TAG, Log.DEBUG) }
                .getOrDefault(false)
        }

        private fun logDebug(tag: String, message: String) {
            runCatching { Log.d(tag, message) }
        }
    }
}

internal fun reflowPageContinuesParagraph(
    sourceLength: Long,
    endOffset: Long,
    raw: String,
    display: CharSequence,
    measuredEnd: Int,
    projectedEnd: Int = measuredEnd,
    rawBoundaryToProjectedIndex: IntArray = IntArray(raw.length + 1) { it }
): Boolean {
    if (measuredEnd <= 0 || endOffset >= sourceLength) {
        return false
    }
    val lastRawIndex = measuredEnd - 1
    if (lastRawIndex !in raw.indices) {
        return false
    }
    if (raw[lastRawIndex] != '\n') {
        return true
    }
    val projectedStart = rawBoundaryToProjectedIndex[lastRawIndex].coerceIn(0, display.length)
    val projectedStop = rawBoundaryToProjectedIndex[measuredEnd].coerceIn(0, display.length)
    if (projectedStop <= projectedStart) {
        return true
    }
    val visibleIndex = (projectedEnd - 1).coerceIn(0, display.length - 1)
    return display[visibleIndex] != '\n'
}
