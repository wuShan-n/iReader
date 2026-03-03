package com.ireader.feature.reader.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultAccumulatorTest {

    @Test
    fun `items should flush in batches`() {
        val flushed = mutableListOf<List<SearchResultItem>>()
        val acc = SearchResultAccumulator(
            flushBatchSize = 2,
            flushIntervalMs = Long.MAX_VALUE,
            nowMs = { 0L },
            onFlush = { flushed += it }
        )

        acc.add(SearchResultItem("a", "1", "l1"))
        acc.add(SearchResultItem("b", "2", "l2"))
        acc.add(SearchResultItem("c", "3", "l3"))
        acc.flush()

        assertEquals(2, flushed.size)
        assertEquals(2, flushed[0].size)
        assertEquals(1, flushed[1].size)
    }
}

