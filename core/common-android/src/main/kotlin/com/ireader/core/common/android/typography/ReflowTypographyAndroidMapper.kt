package com.ireader.core.common.android.typography

import android.graphics.text.LineBreakConfig
import android.text.Layout
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode

data class AndroidLineBreakConfig(
    val lineBreakStyle: Int,
    val lineBreakWordStyle: Int
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

fun BreakStrategyMode.effectiveForInterCharacterScript(
    preferInterCharacter: Boolean
): BreakStrategyMode {
    if (!preferInterCharacter) {
        return this
    }
    return when (this) {
        BreakStrategyMode.BALANCED -> BreakStrategyMode.SIMPLE
        else -> this
    }
}

fun resolveAndroidLineBreakConfig(
    preferInterCharacter: Boolean
): AndroidLineBreakConfig? {
    if (!preferInterCharacter) {
        return null
    }
    return AndroidLineBreakConfig(
        lineBreakStyle = LineBreakConfig.LINE_BREAK_STYLE_NONE,
        lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE
    )
}

fun HyphenationMode.toAndroidHyphenationFrequency(): Int {
    return when (this) {
        HyphenationMode.NONE -> Layout.HYPHENATION_FREQUENCY_NONE
        HyphenationMode.NORMAL -> Layout.HYPHENATION_FREQUENCY_NORMAL
        HyphenationMode.FULL -> Layout.HYPHENATION_FREQUENCY_FULL
    }
}

fun TextAlignMode.toAndroidJustificationMode(
    preferInterCharacter: Boolean = false
): Int {
    return when (this) {
        TextAlignMode.START -> Layout.JUSTIFICATION_MODE_NONE
        TextAlignMode.JUSTIFY -> {
            if (preferInterCharacter) {
                Layout.JUSTIFICATION_MODE_INTER_CHARACTER
            } else {
                Layout.JUSTIFICATION_MODE_INTER_WORD
            }
        }
    }
}
