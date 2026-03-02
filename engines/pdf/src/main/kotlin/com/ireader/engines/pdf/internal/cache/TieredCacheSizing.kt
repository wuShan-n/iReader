package com.ireader.engines.pdf.internal.cache

internal data class TieredCacheBudget(
    val draftBytes: Int,
    val finalBytes: Int
)

internal fun defaultPdfTieredCacheBudget(
    totalBytes: Int = defaultPdfTileCacheBytes(),
    finalRatio: Float = 0.75f
): TieredCacheBudget {
    val ratio = finalRatio.coerceIn(0.5f, 0.9f)
    val finalBudget = (totalBytes * ratio).toInt().coerceAtLeast(8 * 1024 * 1024)
    val draftBudget = (totalBytes - finalBudget).coerceAtLeast(4 * 1024 * 1024)
    return TieredCacheBudget(
        draftBytes = draftBudget,
        finalBytes = finalBudget
    )
}
