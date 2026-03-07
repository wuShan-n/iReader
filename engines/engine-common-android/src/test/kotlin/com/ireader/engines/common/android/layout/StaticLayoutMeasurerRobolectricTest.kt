package com.ireader.engines.common.android.layout

import android.text.Layout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.ireader.core.common.android.typography.txtAndroidLineBreakConfig
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
    @Config(sdk = [36])
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
            includeFontPadding = false
        )

        val textView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            setLineSpacing(0f, lineHeightMult)
            includeFontPadding = false
            breakStrategy = Layout.BREAK_STRATEGY_BALANCED
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
            justificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER
            setFallbackLineSpacing(true)
            val lineBreakConfig = txtAndroidLineBreakConfig()
            lineBreakStyle = lineBreakConfig.lineBreakStyle
            lineBreakWordStyle = lineBreakConfig.lineBreakWordStyle
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

    @Test
    @Config(sdk = [36])
    fun `measure should match TextView visible end with asymmetric paddings`() {
        val context = RuntimeEnvironment.getApplication()
        val metrics = context.resources.displayMetrics
        val viewportWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 360f, metrics).toInt()
        val viewportHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 620f, metrics).toInt()
        val horizontalPaddingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, metrics).toInt()
        val topPaddingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14f, metrics).toInt()
        val bottomPaddingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, metrics).toInt()
        val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, metrics)
        val lineHeightMult = 1.7f
        val sampleText = buildString {
            repeat(64) {
                append("这是用于上下边距一致性校验的正文段落，确保分页测量和实际 TextView 可见范围完全一致。")
            }
        }

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
        }

        val measure = StaticLayoutMeasurer.measure(
            text = sampleText,
            paint = paint,
            widthPx = viewportWidthPx - horizontalPaddingPx * 2,
            heightPx = viewportHeightPx - topPaddingPx - bottomPaddingPx,
            lineHeightMult = lineHeightMult,
            textAlign = TextAlignMode.JUSTIFY,
            breakStrategy = BreakStrategyMode.HIGH_QUALITY,
            hyphenationMode = HyphenationMode.NONE,
            includeFontPadding = false
        )

        val textView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            setLineSpacing(0f, lineHeightMult)
            includeFontPadding = false
            breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            justificationMode = Layout.JUSTIFICATION_MODE_INTER_CHARACTER
            setFallbackLineSpacing(true)
            val lineBreakConfig = txtAndroidLineBreakConfig()
            lineBreakStyle = lineBreakConfig.lineBreakStyle
            lineBreakWordStyle = lineBreakConfig.lineBreakWordStyle
            gravity = Gravity.TOP or Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(horizontalPaddingPx, topPaddingPx, horizontalPaddingPx, bottomPaddingPx)
            text = sampleText
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(viewportWidthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(viewportHeightPx, View.MeasureSpec.EXACTLY)
        textView.measure(widthSpec, heightSpec)
        textView.layout(0, 0, viewportWidthPx, viewportHeightPx)

        val layout = checkNotNull(textView.layout)
        val expectedEnd = layout.getLineEnd(
            computeLastVisibleLine(layout, viewportHeightPx - topPaddingPx - bottomPaddingPx)
        ).coerceIn(0, sampleText.length)

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
