package com.ireader.engines.pdf.internal.cache

internal fun defaultPdfTileCacheBytes(): Int {
    val heapMax = Runtime.getRuntime().maxMemory().coerceAtLeast(128L * 1024 * 1024)
    val target = heapMax / 20L // 5%
    val minBytes = 16L * 1024 * 1024
    val maxBytes = 96L * 1024 * 1024
    return target.coerceIn(minBytes, maxBytes).toInt()
}
