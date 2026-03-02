package com.ireader.engines.pdf.internal.cache

import com.ireader.engines.pdf.internal.backend.PdfPageSize
import java.util.LinkedHashMap

internal class PageSizeCache(
    private val maxEntries: Int
) {
    private val map = object : LinkedHashMap<Int, PdfPageSize>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PdfPageSize>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(pageIndex: Int): PdfPageSize? = map[pageIndex]

    @Synchronized
    fun put(pageIndex: Int, size: PdfPageSize) {
        map[pageIndex] = size
    }
}
