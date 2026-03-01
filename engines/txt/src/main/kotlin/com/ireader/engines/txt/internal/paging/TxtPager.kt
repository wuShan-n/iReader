package com.ireader.engines.txt.internal.paging

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.engines.txt.internal.storage.TxtTextStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PageSlice(
    val startChar: Int,
    val endChar: Int,
    val text: String
)

internal class TxtPager(
    private val store: TxtTextStore
) {

    suspend fun pageAt(
        startChar: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): PageSlice {
        val total = store.totalChars().coerceAtLeast(0)
        if (total == 0) return PageSlice(0, 0, "")

        val start = startChar.coerceIn(0, total - 1)
        val paddingPx = dpToPx(config.pagePaddingDp, constraints.density)
        val contentWidth = max(1, constraints.viewportWidthPx - (paddingPx * 2))
        val contentHeight = max(1, constraints.viewportHeightPx - (paddingPx * 2))

        val estimated = estimateCharsPerPage(constraints, config)
        val candidateMax = min((estimated * 3).coerceAtLeast(2_000), total - start)
        val candidate = store.readChars(start, candidateMax)
        if (candidate.isEmpty()) return PageSlice(start, start, "")

        val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = spToPx(config.fontSizeSp, constraints.density, constraints.fontScale)
        }

        val layout = StaticLayout.Builder
            .obtain(candidate, 0, candidate.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, config.lineHeightMult)
            .apply {
                if (config.hyphenation) {
                    setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                }
            }
            .build()

        val targetY = (contentHeight - 1).coerceAtLeast(0)
        val lastLine = layout
            .getLineForVertical(targetY)
            .coerceIn(0, max(0, layout.lineCount - 1))
        var countFit = layout.getLineEnd(lastLine).coerceIn(0, candidate.length)
        if (countFit <= 0) countFit = 1

        val end = (start + countFit).coerceIn(start, total)
        val text = candidate.substring(0, countFit)
        return PageSlice(startChar = start, endChar = end, text = text)
    }

    fun estimateCharsPerPage(
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): Int {
        val paddingPx = dpToPx(config.pagePaddingDp, constraints.density)
        val contentWidth = max(1, constraints.viewportWidthPx - (paddingPx * 2))
        val contentHeight = max(1, constraints.viewportHeightPx - (paddingPx * 2))

        val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = spToPx(config.fontSizeSp, constraints.density, constraints.fontScale)
        }

        val metrics = textPaint.fontMetrics
        val lineHeightPx = max(1f, (metrics.descent - metrics.ascent) * config.lineHeightMult)
        val linesPerPage = max(1, (contentHeight / lineHeightPx).toInt())

        val avgCharWidth = max(1f, textPaint.measureText("中"))
        val charsPerLine = max(4, (contentWidth / avgCharWidth).toInt())

        return (linesPerPage * charsPerLine).coerceIn(200, 50_000)
    }

    private fun dpToPx(dp: Float, density: Float): Int = (dp * density).roundToInt()

    private fun spToPx(sp: Float, density: Float, fontScale: Float): Float = sp * density * fontScale
}
