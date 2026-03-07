package com.ireader.engines.txt.internal.softbreak

import com.ireader.engines.common.android.reflow.SoftBreakClassifier
import com.ireader.engines.common.android.reflow.SoftBreakClassifierContext
import com.ireader.engines.common.android.reflow.SoftBreakDecisionReasons
import com.ireader.engines.common.android.reflow.SoftBreakLineInfo

internal object SoftBreakDecisionSupport {

    fun classifyBreak(
        line0: SoftBreakLineInfo,
        line1: SoftBreakLineInfo,
        context: SoftBreakClassifierContext,
        hardWrapLikely: Boolean
    ): BreakMapState {
        if (line0.length == 0 || line1.length == 0) {
            return BreakMapState.HARD_PARAGRAPH
        }
        if (line0.isBoundaryTitle || line1.isBoundaryTitle) {
            return BreakMapState.HARD_PARAGRAPH
        }
        if (line1.startsWithListMarker || line1.startsWithDialogueMarker) {
            return BreakMapState.PRESERVE
        }
        val decision = SoftBreakClassifier.classify(
            line0 = line0,
            line1 = line1,
            context = context
        )
        if (decision.isSoft) {
            return chooseSoftState(line0, line1)
        }
        if ((decision.reasons and SoftBreakDecisionReasons.INDENT_INCREASE) != 0 ||
            (decision.reasons and SoftBreakDecisionReasons.INDENT_SHIFT_IN_NORMAL) != 0 ||
            (decision.reasons and SoftBreakDecisionReasons.LIST_OR_DIALOGUE_START) != 0
        ) {
            return BreakMapState.PRESERVE
        }
        if (!hardWrapLikely && decision.threshold - decision.score <= 1) {
            return BreakMapState.UNKNOWN
        }
        return BreakMapState.HARD_PARAGRAPH
    }

    private fun chooseSoftState(
        line0: SoftBreakLineInfo,
        line1: SoftBreakLineInfo
    ): BreakMapState {
        val prev = line0.lastNonSpace ?: return BreakMapState.SOFT_SPACE
        val next = line1.firstNonSpace ?: return BreakMapState.SOFT_SPACE
        if (shouldJoinWithoutSpace(prev, next)) {
            return BreakMapState.SOFT_JOIN
        }
        return BreakMapState.SOFT_SPACE
    }

    private fun shouldJoinWithoutSpace(previous: Char, next: Char): Boolean {
        if (previous == '-' && next.isLetterOrDigit()) {
            return true
        }
        val previousCjk = Character.UnicodeScript.of(previous.code) in CJK_SCRIPTS
        val nextCjk = Character.UnicodeScript.of(next.code) in CJK_SCRIPTS
        if (previousCjk && nextCjk) {
            return true
        }
        return previous in NO_SPACE_AFTER || next in NO_SPACE_BEFORE
    }

    private val NO_SPACE_AFTER = setOf('（', '【', '《', '“', '"', '\'', '「', '『', '—')
    private val NO_SPACE_BEFORE = setOf('）', '】', '》', '”', '"', '\'', '、', '，', '。', '！', '？', '；', '：')
    private val CJK_SCRIPTS = setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL
    )
}
