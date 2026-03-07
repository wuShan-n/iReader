@file:Suppress("MagicNumber")

package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.provider.ChapterDetector
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.runtime.LogicalParagraph
import com.ireader.engines.txt.internal.runtime.ProjectedTextRange
import com.ireader.engines.txt.internal.runtime.BreakResolver
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.PAGE_PADDING_BOTTOM_DP_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_PADDING_TOP_DP_EXTRA_KEY
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MAX_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MIN_DP
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.effectivePagePaddingDp
import com.ireader.reader.api.render.TextLayoutInput
import com.ireader.reader.api.render.TextLayouter
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.api.render.toTypographySpec
import kotlin.math.roundToInt

internal class TxtPageFitter(
    private val store: Utf16TextStore,
    private val blockStore: BlockStore,
    private val breakResolver: BreakResolver
) {
    private val pageEndAdjuster = TxtPageEndAdjuster(ChapterDetector())
    private var textLayouterFactory: TextLayouterFactory? = null
    private var textLayouter: TextLayouter? = null

    fun setTextLayouterFactory(factory: TextLayouterFactory?) {
        textLayouterFactory = factory
        textLayouter = null
    }

    fun fitPage(
        startOffset: Long,
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): TxtPageSlice {
        if (store.lengthCodeUnits <= 0L) {
            return TxtPageSlice(
                startOffset = 0L,
                endOffset = 0L,
                text = "",
                continuesParagraph = false,
                projectedBoundaryToRawOffsets = longArrayOf(0L)
            )
        }
        val safeStart = startOffset.coerceIn(0L, store.lengthCodeUnits)
        if (safeStart >= store.lengthCodeUnits) {
            return TxtPageSlice(
                startOffset = store.lengthCodeUnits,
                endOffset = store.lengthCodeUnits,
                text = "",
                continuesParagraph = false,
                projectedBoundaryToRawOffsets = longArrayOf(store.lengthCodeUnits)
            )
        }

        val input = layoutInput(config, constraints)
        val layouter = textLayouter()
        val pageText = StringBuilder(initialWindowChars(config, constraints))
        val pageRaw = StringBuilder(initialWindowChars(config, constraints))
        val projectedToRaw = LongArrayBuilder()
        projectedToRaw.add(safeStart)
        var adjusterRawWindow = ""
        var cursor = blockStore.anchorForOffset(safeStart)
        var didOverflow = false
        var batchSafety = 0

        while (cursor.utf16Offset < store.lengthCodeUnits && batchSafety < MAX_BATCHES) {
            val batch = blockStore.readParagraphs(
                startAnchor = cursor,
                codeUnitBudget = windowBudgetChars(config, constraints)
            )
            if (batch.paragraphs.isEmpty()) {
                break
            }
            batchSafety++

            for (paragraph in batch.paragraphs) {
                val candidateText = pageText.toString() + paragraph.displayText
                val measure = layouter.measure(candidateText, input)
                if (measure.endChar >= candidateText.length) {
                    appendProjection(
                        pageText = pageText,
                        pageRaw = pageRaw,
                        projectedToRaw = projectedToRaw,
                        projection = paragraph.projection,
                        displayEndExclusive = paragraph.displayText.length
                    )
                    adjusterRawWindow = pageRaw.toString()
                    cursor = paragraph.endAnchor
                    continue
                }

                val prefix = pageText.toString()
                val prefixRaw = pageRaw.toString()
                val fitDisplayChars = refineParagraphFit(
                    prefix = prefix,
                    paragraph = paragraph,
                    input = input,
                    layouter = layouter
                ).coerceIn(0, paragraph.displayText.length)
                if (fitDisplayChars > 0 || pageText.isEmpty()) {
                    val ensuredFit = if (fitDisplayChars == 0 && paragraph.displayText.isNotEmpty()) 1 else fitDisplayChars
                    appendProjection(
                        pageText = pageText,
                        pageRaw = pageRaw,
                        projectedToRaw = projectedToRaw,
                        projection = paragraph.projection,
                        displayEndExclusive = ensuredFit
                    )
                    adjusterRawWindow = prefixRaw + paragraph.projection.rawText
                } else {
                    adjusterRawWindow = prefixRaw + paragraph.projection.rawText
                }
                didOverflow = true
                break
            }

            if (didOverflow || batch.nextAnchor == null) {
                break
            }
            cursor = batch.nextAnchor
        }

        if (pageText.isEmpty() && safeStart < store.lengthCodeUnits) {
            val projection = breakResolver.projectRange(safeStart, (safeStart + 1L).coerceAtMost(store.lengthCodeUnits))
            appendProjection(
                pageText = pageText,
                pageRaw = pageRaw,
                projectedToRaw = projectedToRaw,
                projection = projection,
                displayEndExclusive = projection.displayText.length.coerceAtLeast(1)
            )
            adjusterRawWindow = projection.rawText
        }

        val measuredEndOffset = projectedToRaw.last().coerceIn(safeStart, store.lengthCodeUnits)
        val adjustedEndOffset = adjustEndOffset(
            startOffset = safeStart,
            measuredEndOffset = measuredEndOffset,
            rawWindow = adjusterRawWindow.ifEmpty { pageRaw.toString() },
            projectedToRaw = projectedToRaw.toLongArray()
        )
        val finalBoundary = lowerBound(projectedToRaw.toLongArray(), adjustedEndOffset)
        val finalText = pageText.substring(0, finalBoundary.coerceIn(0, pageText.length))
        val finalProjectedToRaw = projectedToRaw.toLongArray().copyOf(finalBoundary + 1)
        val finalEnd = finalProjectedToRaw.lastOrNull()?.coerceAtLeast(safeStart) ?: safeStart

        return TxtPageSlice(
            startOffset = safeStart,
            endOffset = finalEnd,
            text = finalText,
            continuesParagraph = continuesParagraph(finalEnd),
            projectedBoundaryToRawOffsets = finalProjectedToRaw
        )
    }

    private fun adjustEndOffset(
        startOffset: Long,
        measuredEndOffset: Long,
        rawWindow: String,
        projectedToRaw: LongArray
    ): Long {
        if (rawWindow.isEmpty()) {
            return measuredEndOffset
        }
        val measuredEndLocal = (measuredEndOffset - startOffset).toInt().coerceIn(0, rawWindow.length)
        if (measuredEndLocal <= 0 || measuredEndLocal >= rawWindow.length) {
            return measuredEndOffset
        }
        val adjustedLocal = pageEndAdjuster.adjust(
            raw = rawWindow,
            measuredEnd = measuredEndLocal,
            rawLength = rawWindow.length,
            pageStartOffset = startOffset
        )
        val adjustedGlobal = (startOffset + adjustedLocal.toLong()).coerceIn(startOffset, measuredEndOffset)
        val boundary = lowerBound(projectedToRaw, adjustedGlobal)
        return projectedToRaw.getOrElse(boundary) { measuredEndOffset }
    }

    private fun continuesParagraph(endOffset: Long): Boolean {
        if (endOffset <= 0L || endOffset >= store.lengthCodeUnits) {
            return false
        }
        val previousOffset = endOffset - 1L
        val previousChar = store.readString(previousOffset, 1).firstOrNull() ?: return false
        if (previousChar != '\n') {
            return true
        }
        return breakResolver.stateAt(previousOffset)?.emitsVisibleNewline == false
    }

    private fun appendProjection(
        pageText: StringBuilder,
        pageRaw: StringBuilder,
        projectedToRaw: LongArrayBuilder,
        projection: ProjectedTextRange,
        displayEndExclusive: Int
    ) {
        val safeDisplayEnd = displayEndExclusive.coerceIn(0, projection.displayText.length)
        if (safeDisplayEnd > 0) {
            pageText.append(projection.displayText, 0, safeDisplayEnd)
            for (index in 1..safeDisplayEnd) {
                projectedToRaw.add(projection.projectedBoundaryToRawOffsets[index])
            }
        }
        val rawEndOffset = projection.projectedBoundaryToRawOffsets[safeDisplayEnd]
        val rawLength = (rawEndOffset - projection.rawStartOffset).toInt().coerceAtLeast(0)
        if (rawLength > 0) {
            pageRaw.append(projection.rawText, 0, rawLength.coerceAtMost(projection.rawText.length))
        }
    }

    private fun refineParagraphFit(
        prefix: String,
        paragraph: LogicalParagraph,
        input: TextLayoutInput,
        layouter: TextLayouter
    ): Int {
        if (paragraph.displayText.isEmpty()) {
            return 0
        }
        var low = 0
        var high = paragraph.displayText.length
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            val candidate = prefix + paragraph.displayText.substring(0, mid)
            val measure = layouter.measure(candidate, input)
            if (measure.endChar >= candidate.length) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return low
    }

    private fun layoutInput(
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): TextLayoutInput {
        val typography = config.toTypographySpec()
        val pagePadding = resolvePagePadding(config)
        val horizontalPaddingPx = (pagePadding.horizontal * constraints.density).roundToInt()
        val topPaddingPx = (pagePadding.top * constraints.density).roundToInt()
        val bottomPaddingPx = (pagePadding.bottom * constraints.density).roundToInt()
        return TextLayoutInput(
            widthPx = (constraints.viewportWidthPx - horizontalPaddingPx * 2).coerceAtLeast(1),
            heightPx = (constraints.viewportHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(1),
            fontSizeSp = typography.fontSizeSp,
            lineHeightMult = typography.lineHeightMult,
            textAlign = TextAlignMode.JUSTIFY,
            breakStrategy = typography.breakStrategy,
            hyphenationMode = typography.hyphenationMode,
            includeFontPadding = typography.includeFontPadding,
            fontFamilyName = typography.fontFamilyName,
            paragraphSpacingPx = (typography.paragraphSpacingDp * constraints.density).roundToInt()
        )
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

    private fun initialWindowChars(
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): Int {
        val area = constraints.viewportWidthPx.toLong() * constraints.viewportHeightPx.toLong()
        val scale = (config.fontSizeSp * constraints.fontScale).coerceAtLeast(10f)
        val rough = (area / (scale * 22f)).toInt()
        return rough.coerceIn(2_500, 16_000)
    }

    private fun windowBudgetChars(
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): Int {
        return (initialWindowChars(config, constraints) * 3).coerceIn(8_000, 96_000)
    }

    private fun lowerBound(values: LongArray, target: Long): Int {
        var low = 0
        var high = values.lastIndex
        var answer = values.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (values[mid] >= target) {
                answer = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return answer.coerceIn(0, values.lastIndex)
    }

    private class LongArrayBuilder(initialCapacity: Int = 64) {
        private var values = LongArray(initialCapacity.coerceAtLeast(4))
        var size: Int = 0
            private set

        fun add(value: Long) {
            ensureCapacity(size + 1)
            values[size] = value
            size++
        }

        fun last(): Long {
            return values[(size - 1).coerceAtLeast(0)]
        }

        fun toLongArray(): LongArray {
            return values.copyOf(size)
        }

        private fun ensureCapacity(required: Int) {
            if (required <= values.size) {
                return
            }
            values = values.copyOf((values.size * 2).coerceAtLeast(required))
        }
    }

    private companion object {
        private const val MAX_BATCHES = 8
    }

    private data class PagePadding(
        val horizontal: Float,
        val top: Float,
        val bottom: Float
    )

    private fun resolvePagePadding(config: RenderConfig.ReflowText): PagePadding {
        val horizontal = config.effectivePagePaddingDp()
        return PagePadding(
            horizontal = horizontal,
            top = resolveVerticalPadding(config.extra[PAGE_PADDING_TOP_DP_EXTRA_KEY], horizontal),
            bottom = resolveVerticalPadding(config.extra[PAGE_PADDING_BOTTOM_DP_EXTRA_KEY], horizontal)
        )
    }

    private fun resolveVerticalPadding(raw: String?, fallback: Float): Float {
        return (raw?.toFloatOrNull() ?: fallback)
            .takeIf(Float::isFinite)
            ?.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
            ?: fallback.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
    }
}
