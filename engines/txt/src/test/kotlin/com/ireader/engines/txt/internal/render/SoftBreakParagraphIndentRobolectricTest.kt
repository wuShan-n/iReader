package com.ireader.engines.txt.internal.render

import android.text.Spanned
import android.text.style.LeadingMarginSpan
import com.ireader.engines.common.android.reflow.SoftBreakProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoftBreakParagraphIndentRobolectricTest {

    @Test
    fun `paragraph with full-width manual indent should not receive extra leading margin span`() {
        val output = SoftBreakProcessor.process(
            rawText = "　　这是第一段正文并且已经带有全角空格缩进。\n　　这是第二段正文同样带有全角空格缩进。",
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 24,
            startsAtParagraphBoundary = true
        )

        val spanned = output as Spanned
        val leadingMargins = spanned.getSpans(0, spanned.length, LeadingMarginSpan::class.java)
        assertEquals(0, leadingMargins.size)
    }
}
