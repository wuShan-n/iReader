package com.ireader.core.work

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkNamesTest {

    @Test
    fun `uniqueReindex should be order-independent`() {
        val first = WorkNames.uniqueReindex(listOf(9L, 2L, 5L))
        val second = WorkNames.uniqueReindex(listOf(5L, 9L, 2L))

        assertEquals(first, second)
    }

    @Test
    fun `job names should include prefixes`() {
        val jobId = "job-1"

        assertEquals("import:job-1", WorkNames.uniqueForJob(jobId))
        assertEquals("import_tag:job-1", WorkNames.tagForJob(jobId))
        assertEquals("enrich:job-1", WorkNames.uniqueEnrichForJob(jobId))
        assertEquals("enrich_tag:job-1", WorkNames.tagEnrichForJob(jobId))
    }
}
