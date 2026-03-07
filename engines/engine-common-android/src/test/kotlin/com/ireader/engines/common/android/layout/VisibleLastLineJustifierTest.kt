package com.ireader.engines.common.android.layout

import android.text.Spanned
import android.text.style.ReplacementSpan
import com.ireader.core.common.android.typography.appendHiddenTrailingLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisibleLastLineJustifierTest {

    @Test
    fun `appendHiddenTrailingLine should preserve content and append one hidden span`() {
        val baseText = "这是正文内容"
        val adjusted = appendHiddenTrailingLine(baseText, lineWidthPx = 320)

        assertTrue(adjusted is Spanned)
        assertEquals(baseText, adjusted.subSequence(0, baseText.length).toString())
        assertEquals(baseText.length + 2, adjusted.length)

        val spanned = adjusted as Spanned
        val spans = spanned.getSpans(baseText.length, adjusted.length, ReplacementSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(baseText.length, spanned.getSpanStart(spans.first()))
        assertEquals(baseText.length + 1, spanned.getSpanEnd(spans.first()))
    }
}
