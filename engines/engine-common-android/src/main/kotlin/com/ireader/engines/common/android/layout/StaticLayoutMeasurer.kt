@file:Suppress("LongParameterList")

package com.ireader.engines.common.android.layout

import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
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
            .setHyphenationFrequency(hyphenationMode.toAndroidHyphenationFrequency())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setBreakStrategy(breakStrategy.toAndroidBreakStrategy())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setJustificationMode(textAlign.toAndroidJustificationMode())
        }

        val layout = builder.build()

        if (layout.lineCount == 0) {
            return MeasureResult(
                endChar = 0,
                lineCount = 0,
                lastVisibleLine = -1
            )
        }

        val contentHeight = heightPx.coerceAtLeast(1)
        var lastVisibleLine = -1
        var line = 0
        while (line < layout.lineCount) {
            if (layout.getLineBottom(line) <= contentHeight) {
                lastVisibleLine = line
                line++
            } else {
                break
            }
        }
        if (lastVisibleLine < 0) {
            lastVisibleLine = 0
        }
        val end = layout.getLineEnd(lastVisibleLine).coerceAtLeast(0).coerceAtMost(text.length)
        return MeasureResult(
            endChar = end,
            lineCount = lastVisibleLine + 1,
            lastVisibleLine = lastVisibleLine
        )
    }

}
