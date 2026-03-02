package com.ireader.engines.txt

import org.junit.Assert.assertTrue
import org.junit.Test

class TxtEngineConfigTest {

    @Test
    fun normalized_coerces_invalid_values() {
        val normalized = TxtEngineConfig(
            paginationWriteEveryNewStarts = 0,
            lastPositionMinIntervalMs = -1L,
            inMemoryThresholdBytes = 0L,
            indexedWindowCacheChars = 16,
            chunkSizeChars = 1,
            pageCacheSize = 0,
            prefetchAhead = 99,
            prefetchBehind = -2,
            snippetLength = 8,
            locatorSampleStrideChars = 4,
            locatorSampleWindowChars = 8,
            locatorMaxSamples = 0,
            locatorSmallDocumentFullScanThresholdChars = 32,
            locatorSnippetWindowMinChars = 32,
            locatorSnippetWindowMaxChars = 64,
            locatorSnippetWindowCapChars = 128,
            maxSearchHitsDefault = 0
        ).normalized()

        assertTrue(normalized.paginationWriteEveryNewStarts >= 1)
        assertTrue(normalized.lastPositionMinIntervalMs >= 0L)
        assertTrue(normalized.inMemoryThresholdBytes >= 1L * 1024L * 1024L)
        assertTrue(normalized.indexedWindowCacheChars >= 8 * 1024)
        assertTrue(normalized.chunkSizeChars >= 2_048)
        assertTrue(normalized.pageCacheSize >= 4)
        assertTrue(normalized.prefetchAhead <= 8)
        assertTrue(normalized.prefetchBehind >= 0)
        assertTrue(normalized.snippetLength >= 24)
        assertTrue(normalized.locatorSampleWindowChars >= normalized.snippetLength * 2)
        assertTrue(normalized.locatorSnippetWindowMaxChars >= normalized.locatorSnippetWindowMinChars)
        assertTrue(normalized.locatorSnippetWindowCapChars >= normalized.locatorSnippetWindowMaxChars)
        assertTrue(normalized.maxSearchHitsDefault >= 1)
    }
}

