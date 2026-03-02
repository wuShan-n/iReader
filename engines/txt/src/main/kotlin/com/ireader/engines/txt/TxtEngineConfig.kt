package com.ireader.engines.txt

import java.io.File

data class TxtEngineConfig(
    val cacheDir: File? = null,
    val persistPagination: Boolean = true,
    val persistOutline: Boolean = false,
    val paginationWriteEveryNewStarts: Int = 12,
    val persistLastPosition: Boolean = true,
    val lastPositionMinIntervalMs: Long = 1500L,
    val outlineAsTree: Boolean = true,

    // Storage / memory
    val inMemoryThresholdBytes: Long = 20L * 1024L * 1024L,
    val indexedWindowCacheChars: Int = 128 * 1024,

    // Pagination / rendering
    val chunkSizeChars: Int = 32 * 1024,
    val pageCacheSize: Int = 24,
    val prefetchAhead: Int = 6,
    val prefetchBehind: Int = 2,

    // Locator / relocation
    val snippetLength: Int = 48,
    val locatorSampleStrideChars: Int = 32 * 1024,
    val locatorSampleWindowChars: Int = 512,
    val locatorMaxSamples: Int = 512,
    val locatorSmallDocumentFullScanThresholdChars: Int = 600_000,
    val locatorSnippetWindowMinChars: Int = 4_096,
    val locatorSnippetWindowMaxChars: Int = 256_000,
    val locatorSnippetWindowCapChars: Int = 1_000_000,

    // Provider defaults
    val maxSearchHitsDefault: Int = 500
) {
    fun normalized(): TxtEngineConfig {
        val safeSnippetLength = snippetLength.coerceIn(24, 256)
        val safeMinWindow = locatorSnippetWindowMinChars.coerceIn(512, 128 * 1024)
        val safeMaxWindow = locatorSnippetWindowMaxChars
            .coerceAtLeast(safeMinWindow)
            .coerceIn(2 * 1024, 2 * 1024 * 1024)
        val safeCapWindow = locatorSnippetWindowCapChars
            .coerceAtLeast(safeMaxWindow)
            .coerceIn(8 * 1024, 8 * 1024 * 1024)

        return copy(
            paginationWriteEveryNewStarts = paginationWriteEveryNewStarts.coerceAtLeast(1),
            lastPositionMinIntervalMs = lastPositionMinIntervalMs.coerceAtLeast(0L),
            inMemoryThresholdBytes = inMemoryThresholdBytes.coerceAtLeast(1L * 1024L * 1024L),
            indexedWindowCacheChars = indexedWindowCacheChars.coerceIn(8 * 1024, 512 * 1024),
            chunkSizeChars = chunkSizeChars.coerceIn(2_048, 256 * 1024),
            pageCacheSize = pageCacheSize.coerceIn(4, 128),
            prefetchAhead = prefetchAhead.coerceIn(0, 8),
            prefetchBehind = prefetchBehind.coerceIn(0, 4),
            snippetLength = safeSnippetLength,
            locatorSampleStrideChars = locatorSampleStrideChars
                .coerceAtLeast(safeSnippetLength * 4)
                .coerceAtMost(2 * 1024 * 1024),
            locatorSampleWindowChars = locatorSampleWindowChars
                .coerceAtLeast(safeSnippetLength * 2)
                .coerceIn(128, 8 * 1024),
            locatorMaxSamples = locatorMaxSamples.coerceIn(16, 4_096),
            locatorSmallDocumentFullScanThresholdChars = locatorSmallDocumentFullScanThresholdChars.coerceIn(
                64 * 1024,
                8 * 1024 * 1024
            ),
            locatorSnippetWindowMinChars = safeMinWindow,
            locatorSnippetWindowMaxChars = safeMaxWindow,
            locatorSnippetWindowCapChars = safeCapWindow,
            maxSearchHitsDefault = maxSearchHitsDefault.coerceIn(1, 5_000)
        )
    }
}

