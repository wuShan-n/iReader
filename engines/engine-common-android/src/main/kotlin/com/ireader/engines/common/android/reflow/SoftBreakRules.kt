package com.ireader.engines.common.android.reflow

import java.util.Locale

const val SOFT_BREAK_PROFILE_EXTRA_KEY: String = "txt.soft_break_profile"

enum class SoftBreakTuningProfile(val storageValue: String) {
    STRICT("strict"),
    BALANCED("balanced"),
    AGGRESSIVE("aggressive");

    companion object {
        private val BY_STORAGE = entries.associateBy { it.storageValue }

        fun fromStorageValue(raw: String?): SoftBreakTuningProfile {
            val key = raw?.trim()?.lowercase(Locale.US)
            return if (key.isNullOrEmpty()) {
                BALANCED
            } else {
                BY_STORAGE[key] ?: BALANCED
            }
        }
    }
}

data class SoftBreakRuleConfig(
    val rulesVersion: Int,
    val minTypicalHardWrap: Int,
    val minTypicalNormal: Int,
    val maxTypical: Int,
    val baseScoreHardWrap: Int,
    val baseScoreNormal: Int,
    val thresholdHardWrap: Int,
    val thresholdNormal: Int,
    val lenGapRatio: Float,
    val shortLineMinLength: Int,
    val shortLinePenaltyHardWrap: Int,
    val shortLinePenaltyNormal: Int,
    val sameIndentBoost: Int,
    val strongEndShortTailRatio: Float,
    val strongEndPenaltyShortTailHardWrap: Int,
    val strongEndPenaltyShortTailNormal: Int,
    val strongEndPenaltyHardWrap: Int,
    val strongEndPenaltyNormal: Int,
    val dialogueStartPenalty: Int,
    val nonHardWrapIndentPenalty: Int,
    val indentIncreaseHardBreakMin: Int,
    val nonHardWrapIndentChangeHardBreakMin: Int,
    val line0HighRatio: Float,
    val line0MidRatio: Float,
    val line0LowRatio: Float,
    val line1HighRatio: Float,
    val line1MidRatio: Float,
    val line1LowRatio: Float
) {
    companion object {
        private val BALANCED_BASE = SoftBreakRuleConfig(
            rulesVersion = 1,
            minTypicalHardWrap = 8,
            minTypicalNormal = 18,
            maxTypical = 200,
            baseScoreHardWrap = 2,
            baseScoreNormal = 0,
            thresholdHardWrap = 1,
            thresholdNormal = 7,
            lenGapRatio = 0.28f,
            shortLineMinLength = 8,
            shortLinePenaltyHardWrap = 1,
            shortLinePenaltyNormal = 3,
            sameIndentBoost = 1,
            strongEndShortTailRatio = 0.88f,
            strongEndPenaltyShortTailHardWrap = 1,
            strongEndPenaltyShortTailNormal = 4,
            strongEndPenaltyHardWrap = 1,
            strongEndPenaltyNormal = 2,
            dialogueStartPenalty = 1,
            nonHardWrapIndentPenalty = 1,
            indentIncreaseHardBreakMin = 2,
            nonHardWrapIndentChangeHardBreakMin = 2,
            line0HighRatio = 0.95f,
            line0MidRatio = 0.78f,
            line0LowRatio = 0.62f,
            line1HighRatio = 0.72f,
            line1MidRatio = 0.50f,
            line1LowRatio = 0.32f
        )

        fun forProfile(profile: SoftBreakTuningProfile): SoftBreakRuleConfig {
            return when (profile) {
                SoftBreakTuningProfile.STRICT -> BALANCED_BASE.copy(
                    thresholdHardWrap = 2,
                    thresholdNormal = 9,
                    lenGapRatio = 0.22f,
                    shortLinePenaltyHardWrap = 2,
                    shortLinePenaltyNormal = 4,
                    strongEndPenaltyHardWrap = 2,
                    strongEndPenaltyNormal = 3,
                    strongEndPenaltyShortTailHardWrap = 2,
                    strongEndPenaltyShortTailNormal = 5
                )

                SoftBreakTuningProfile.BALANCED -> BALANCED_BASE

                SoftBreakTuningProfile.AGGRESSIVE -> BALANCED_BASE.copy(
                    baseScoreHardWrap = 3,
                    thresholdHardWrap = 0,
                    thresholdNormal = 5,
                    lenGapRatio = 0.35f,
                    shortLinePenaltyHardWrap = 1,
                    shortLinePenaltyNormal = 2,
                    strongEndPenaltyHardWrap = 1,
                    strongEndPenaltyNormal = 1,
                    strongEndPenaltyShortTailHardWrap = 1,
                    strongEndPenaltyShortTailNormal = 2
                )
            }
        }
    }
}

data class SoftBreakDecision(
    val isSoft: Boolean,
    val score: Int,
    val threshold: Int,
    val reasons: Int
)

object SoftBreakDecisionReasons {
    const val EMPTY_LINE = 1 shl 0
    const val BOUNDARY_TITLE = 1 shl 1
    const val LIST_OR_DIALOGUE_START = 1 shl 2
    const val INDENT_INCREASE = 1 shl 3
    const val INDENT_SHIFT_IN_NORMAL = 1 shl 4
    const val LENGTH_STABLE = 1 shl 5
    const val LENGTH_GAP = 1 shl 6
    const val SHORT_LINE = 1 shl 7
    const val SAME_INDENT = 1 shl 8
    const val STRONG_END_PUNCT = 1 shl 9
    const val LINE0_DIALOGUE_START = 1 shl 10
    const val INDENT_PRESENT_IN_NORMAL = 1 shl 11

    fun describe(flags: Int): List<String> {
        val items = ArrayList<String>(8)
        if ((flags and EMPTY_LINE) != 0) items += "empty_line"
        if ((flags and BOUNDARY_TITLE) != 0) items += "boundary_title"
        if ((flags and LIST_OR_DIALOGUE_START) != 0) items += "list_or_dialogue_start"
        if ((flags and INDENT_INCREASE) != 0) items += "indent_increase"
        if ((flags and INDENT_SHIFT_IN_NORMAL) != 0) items += "indent_shift_in_normal"
        if ((flags and LENGTH_STABLE) != 0) items += "length_stable"
        if ((flags and LENGTH_GAP) != 0) items += "length_gap"
        if ((flags and SHORT_LINE) != 0) items += "short_line"
        if ((flags and SAME_INDENT) != 0) items += "same_indent"
        if ((flags and STRONG_END_PUNCT) != 0) items += "strong_end_punct"
        if ((flags and LINE0_DIALOGUE_START) != 0) items += "line0_dialogue_start"
        if ((flags and INDENT_PRESENT_IN_NORMAL) != 0) items += "indent_present_in_normal"
        return items
    }
}
