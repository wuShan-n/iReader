@file:Suppress("LongParameterList")

package com.ireader.engines.txt.internal.layout

import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

internal data class MeasureResult(
    val endChar: Int,
    val lineCount: Int
)

internal object StaticLayoutMeasurer {

    fun measure(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        heightPx: Int,
        lineHeightMult: Float,
        hyphenation: Boolean
    ): MeasureResult {
        val layout = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            paint,
            widthPx.coerceAtLeast(1)
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, lineHeightMult)
            .setHyphenationFrequency(
                if (hyphenation) Layout.HYPHENATION_FREQUENCY_NORMAL else Layout.HYPHENATION_FREQUENCY_NONE
            )
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                }
            }
            .build()

        if (layout.lineCount == 0) {
            return MeasureResult(endChar = 0, lineCount = 0)
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
        return MeasureResult(endChar = end, lineCount = lastVisibleLine + 1)
    }
}
