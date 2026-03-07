package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.cache.LruCache

internal class TxtPageCache(maxPages: Int) {
    private val pages = LruCache<Long, TxtPageSlice>(maxPages.coerceAtLeast(4))
    private var layoutHash: String? = null

    fun bindLayout(layoutHash: String?) {
        if (this.layoutHash == layoutHash) {
            return
        }
        this.layoutHash = layoutHash
        clear()
    }

    fun get(startOffset: Long): TxtPageSlice? = pages[startOffset]

    fun put(slice: TxtPageSlice) {
        pages[slice.startOffset] = slice
    }

    fun clear() {
        pages.clear()
    }
}
