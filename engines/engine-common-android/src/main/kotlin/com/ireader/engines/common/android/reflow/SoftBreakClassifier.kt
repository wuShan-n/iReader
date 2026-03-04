package com.ireader.engines.common.android.reflow

import kotlin.math.abs

data class SoftBreakLineInfo(
    val length: Int,
    val leadingSpaces: Int,
    val firstNonSpace: Char?,
    val secondNonSpace: Char?,
    val lastNonSpace: Char?,
    val isBoundaryTitle: Boolean,
    val startsWithListMarker: Boolean,
    val startsWithDialogueMarker: Boolean
)

data class SoftBreakClassifierContext(
    val typicalLineLength: Int,
    val hardWrapLikely: Boolean,
    val rules: SoftBreakRuleConfig
)

object SoftBreakClassifier {

    private val strongEndPunctuation = setOf('。', '！', '？', '.', '!', '?', '…', ':', '：', ';', '；')
    private val dialogueMarkers = setOf('“', '"', '「', '『', '—')

    fun classify(
        line0: SoftBreakLineInfo,
        line1: SoftBreakLineInfo,
        context: SoftBreakClassifierContext
    ): SoftBreakDecision {
        val rules = context.rules
        val minTypical = if (context.hardWrapLikely) rules.minTypicalHardWrap else rules.minTypicalNormal
        val typical = context.typicalLineLength.coerceIn(minTypical, rules.maxTypical)
        val threshold = if (context.hardWrapLikely) rules.thresholdHardWrap else rules.thresholdNormal
        var reasons = 0
        if (line0.length == 0 || line1.length == 0) {
            reasons = reasons or SoftBreakDecisionReasons.EMPTY_LINE
            return hardBreakDecision(threshold, reasons)
        }
        if (line0.isBoundaryTitle || line1.isBoundaryTitle) {
            reasons = reasons or SoftBreakDecisionReasons.BOUNDARY_TITLE
            return hardBreakDecision(threshold, reasons)
        }
        if (line1.startsWithListMarker || line1.startsWithDialogueMarker) {
            reasons = reasons or SoftBreakDecisionReasons.LIST_OR_DIALOGUE_START
            return hardBreakDecision(threshold, reasons)
        }

        val indentIncrease = line1.leadingSpaces - line0.leadingSpaces
        if (indentIncrease >= rules.indentIncreaseHardBreakMin) {
            reasons = reasons or SoftBreakDecisionReasons.INDENT_INCREASE
            return hardBreakDecision(threshold, reasons)
        }
        if (
            !context.hardWrapLikely &&
            line1.leadingSpaces >= rules.nonHardWrapIndentChangeHardBreakMin &&
            line1.leadingSpaces != line0.leadingSpaces
        ) {
            reasons = reasons or SoftBreakDecisionReasons.INDENT_SHIFT_IN_NORMAL
            return hardBreakDecision(threshold, reasons)
        }

        var score = if (context.hardWrapLikely) rules.baseScoreHardWrap else rules.baseScoreNormal
        score += line0LengthScore(line0.length, typical, rules)
        score += line1LengthScore(line1.length, typical, rules)

        val lenGap = abs(line0.length - line1.length)
        score += if (lenGap <= (typical * rules.lenGapRatio).toInt()) {
            reasons = reasons or SoftBreakDecisionReasons.LENGTH_STABLE
            1
        } else {
            reasons = reasons or SoftBreakDecisionReasons.LENGTH_GAP
            -1
        }
        if (line0.length < rules.shortLineMinLength || line1.length < rules.shortLineMinLength) {
            reasons = reasons or SoftBreakDecisionReasons.SHORT_LINE
            score -= if (context.hardWrapLikely) {
                rules.shortLinePenaltyHardWrap
            } else {
                rules.shortLinePenaltyNormal
            }
        }

        if (line0.leadingSpaces == line1.leadingSpaces && line1.leadingSpaces > 0) {
            reasons = reasons or SoftBreakDecisionReasons.SAME_INDENT
            score += rules.sameIndentBoost
        }

        val line0StrongEnd = line0.lastNonSpace?.let(strongEndPunctuation::contains) == true
        if (line0StrongEnd) {
            reasons = reasons or SoftBreakDecisionReasons.STRONG_END_PUNCT
            val shortTail = line0.length < (typical * rules.strongEndShortTailRatio).toInt()
            score -= when {
                shortTail && context.hardWrapLikely -> rules.strongEndPenaltyShortTailHardWrap
                shortTail -> rules.strongEndPenaltyShortTailNormal
                context.hardWrapLikely -> rules.strongEndPenaltyHardWrap
                else -> rules.strongEndPenaltyNormal
            }
        }

        if (line0.startsWithDialogueMarker) {
            reasons = reasons or SoftBreakDecisionReasons.LINE0_DIALOGUE_START
            score -= rules.dialogueStartPenalty
        }

        if (
            !context.hardWrapLikely &&
            (
                line0.leadingSpaces >= rules.nonHardWrapIndentChangeHardBreakMin ||
                    line1.leadingSpaces >= rules.nonHardWrapIndentChangeHardBreakMin
                )
        ) {
            reasons = reasons or SoftBreakDecisionReasons.INDENT_PRESENT_IN_NORMAL
            score -= rules.nonHardWrapIndentPenalty
        }

        return SoftBreakDecision(
            isSoft = score >= threshold,
            score = score,
            threshold = threshold,
            reasons = reasons
        )
    }

    fun detectListMarker(firstNonSpace: Char?, secondNonSpace: Char?): Boolean {
        val first = firstNonSpace ?: return false
        if (first == '-' || first == '*' || first == '•' || first == '·') {
            return true
        }
        if (!first.isDigit()) {
            return false
        }
        return secondNonSpace == '.' || secondNonSpace == '、' || secondNonSpace == ')' || secondNonSpace == '）'
    }

    fun detectDialogueMarker(firstNonSpace: Char?): Boolean {
        return firstNonSpace != null && dialogueMarkers.contains(firstNonSpace)
    }

    private fun line0LengthScore(length: Int, typical: Int, rules: SoftBreakRuleConfig): Int {
        return when {
            length >= (typical * rules.line0HighRatio).toInt() -> 3
            length >= (typical * rules.line0MidRatio).toInt() -> 2
            length >= (typical * rules.line0LowRatio).toInt() -> 0
            else -> -3
        }
    }

    private fun line1LengthScore(length: Int, typical: Int, rules: SoftBreakRuleConfig): Int {
        return when {
            length >= (typical * rules.line1HighRatio).toInt() -> 2
            length >= (typical * rules.line1MidRatio).toInt() -> 1
            length >= (typical * rules.line1LowRatio).toInt() -> 0
            else -> -2
        }
    }

    private fun hardBreakDecision(threshold: Int, reasons: Int): SoftBreakDecision {
        return SoftBreakDecision(
            isSoft = false,
            score = threshold - 1,
            threshold = threshold,
            reasons = reasons
        )
    }
}
