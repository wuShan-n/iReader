@file:Suppress("LongMethod", "MagicNumber", "ReturnCount")

package com.ireader.engines.txt.internal.pagination

import android.text.SpannableStringBuilder
import android.text.Spanned
import com.ireader.engines.common.android.layout.StaticLayoutMeasurer
import com.ireader.engines.common.android.layout.TextPaintFactory
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.render.ParagraphSpacingSpan
import com.ireader.engines.txt.internal.render.SoftBreakProcessor
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
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

        val paddingPx = (config.pagePaddingDp * constraints.density).roundToInt()
        val width = (constraints.viewportWidthPx - paddingPx * 2).coerceAtLeast(1)
        val height = (constraints.viewportHeightPx - paddingPx * 2).coerceAtLeast(1)
        val paragraphSpacingPx = (config.paragraphSpacingDp * constraints.density).roundToInt()
        val paint = TextPaintFactory.create(config, constraints)

        var windowChars = initialWindowChars(config, constraints)
        var measuredEnd = 0
        var measuredText: CharSequence = ""
        var rawLength = 0

        while (true) {
            val toRead = min(windowChars.toLong(), store.lengthChars - start).toInt()
            val raw = store.readString(start, toRead)
            rawLength = raw.length
            if (rawLength == 0) {
                return PageSlice(start, start, "")
            }

            val display = if (softBreakIndex != null && meta.hardWrapLikely) {
                applySoftBreakIndex(
                    start = start,
                    raw = raw,
                    paragraphSpacingPx = paragraphSpacingPx
                )
            } else {
                SoftBreakProcessor.process(
                    rawText = raw,
                    hardWrapLikely = meta.hardWrapLikely,
                    paragraphSpacingPx = paragraphSpacingPx
                )
            }
            measuredText = display

            val measure = StaticLayoutMeasurer.measure(
                text = display,
                paint = paint,
                widthPx = width,
                heightPx = height,
                lineHeightMult = config.lineHeightMult,
                hyphenation = config.hyphenation
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
        paragraphSpacingPx: Int
    ): CharSequence {
        val builder = SpannableStringBuilder(raw)
        val end = start + raw.length.toLong()
        softBreakIndex?.forEachNewlineInRange(start, end) { offset, isSoft ->
            val local = (offset - start).toInt()
            if (local !in 0 until builder.length) {
                return@forEachNewlineInRange
            }
            if (isSoft) {
                builder.replace(local, local + 1, " ")
            } else if (paragraphSpacingPx > 0) {
                applyParagraphSpan(builder, local, paragraphSpacingPx)
            }
        }
        return builder
    }

    private fun applyParagraphSpan(
        builder: SpannableStringBuilder,
        lineBreakIndex: Int,
        paragraphSpacingPx: Int
    ) {
        val next = if (lineBreakIndex + 1 < builder.length) {
            builder[lineBreakIndex + 1]
        } else {
            null
        }
        if (next == '\n') {
            return
        }
        builder.setSpan(
            ParagraphSpacingSpan(paragraphSpacingPx),
            lineBreakIndex,
            lineBreakIndex + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
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

    private companion object {
        private const val MAX_WINDOW_CHARS = 96_000
    }
}
