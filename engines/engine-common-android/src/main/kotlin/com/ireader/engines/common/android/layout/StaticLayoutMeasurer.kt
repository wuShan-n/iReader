@file:Suppress("LongParameterList")

package com.ireader.engines.common.android.layout

import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import com.ireader.core.common.android.typography.resolveAndroidLineBreakConfig
import com.ireader.core.common.android.typography.toAndroidBreakStrategy
import com.ireader.core.common.android.typography.toAndroidHyphenationFrequency
import com.ireader.core.common.android.typography.toAndroidJustificationMode
import com.ireader.core.common.android.typography.toAndroidLayoutAlignment
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode
import java.lang.reflect.Method

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
        includeFontPadding: Boolean,
        preferInterCharacterJustify: Boolean
    ): MeasureResult {
        if (text.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            return MeasureResult(
                endChar = 0,
                lineCount = 0,
                lastVisibleLine = -1
            )
        }

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
            .setBreakStrategy(breakStrategy.toAndroidBreakStrategy())

        setUseLineSpacingFromFallbacksMethod?.let { method ->
            runCatching { method.invoke(builder, true) }
        }

        runCatching {
            builder.setJustificationMode(
                textAlign.toAndroidJustificationMode(
                    preferInterCharacter = preferInterCharacterJustify
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveAndroidLineBreakConfig(preferInterCharacterJustify)?.let { config ->
                runCatching {
                    builder.setLineBreakConfig(
                        LineBreakConfig.Builder()
                            .setLineBreakStyle(config.lineBreakStyle)
                            .setLineBreakWordStyle(config.lineBreakWordStyle)
                            .build()
                    )
                }
            }
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

    private val setUseLineSpacingFromFallbacksMethod: Method? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            StaticLayout.Builder::class.java.getMethod(
                "setUseLineSpacingFromFallbacks",
                Boolean::class.javaPrimitiveType
            )
        }.getOrNull()
    }

}
