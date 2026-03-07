package com.ireader.core.common.android.typography

import android.graphics.text.LineBreakConfig
import android.text.Layout
import com.ireader.reader.api.render.PAGE_PADDING_BOTTOM_DP_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_PADDING_TOP_DP_EXTRA_KEY
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MAX_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MIN_DP
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.effectivePagePaddingDp

data class AndroidLineBreakConfig(
    val lineBreakStyle: Int,
    val lineBreakWordStyle: Int
)

data class ResolvedPagePaddingDp(
    val horizontal: Float,
    val top: Float,
    val bottom: Float
)

fun TextAlignMode.toAndroidLayoutAlignment(): Layout.Alignment {
    return when (this) {
        TextAlignMode.START -> Layout.Alignment.ALIGN_NORMAL
        TextAlignMode.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
    }
}

fun BreakStrategyMode.toAndroidBreakStrategy(): Int {
    return when (this) {
        BreakStrategyMode.SIMPLE -> Layout.BREAK_STRATEGY_SIMPLE
        BreakStrategyMode.BALANCED -> Layout.BREAK_STRATEGY_BALANCED
        BreakStrategyMode.HIGH_QUALITY -> Layout.BREAK_STRATEGY_HIGH_QUALITY
    }
}

fun txtAndroidLineBreakConfig(): AndroidLineBreakConfig {
    return AndroidLineBreakConfig(
        lineBreakStyle = LineBreakConfig.LINE_BREAK_STYLE_STRICT,
        lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
    )
}

fun HyphenationMode.toAndroidHyphenationFrequency(): Int {
    return when (this) {
        HyphenationMode.NONE -> Layout.HYPHENATION_FREQUENCY_NONE
        HyphenationMode.NORMAL -> Layout.HYPHENATION_FREQUENCY_NORMAL
        HyphenationMode.FULL -> Layout.HYPHENATION_FREQUENCY_FULL
    }
}

fun TextAlignMode.toAndroidJustificationMode(): Int {
    return when (this) {
        TextAlignMode.START -> Layout.JUSTIFICATION_MODE_NONE
        TextAlignMode.JUSTIFY -> Layout.JUSTIFICATION_MODE_INTER_CHARACTER
    }
}

fun RenderConfig.ReflowText.resolvePagePaddingDp(): ResolvedPagePaddingDp {
    val horizontal = effectivePagePaddingDp()
    return ResolvedPagePaddingDp(
        horizontal = horizontal,
        top = resolveVerticalPaddingDp(extra[PAGE_PADDING_TOP_DP_EXTRA_KEY], horizontal),
        bottom = resolveVerticalPaddingDp(extra[PAGE_PADDING_BOTTOM_DP_EXTRA_KEY], horizontal)
    )
}

private fun resolveVerticalPaddingDp(raw: String?, fallback: Float): Float {
    return (raw?.toFloatOrNull() ?: fallback)
        .takeIf(Float::isFinite)
        ?.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
        ?: fallback.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
}
