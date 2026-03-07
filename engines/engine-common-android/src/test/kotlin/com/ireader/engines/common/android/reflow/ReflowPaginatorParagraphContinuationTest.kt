package com.ireader.engines.common.android.reflow

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReflowPaginatorParagraphContinuationTest {

    @Test
    fun `reflowPageContinuesParagraph should stop at document end`() {
        val text = "短文。"

        assertFalse(
            reflowPageContinuesParagraph(
                sourceLength = text.length.toLong(),
                endOffset = text.length.toLong(),
                raw = text,
                display = text,
                measuredEnd = text.length
            )
        )
    }

    @Test
    fun `reflowPageContinuesParagraph should continue for mid paragraph end`() {
        val text = (
            "This paragraph keeps flowing through the page without explicit paragraph breaks, " +
                "so pagination should treat the visible end as a continuation. "
            ).repeat(48)
        val measuredEnd = 512

        assertTrue(
            reflowPageContinuesParagraph(
                sourceLength = text.length.toLong(),
                endOffset = measuredEnd.toLong(),
                raw = text,
                display = text,
                measuredEnd = measuredEnd
            )
        )
    }

    @Test
    fun `pageAt should stop continuation when page ends after hard newline`() = runBlocking {
        val firstParagraph = "这一段在页尾之前明确结束，用来验证硬换行不会被当成续段。"
        val secondParagraph = "第二段继续补足内容，确保分页测量会越过上一段的换行位置。".repeat(24)
        val text = "$firstParagraph\n$secondParagraph"
        val paginator = ReflowPaginator(
            source = StringTextSource(text),
            hardWrapLikely = false,
            pageEndAdjuster = forcePageEnd(firstParagraph.length + 1)
        )

        val page = paginator.pageAt(
            startOffset = 0L,
            config = defaultConfig(),
            constraints = compactConstraints()
        )

        assertFalse(page.continuesParagraph)
    }

    @Test
    fun `reflowPageContinuesParagraph should continue after soft newline`() {
        val line1 =
            "This wrapped line is folded into a space by the soft-break index to keep the paragraph continuous."
        val line2 =
            "The following content keeps extending the same paragraph well beyond the current page boundary. "
                .repeat(28)
        val raw = "$line1\n$line2"
        val display = "$line1 $line2"
        val measuredEnd = line1.length + 1

        assertTrue(
            reflowPageContinuesParagraph(
                sourceLength = raw.length.toLong(),
                endOffset = measuredEnd.toLong(),
                raw = raw,
                display = display,
                measuredEnd = measuredEnd
            )
        )
    }

    private fun forcePageEnd(targetEnd: Int): ReflowPageEndAdjuster {
        return ReflowPageEndAdjuster { _, measuredEnd, _, _ ->
            targetEnd.coerceAtMost(measuredEnd)
        }
    }

    private fun defaultConfig(): RenderConfig.ReflowText {
        return RenderConfig.ReflowText(
            fontSizeSp = 18f,
            lineHeightMult = 1.5f,
            paragraphSpacingDp = 0f,
            pagePaddingDp = 0f,
            includeFontPadding = false
        )
    }

    private fun compactConstraints(): LayoutConstraints {
        return LayoutConstraints(
            viewportWidthPx = 360,
            viewportHeightPx = 180,
            density = 1f,
            fontScale = 1f
        )
    }

    private fun tallConstraints(): LayoutConstraints {
        return LayoutConstraints(
            viewportWidthPx = 360,
            viewportHeightPx = 1600,
            density = 1f,
            fontScale = 1f
        )
    }

    private class StringTextSource(
        private val text: String
    ) : ReflowTextSource {

        override val lengthChars: Long = text.length.toLong()

        override fun readString(start: Long, count: Int): String {
            val safeStart = start.toInt().coerceIn(0, text.length)
            val safeEnd = (safeStart + count).coerceIn(safeStart, text.length)
            return text.substring(safeStart, safeEnd)
        }
    }
}
