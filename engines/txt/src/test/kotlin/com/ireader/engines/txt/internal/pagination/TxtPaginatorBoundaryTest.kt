package com.ireader.engines.txt.internal.pagination

import com.ireader.engines.common.android.reflow.ReflowPaginator
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtPaginatorBoundaryTest {

    @Test
    fun `adjustMeasuredEndForParagraphTail should rewind short paragraph tail`() {
        val raw = buildString {
            append("First paragraph is intentionally long enough to trigger boundary rewind. ")
            append("First paragraph is intentionally long enough to trigger boundary rewind. ")
            append('\n')
            append("tail")
            append("123456")
        }
        val measuredEnd = raw.length - 1

        val adjusted = ReflowPaginator.adjustMeasuredEndForParagraphTail(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length
        )

        assertEquals(raw.indexOf('\n') + 1, adjusted)
    }

    @Test
    fun `adjustMeasuredEndForParagraphTail should keep end when there is no line break`() {
        val raw = "This is a single paragraph without line breaks and should not rewind."
        val measuredEnd = raw.length - 1

        val adjusted = ReflowPaginator.adjustMeasuredEndForParagraphTail(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length
        )

        assertEquals(measuredEnd, adjusted)
    }

    @Test
    fun `adjustMeasuredEndForParagraphTail should rewind short sentence tail`() {
        val raw = "这是第一句内容足够长用于分页边界测试。这是第二句尾巴很短"
        val measuredEnd = raw.length - 1

        val adjusted = ReflowPaginator.adjustMeasuredEndForParagraphTail(
            raw = raw,
            measuredEnd = measuredEnd,
            rawLength = raw.length
        )

        assertEquals(raw.indexOf('。') + 1, adjusted)
    }
}
