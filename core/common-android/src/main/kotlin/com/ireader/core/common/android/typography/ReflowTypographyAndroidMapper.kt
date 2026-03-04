package com.ireader.core.common.android.typography

import android.text.Layout
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode

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
