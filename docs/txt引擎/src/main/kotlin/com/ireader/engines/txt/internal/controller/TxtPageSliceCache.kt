package com.ireader.engines.txt.internal.controller

import com.ireader.engines.txt.internal.paging.PageSlice
import com.ireader.engines.txt.internal.paging.PaginationCache
import com.ireader.engines.txt.internal.paging.RenderKey

internal class TxtPageSliceCache(
    maxPages: Int
) {
    private val cache = PaginationCache(maxPages = maxPages.coerceAtLeast(4))

    fun clear() {
        cache.clear()
    }

    fun get(startChar: Int, renderKey: RenderKey?): PageSlice? {
        return cache.get(startChar, namespace(renderKey))
    }

    fun put(slice: PageSlice, renderKey: RenderKey?) {
        cache.put(slice, namespace(renderKey))
    }

    private fun namespace(renderKey: RenderKey?): String {
        return renderKey?.toString() ?: "pending"
    }
}
