package com.ireader.engines.common.android.layout

import android.os.Build
import android.text.Layout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.ireader.core.common.android.typography.resolveAndroidLineBreakConfig
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class StaticLayoutMeasurerRobolectricTest {

    @Test
    @Config(sdk = [33])
    fun `measure should match TextView visible end with same layout params`() {
        val context = RuntimeEnvironment.getApplication()
        val metrics = context.resources.displayMetrics
        val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320f, metrics).toInt()
        val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 520f, metrics).toInt()
        val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, metrics)
        val lineHeightMult = 1.85f
        val sampleText = buildString {
            repeat(56) {
                append("这是🙂用于分页一致性校验的混排段落，确保页底不会出现半行被截断。")
            }
        }

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
        }

        val measure = StaticLayoutMeasurer.measure(
            text = sampleText,
            paint = paint,
            widthPx = widthPx,
            heightPx = heightPx,
            lineHeightMult = lineHeightMult,
            textAlign = TextAlignMode.JUSTIFY,
            breakStrategy = BreakStrategyMode.BALANCED,
            hyphenationMode = HyphenationMode.NORMAL,
            includeFontPadding = false,
            preferInterCharacterJustify = true
        )

        val textView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            setLineSpacing(0f, lineHeightMult)
            includeFontPadding = false
            runCatching { breakStrategy = Layout.BREAK_STRATEGY_BALANCED }
            runCatching { hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL }
            runCatching { justificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER }
            runCatching { setFallbackLineSpacing(true) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val lineBreakConfig = checkNotNull(resolveAndroidLineBreakConfig(preferInterCharacter = true))
                lineBreakStyle = lineBreakConfig.lineBreakStyle
                lineBreakWordStyle = lineBreakConfig.lineBreakWordStyle
            }
            gravity = Gravity.TOP or Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            text = sampleText
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        textView.measure(widthSpec, heightSpec)
        textView.layout(0, 0, widthPx, heightPx)

        val layout = checkNotNull(textView.layout)
        val lastVisibleLine = computeLastVisibleLine(layout, heightPx)
        val expectedEnd = layout.getLineEnd(lastVisibleLine).coerceIn(0, sampleText.length)

        assertEquals(expectedEnd, measure.endChar)
    }

    private fun computeLastVisibleLine(layout: Layout, contentHeight: Int): Int {
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
            return 0
        }
        return lastVisibleLine
    }
}
