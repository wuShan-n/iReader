package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.SoftBreakClassifier
import com.ireader.engines.common.android.reflow.SoftBreakClassifierContext
import com.ireader.engines.common.android.reflow.SoftBreakDecisionReasons
import com.ireader.engines.common.android.reflow.SoftBreakLineInfo
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftBreakClassifierDecisionTest {

    @Test
    fun `boundary title should keep hard break and expose reason`() {
        val context = SoftBreakClassifierContext(
            typicalLineLength = 42,
            hardWrapLikely = true,
            rules = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED)
        )
        val decision = SoftBreakClassifier.classify(
            line0 = line(length = 38, lastNonSpace = '。'),
            line1 = line(length = 10, isBoundaryTitle = true, firstNonSpace = '第', secondNonSpace = '十'),
            context = context
        )

        assertFalse(decision.isSoft)
        assertTrue((decision.reasons and SoftBreakDecisionReasons.BOUNDARY_TITLE) != 0)
        assertTrue(SoftBreakDecisionReasons.describe(decision.reasons).contains("boundary_title"))
    }

    @Test
    fun `aggressive profile should merge more borderline breaks than strict profile`() {
        val line0 = line(length = 38, leadingSpaces = 0)
        val line1 = line(length = 35, leadingSpaces = 0)

        val strict = SoftBreakClassifier.classify(
            line0 = line0,
            line1 = line1,
            context = SoftBreakClassifierContext(
                typicalLineLength = 40,
                hardWrapLikely = false,
                rules = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.STRICT)
            )
        )
        val aggressive = SoftBreakClassifier.classify(
            line0 = line0,
            line1 = line1,
            context = SoftBreakClassifierContext(
                typicalLineLength = 40,
                hardWrapLikely = false,
                rules = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.AGGRESSIVE)
            )
        )

        assertFalse(strict.isSoft)
        assertTrue(aggressive.isSoft)
        assertTrue(aggressive.threshold < strict.threshold)
    }

    @Test
    fun `strong end punctuation should be visible in reasons`() {
        val context = SoftBreakClassifierContext(
            typicalLineLength = 40,
            hardWrapLikely = false,
            rules = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED)
        )
        val decision = SoftBreakClassifier.classify(
            line0 = line(length = 22, lastNonSpace = '。'),
            line1 = line(length = 30, lastNonSpace = '文'),
            context = context
        )

        assertTrue((decision.reasons and SoftBreakDecisionReasons.STRONG_END_PUNCT) != 0)
    }

    private fun line(
        length: Int,
        leadingSpaces: Int = 0,
        firstNonSpace: Char? = '这',
        secondNonSpace: Char? = '是',
        lastNonSpace: Char? = '文',
        isBoundaryTitle: Boolean = false,
        startsWithListMarker: Boolean = false,
        startsWithDialogueMarker: Boolean = false
    ): SoftBreakLineInfo {
        return SoftBreakLineInfo(
            length = length,
            leadingSpaces = leadingSpaces,
            firstNonSpace = firstNonSpace,
            secondNonSpace = secondNonSpace,
            lastNonSpace = lastNonSpace,
            isBoundaryTitle = isBoundaryTitle,
            startsWithListMarker = startsWithListMarker,
            startsWithDialogueMarker = startsWithDialogueMarker
        )
    }
}
