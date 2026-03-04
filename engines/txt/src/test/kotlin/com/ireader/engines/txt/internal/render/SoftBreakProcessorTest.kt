package com.ireader.engines.txt.internal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `line break after strong punctuation should remain paragraph break`() {
        val output = SoftBreakProcessor.process(
            rawText = "段落结束。\n下一段内容",
            hardWrapLikely = true,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        )

        assertEquals("段落结束。\n下一段内容", output.toString())
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
    fun `should normalize soft breaks even when hardWrapLikely is false`() {
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

        assertFalse(output.contains('\n'))
    }
}
