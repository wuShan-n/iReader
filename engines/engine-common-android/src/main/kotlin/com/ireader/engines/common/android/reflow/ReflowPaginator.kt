@file:Suppress("LongMethod", "MagicNumber", "ReturnCount")

package com.ireader.engines.common.android.reflow

import android.text.SpannableStringBuilder
import android.util.Log
import com.ireader.core.common.android.typography.prefersInterCharacterJustify
import com.ireader.engines.common.android.layout.StaticLayoutMeasurer
import com.ireader.engines.common.android.layout.TextPaintFactory
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
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

    fun setSoftBreakIndex(index: ReflowSoftBreakIndex?) {
        softBreakIndex = index
    }

    suspend fun pageAt(
        startOffset: Long,
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): ReflowPageSlice {
        if (source.lengthChars <= 0L) {
            return ReflowPageSlice(0L, 0L, "")
        }

        val start = startOffset.coerceIn(0L, source.lengthChars)
        if (start >= source.lengthChars) {
            return ReflowPageSlice(source.lengthChars, source.lengthChars, "")
        }

        val startsAtParagraphBoundary = startsAtParagraphBoundary(start)
        val typography = config.toTypographySpec()
        val paddingPx = (typography.pagePaddingDp * constraints.density).roundToInt()
        val width = (constraints.viewportWidthPx - paddingPx * 2).coerceAtLeast(1)
        val height = (constraints.viewportHeightPx - paddingPx * 2).coerceAtLeast(1)
        val paragraphSpacingPx = (typography.paragraphSpacingDp * constraints.density).roundToInt()
        val paint = TextPaintFactory.create(config, constraints)
        val paragraphIndentPx = (paint.textSize * typography.paragraphIndentEm).roundToInt()
            .coerceAtLeast(0)
        val softBreakProfile = SoftBreakTuningProfile.fromStorageValue(config.extra[SOFT_BREAK_PROFILE_EXTRA_KEY])
        val softBreakRules = SoftBreakRuleConfig.forProfile(softBreakProfile)
        val usingSoftBreakIndex = softBreakIndex != null

        var windowChars = initialWindowChars(config, constraints)
        var measuredEnd = 0
        var measuredText: CharSequence = ""
        var rawLength = 0
        var measuredRaw = ""

        while (true) {
            val toRead = min(windowChars.toLong(), source.lengthChars - start).toInt()
            val raw = source.readString(start, toRead)
            rawLength = raw.length
            measuredRaw = raw
            if (rawLength == 0) {
                return ReflowPageSlice(start, start, "")
            }

            val display = if (softBreakIndex != null) {
                applySoftBreakIndex(
                    start = start,
                    raw = raw,
                    paragraphSpacingPx = paragraphSpacingPx,
                    paragraphIndentPx = paragraphIndentPx,
                    startsAtParagraphBoundary = startsAtParagraphBoundary
                )
            } else {
                SoftBreakProcessor.process(
                    rawText = raw,
                    hardWrapLikely = hardWrapLikely,
                    paragraphSpacingPx = paragraphSpacingPx,
                    paragraphIndentPx = paragraphIndentPx,
                    startsAtParagraphBoundary = startsAtParagraphBoundary,
                    ruleConfig = softBreakRules
                )
            }
            measuredText = display
            val preferInterCharacterJustify = raw.prefersInterCharacterJustify()

            val measure = StaticLayoutMeasurer.measure(
                text = display,
                paint = paint,
                widthPx = width,
                heightPx = height,
                lineHeightMult = typography.lineHeightMult,
                textAlign = typography.textAlign,
                breakStrategy = typography.breakStrategy,
                hyphenationMode = typography.hyphenationMode,
                includeFontPadding = typography.includeFontPadding,
                preferInterCharacterJustify = preferInterCharacterJustify
            )
            measuredEnd = measure.endChar.coerceIn(0, rawLength)

            val consumedAllWindow = measuredEnd >= rawLength
            val reachedDocumentEnd = start + rawLength >= source.lengthChars
            if (!consumedAllWindow || reachedDocumentEnd || windowChars >= MAX_WINDOW_CHARS) {
                break
            }
            windowChars = (windowChars * 2).coerceAtMost(MAX_WINDOW_CHARS)
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
        var end = start + measuredEnd.toLong()
        if (end <= start) {
            end = (start + 1L).coerceAtMost(source.lengthChars)
            measuredEnd = (end - start).toInt()
            measuredText = measuredText.subSequence(0, measuredEnd)
        } else {
            measuredText = measuredText.subSequence(0, measuredEnd)
        }

        if (isDebugLoggingEnabled()) {
            val newlineStats = collectNewlineStats(
                raw = measuredRaw,
                display = measuredText,
                endExclusive = measuredEnd
            )
            logDebug(
                TAG,
                "pageAt start=$start end=$end source=${if (usingSoftBreakIndex) "index" else "runtime"} " +
                    "hardWrapLikely=$hardWrapLikely softBreakProfile=${softBreakProfile.storageValue} " +
                    "newlineSoft=${newlineStats.softBreaks} newlineHard=${newlineStats.hardBreaks} " +
                    "measuredEnd=$measuredEndBeforeAdjust adjustedEnd=$measuredEnd " +
                    "rewind=${measuredEndBeforeAdjust - measuredEnd}"
            )
        }

        return ReflowPageSlice(
            startOffset = start,
            endOffset = end,
            text = measuredText
        )
    }

    private fun applySoftBreakIndex(
        start: Long,
        raw: String,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean
    ): CharSequence {
        val builder = SpannableStringBuilder(raw)
        val hardBreakPositions = ArrayList<Int>()
        val end = start + raw.length.toLong()
        softBreakIndex?.forEachNewlineInRange(start, end) { offset, isSoft ->
            val local = (offset - start).toInt()
            if (local !in 0 until builder.length) {
                return@forEachNewlineInRange
            }
            if (isSoft) {
                builder.replace(local, local + 1, " ")
            } else {
                hardBreakPositions += local
            }
        }
        SoftBreakProcessor.decorateParagraphs(
            builder = builder,
            hardBreakPositions = hardBreakPositions.toIntArray(),
            paragraphSpacingPx = paragraphSpacingPx,
            paragraphIndentPx = paragraphIndentPx,
            startsAtParagraphBoundary = startsAtParagraphBoundary
        )
        return builder
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
