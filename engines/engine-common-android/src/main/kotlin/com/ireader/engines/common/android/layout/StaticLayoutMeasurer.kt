@file:Suppress("LongParameterList")

package com.ireader.engines.common.android.layout

import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
            .setAlignment(layoutAlignment(textAlign))
            .setIncludePad(includeFontPadding)
            .setLineSpacing(0f, lineHeightMult)
            .setHyphenationFrequency(hyphenationFrequency(hyphenationMode))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setBreakStrategy(breakStrategy(breakStrategy))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setJustificationMode(justificationMode(textAlign))
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

    internal fun layoutAlignment(mode: TextAlignMode): Layout.Alignment {
        return when (mode) {
            TextAlignMode.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignMode.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
        }
    }

    internal fun breakStrategy(mode: BreakStrategyMode): Int {
        return when (mode) {
            BreakStrategyMode.SIMPLE -> Layout.BREAK_STRATEGY_SIMPLE
            BreakStrategyMode.BALANCED -> Layout.BREAK_STRATEGY_BALANCED
            BreakStrategyMode.HIGH_QUALITY -> Layout.BREAK_STRATEGY_HIGH_QUALITY
        }
    }

    internal fun hyphenationFrequency(mode: HyphenationMode): Int {
        return when (mode) {
            HyphenationMode.NONE -> Layout.HYPHENATION_FREQUENCY_NONE
            HyphenationMode.NORMAL -> Layout.HYPHENATION_FREQUENCY_NORMAL
            HyphenationMode.FULL -> Layout.HYPHENATION_FREQUENCY_FULL
        }
    }

    internal fun justificationMode(mode: TextAlignMode): Int {
        return when (mode) {
            TextAlignMode.START -> Layout.JUSTIFICATION_MODE_NONE
            TextAlignMode.JUSTIFY -> Layout.JUSTIFICATION_MODE_INTER_WORD
        }
    }
}
