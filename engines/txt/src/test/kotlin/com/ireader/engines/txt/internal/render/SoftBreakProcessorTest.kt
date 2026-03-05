package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.SoftBreakProcessor
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftBreakProcessorTest {

    @Test
    fun `hard wrap should collapse soft line break into a space`() {
        val output = SoftBreakProcessor.process(
            rawText = "这是第一行\n这是第二行",
            hardWrapLikely = true,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("这是第一行 这是第二行", output.toString())
    }

    @Test
    fun `line break after strong punctuation should remain paragraph break when hard wrap is not likely`() {
        val output = SoftBreakProcessor.process(
            rawText = "段落结束。\n下一段内容",
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("段落结束。\n下一段内容", output.toString())
    }

    @Test
    fun `line break after strong punctuation should collapse when hard wrap is likely`() {
        val output = SoftBreakProcessor.process(
            rawText = "段落结束。\n下一段内容",
            hardWrapLikely = true,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("段落结束。 下一段内容", output.toString())
    }

    @Test
    fun `line break before chapter marker should remain paragraph break`() {
        val output = SoftBreakProcessor.process(
            rawText = "上一段内容\n第十二章 开始",
            hardWrapLikely = true,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("上一段内容\n第十二章 开始", output.toString())
    }

    @Test
    fun `line break before directory marker should remain paragraph break`() {
        val output = SoftBreakProcessor.process(
            rawText = "上一段内容\n目录",
            hardWrapLikely = true,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("上一段内容\n目录", output.toString())
    }

    @Test
    fun `consecutive newlines should be preserved`() {
        val output = SoftBreakProcessor.process(
            rawText = "第一段\n\n第二段",
            hardWrapLikely = true,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("第一段\n\n第二段", output.toString())
    }

    @Test
    fun `line break followed by whitespace should be preserved`() {
        val output = SoftBreakProcessor.process(
            rawText = "第一行\n 第二行",
            hardWrapLikely = true,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("第一行\n 第二行", output.toString())
    }

    @Test
    fun `crlf should be normalized before processing`() {
        val output = SoftBreakProcessor.process(
            rawText = "第一行\r\n第二行",
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("第一行\n第二行", output.toString())
    }

    @Test
    fun `should keep soft breaks when hardWrapLikely is false`() {
        val raw = buildString {
            repeat(30) { index ->
                append("这是第")
                append(index + 1)
                append("行没有句号没有终止符并且继续叙述内容用于验证自动软换行识别")
                if (index < 29) {
                    append('\n')
                }
            }
        }

        val output = SoftBreakProcessor.process(
            rawText = raw,
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals(raw, output.toString())
    }

    @Test
    fun `line width profile text should stay unchanged when hardWrapLikely is false`() {
        val raw = buildString {
            repeat(36) { index ->
                when (index % 3) {
                    0 -> append("这是第${index + 1}行用于稳定宽度兜底触发并保持继续叙述内容")
                    1 -> append("这是第${index + 1}行以句号结尾用于降低软换行比例。")
                    else -> append("这是第${index + 1}行仍旧保持稳定长度并继续叙述用于测试")
                }
                if (index < 35) {
                    append('\n')
                }
            }
        }

        val output = SoftBreakProcessor.process(
            rawText = raw,
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        ).toString()

        assertEquals(raw, output)
    }

    @Test
    fun `hardWrapLikely false should keep chapter and blank line boundaries verbatim`() {
        val raw = buildString {
            repeat(12) { index ->
                append("这是一段连续叙述用于触发自动软换行并验证边界保留能力")
                append(index + 1)
                append('\n')
            }
            append("上一段收束\n")
            append("第十二章 新篇章开始\n")
            append('\n')
            append("章节正文第一行继续描述故事发展")
        }

        val output = SoftBreakProcessor.process(
            rawText = raw,
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        ).toString()

        assertEquals(raw, output)
    }

    @Test
    fun `list-like text should keep line breaks`() {
        val raw = buildString {
            repeat(25) { index ->
                append("- 清单条目")
                append(index + 1)
                append(" 需要逐行展示避免合并")
                if (index < 24) {
                    append('\n')
                }
            }
        }

        val output = SoftBreakProcessor.process(
            rawText = raw,
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        ).toString()

        assertEquals(raw, output)
    }

    @Test
    fun `aggressive profile may merge same-indented lines when previous line ends with quote`() {
        val raw = buildString {
            append("　　我慢慢说完了这个临时想到的行动计划并让她先别插话”\n")
            append("　　她没有打断我只是继续看着我等待我把后续想法全部讲完")
        }

        val output = SoftBreakProcessor.process(
            rawText = raw,
            hardWrapLikely = false,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true,
            ruleConfig = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.AGGRESSIVE)
        ).toString()

        assertTrue("expected newline to be merged into space", output.contains("” 　　她"))
        assertEquals(' ', output[raw.indexOf('\n')])
    }

}
