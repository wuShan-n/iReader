package com.ireader.feature.reader.ui.components

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.effectivePagePaddingDp

@Composable
fun TextPage(
    content: RenderContent.Text,
    reflowConfig: RenderConfig.ReflowText?,
    modifier: Modifier = Modifier
) {
    val config = reflowConfig ?: RenderConfig.ReflowText()
    val density = LocalDensity.current
    val pagePaddingPx = with(density) { config.effectivePagePaddingDp().dp.roundToPx() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isFocusable = false
                isClickable = false
                overScrollMode = View.OVER_SCROLL_NEVER
                gravity = Gravity.TOP or Gravity.START
            }
        },
        update = { textView ->
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp)
            textView.setLineSpacing(0f, config.lineHeightMult)
            textView.includeFontPadding = config.includeFontPadding
            textView.setPadding(pagePaddingPx, pagePaddingPx, pagePaddingPx, pagePaddingPx)
            textView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textView.gravity = Gravity.TOP or Gravity.START
            val familyName = config.fontFamilyName
            textView.typeface = if (familyName.isNullOrBlank()) {
                Typeface.DEFAULT
            } else {
                Typeface.create(familyName, Typeface.NORMAL)
            }
            textView.text = content.text

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                textView.breakStrategy = when (config.breakStrategy) {
                    BreakStrategyMode.SIMPLE -> Layout.BREAK_STRATEGY_SIMPLE
                    BreakStrategyMode.BALANCED -> Layout.BREAK_STRATEGY_BALANCED
                    BreakStrategyMode.HIGH_QUALITY -> Layout.BREAK_STRATEGY_HIGH_QUALITY
                }
                textView.hyphenationFrequency = when (config.hyphenationMode) {
                    HyphenationMode.NONE -> Layout.HYPHENATION_FREQUENCY_NONE
                    HyphenationMode.NORMAL -> Layout.HYPHENATION_FREQUENCY_NORMAL
                    HyphenationMode.FULL -> Layout.HYPHENATION_FREQUENCY_FULL
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                textView.justificationMode = when (config.textAlign) {
                    TextAlignMode.START -> Layout.JUSTIFICATION_MODE_NONE
                    TextAlignMode.JUSTIFY -> Layout.JUSTIFICATION_MODE_INTER_WORD
                }
            }
            textView.requestLayout()
        }
    )
}
