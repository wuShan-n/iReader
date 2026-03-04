@file:Suppress("LongMethod", "MagicNumber", "ReturnCount")

package com.ireader.engines.txt.internal.pagination

import android.text.SpannableStringBuilder
import com.ireader.engines.common.android.layout.StaticLayoutMeasurer
import com.ireader.engines.common.android.layout.TextPaintFactory
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.render.SoftBreakProcessor
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.toTypographySpec
import kotlin.math.min
import kotlin.math.roundToInt

internal class TxtPaginator(
    private val store: Utf16TextStore,
    private val meta: TxtMeta,
    private var softBreakIndex: SoftBreakIndex? = null
) {

    fun setSoftBreakIndex(index: SoftBreakIndex?) {
        softBreakIndex = index
    }

    suspend fun pageAt(
        startOffset: Long,
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): PageSlice {
        if (store.lengthChars <= 0L) {
            return PageSlice(0L, 0L, "")
        }

        val start = startOffset.coerceIn(0L, store.lengthChars)
        if (start >= store.lengthChars) {
            return PageSlice(store.lengthChars, store.lengthChars, "")
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

        var windowChars = initialWindowChars(config, constraints)
        var measuredEnd = 0
        var measuredText: CharSequence = ""
        var rawLength = 0
        var measuredRaw = ""

        while (true) {
            val toRead = min(windowChars.toLong(), store.lengthChars - start).toInt()
            val raw = store.readString(start, toRead)
            rawLength = raw.length
            measuredRaw = raw
            if (rawLength == 0) {
                return PageSlice(start, start, "")
            }

            val display = if (softBreakIndex != null && meta.hardWrapLikely) {
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
                    hardWrapLikely = meta.hardWrapLikely,
                    paragraphSpacingPx = paragraphSpacingPx,
                    paragraphIndentPx = paragraphIndentPx,
                    startsAtParagraphBoundary = startsAtParagraphBoundary
                )
            }
            measuredText = display

            val measure = StaticLayoutMeasurer.measure(
                text = display,
                paint = paint,
                widthPx = width,
                heightPx = height,
                lineHeightMult = typography.lineHeightMult,
                textAlign = typography.textAlign,
                breakStrategy = typography.breakStrategy,
                hyphenationMode = typography.hyphenationMode,
                includeFontPadding = typography.includeFontPadding
            )
            measuredEnd = measure.endChar.coerceIn(0, rawLength)

            val consumedAllWindow = measuredEnd >= rawLength
            val reachedDocumentEnd = start + rawLength >= store.lengthChars
            if (!consumedAllWindow || reachedDocumentEnd || windowChars >= MAX_WINDOW_CHARS) {
                break
            }
            windowChars = (windowChars * 2).coerceAtMost(MAX_WINDOW_CHARS)
        }

        var end = start + measuredEnd.toLong()
        if (measuredEnd in 1 until rawLength && start + rawLength.toLong() < store.lengthChars) {
            val adjusted = adjustMeasuredEndForParagraphTail(measuredRaw, measuredEnd, rawLength)
            if (adjusted in 1 until measuredEnd) {
                measuredEnd = adjusted
                end = start + measuredEnd.toLong()
            }
        }
        if (end <= start) {
            end = (start + 1L).coerceAtMost(store.lengthChars)
            measuredEnd = (end - start).toInt()
            measuredText = measuredText.subSequence(0, measuredEnd)
        } else {
            measuredText = measuredText.subSequence(0, measuredEnd)
        }

        return PageSlice(
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
        val previous = store.readString(offset - 1L, 1)
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

    companion object {
        private const val MAX_WINDOW_CHARS = 96_000
        private const val MIN_TAIL_CHARS_FOR_REWIND = 14
        private const val MIN_PARAGRAPH_CHARS_FOR_REWIND = 24
        private const val MAX_REWIND_CHARS = 240

        internal fun adjustMeasuredEndForParagraphTail(
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
                return clampedEnd
            }
            val previousBreak = raw.lastIndexOf('\n', startIndex = lineBreak - 1)
            val paragraphStart = previousBreak + 1
            val tailChars = clampedEnd - (lineBreak + 1)
            if (tailChars <= 0 || tailChars >= MIN_TAIL_CHARS_FOR_REWIND) {
                return clampedEnd
            }
            val paragraphChars = clampedEnd - paragraphStart
            if (paragraphChars < MIN_PARAGRAPH_CHARS_FOR_REWIND) {
                return clampedEnd
            }
            val candidate = lineBreak + 1
            if (clampedEnd - candidate > MAX_REWIND_CHARS) {
                return clampedEnd
            }
            return candidate
        }
    }
}
