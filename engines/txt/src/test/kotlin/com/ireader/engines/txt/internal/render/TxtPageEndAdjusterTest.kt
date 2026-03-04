package com.ireader.engines.txt.internal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtPageEndAdjusterTest {

    private val adjuster = TxtPageEndAdjuster()

    @Test
    fun `adjust should keep measured end when page has no chapter boundary`() {
        val raw = "这是一段普通正文内容\n没有章节标题继续阅读。"
        val measuredEnd = raw.length - 2

        val adjusted = adjuster.adjust(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length,
            pageStartOffset = 0L
        )

        assertEquals(measuredEnd, adjusted)
    }

    @Test
    fun `adjust should move chapter title to next page`() {
        val raw = "上一段正文仍在继续。\n第十二章 新篇章开始\n章节正文第一行"
        val measuredEnd = raw.indexOf("章节正文")
        val expected = raw.indexOf("第十二章")

        val adjusted = adjuster.adjust(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length,
            pageStartOffset = 0L
        )

        assertEquals(expected, adjusted)
    }

    @Test
    fun `adjust should not cut when chapter title is already at page start`() {
        val raw = "第十二章 新篇章开始\n章节正文第一行继续"
        val measuredEnd = raw.length - 1

        val adjusted = adjuster.adjust(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length,
            pageStartOffset = 1024L
        )

        assertEquals(measuredEnd, adjusted)
    }

    @Test
    fun `adjust should treat directory as boundary marker`() {
        val raw = "上一段正文\n\n目录\n第一章 起始"
        val measuredEnd = raw.indexOf("第一章")
        val expected = raw.indexOf("目录")

        val adjusted = adjuster.adjust(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length,
            pageStartOffset = 0L
        )

        assertEquals(expected, adjusted)
    }

    @Test
    fun `adjust should not rewind weak chapter marker without strong prelude`() {
        val raw = "上一段正文没有句号\n目录\n下一段内容"
        val measuredEnd = raw.indexOf("下一段内容")

        val adjusted = adjuster.adjust(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length,
            pageStartOffset = 0L
        )

        assertEquals(measuredEnd, adjusted)
    }

    @Test
    fun `adjust should pick nearest boundary when multiple chapter markers appear before measured end`() {
        val raw = buildString {
            append("上一章收束。\n")
            append("第10章 边界A\n")
            repeat(12) { index ->
                append("中间正文第${index + 1}行继续推进剧情。\n")
            }
            append("第11章 边界B\n")
            append("新章节正文开始。")
        }
        val measuredEnd = raw.indexOf("新章节正文")
        val firstBoundary = raw.indexOf("第10章")
        val secondBoundary = raw.indexOf("第11章")

        val adjusted = adjuster.adjust(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length,
            pageStartOffset = 0L
        )

        assertEquals(secondBoundary, adjusted)
        assertTrue("expected rewind to be positive", measuredEnd - adjusted > 0)
        assertTrue("expected nearest boundary to be selected", adjusted > firstBoundary)
    }

    @Test
    fun `adjust should ignore far boundary when rewind exceeds cap`() {
        val raw = buildString {
            append("上一章收束。\n")
            append("第10章 边界A\n")
            repeat(320) { index ->
                append("这是一段较长正文用于制造超大回退距离${index + 1}\n")
            }
        }
        val measuredEnd = raw.length - 1

        val adjusted = adjuster.adjust(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length,
            pageStartOffset = 0L
        )

        assertEquals(measuredEnd, adjusted)
    }
}
