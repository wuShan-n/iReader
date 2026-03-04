package com.ireader.core.work.index

import androidx.work.Data
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ReindexWorkerInputTest {

    @Test
    fun `data and bookIds should round trip`() {
        val ids = longArrayOf(1L, 3L, 7L)

        val data = ReindexWorkerInput.data(ids)
        val decoded = ReindexWorkerInput.bookIds(data)

        assertArrayEquals(ids, decoded)
    }

    @Test
    fun `bookIds should return empty when key missing`() {
        val decoded = ReindexWorkerInput.bookIds(Data.EMPTY)

        assertArrayEquals(longArrayOf(), decoded)
    }
}
