@file:Suppress("LongParameterList")

package com.ireader.engines.common.android.layout

import android.graphics.text.LineBreakConfig
import android.text.StaticLayout
import android.text.TextPaint
import com.ireader.core.common.android.typography.txtAndroidLineBreakConfig
import com.ireader.core.common.android.typography.toAndroidBreakStrategy
import com.ireader.core.common.android.typography.toAndroidHyphenationFrequency
import com.ireader.core.common.android.typography.toAndroidJustificationMode
import com.ireader.core.common.android.typography.toAndroidLayoutAlignment
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode

data class MeasureResult(
    val endChar: Int,
    val lineCount: Int,
    val lastVisibleLine: Int
)

object StaticLayoutMeasurer {

    fun measure(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        heightPx: Int,
        lineHeightMult: Float,
        textAlign: TextAlignMode,
        breakStrategy: BreakStrategyMode,
        hyphenationMode: HyphenationMode,
        includeFontPadding: Boolean
    ): MeasureResult {
        if (text.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            return MeasureResult(
                endChar = 0,
                lineCount = 0,
                lastVisibleLine = -1
            )
        }
        val lineBreakConfig = txtAndroidLineBreakConfig()

        val builder = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            paint,
            widthPx.coerceAtLeast(1)
        )
            .setAlignment(textAlign.toAndroidLayoutAlignment())
            .setIncludePad(includeFontPadding)
            .setLineSpacing(0f, lineHeightMult)
            .setUseLineSpacingFromFallbacks(true)
            .setHyphenationFrequency(hyphenationMode.toAndroidHyphenationFrequency())
            .setBreakStrategy(breakStrategy.toAndroidBreakStrategy())
            .setJustificationMode(textAlign.toAndroidJustificationMode())
            .setLineBreakConfig(
                LineBreakConfig.Builder()
                    .setLineBreakStyle(lineBreakConfig.lineBreakStyle)
                    .setLineBreakWordStyle(lineBreakConfig.lineBreakWordStyle)
                    .build()
            )

        val layout = builder.build()

        if (layout.lineCount == 0) {
            return MeasureResult(
                endChar = 0,
                lineCount = 0,
                lastVisibleLine = -1
            )
        }

        val contentHeight = heightPx.coerceAtLeast(1)
        val lastVisibleLine = findLastVisibleLine(layout, contentHeight)
        val end = layout.getLineEnd(lastVisibleLine).coerceAtLeast(0).coerceAtMost(text.length)
        return MeasureResult(
            endChar = end,
            lineCount = lastVisibleLine + 1,
            lastVisibleLine = lastVisibleLine
        )
    }

    private fun findLastVisibleLine(layout: StaticLayout, contentHeight: Int): Int {
        var low = 0
        var high = layout.lineCount - 1
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (layout.getLineBottom(mid) <= contentHeight) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer.coerceAtLeast(0)
    }
}
