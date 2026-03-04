package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.cache.LruCache
import com.ireader.engines.txt.internal.pagination.PageSlice
import com.ireader.engines.txt.internal.pagination.TxtPaginator
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

internal class TxtPageSliceCache(
    private val paginator: TxtPaginator,
    maxPageCache: Int,
    private val maxOffsetProvider: () -> Long
) {
    private val pageCache = LruCache<Long, PageSlice>(maxPageCache)

    fun clear() {
        pageCache.clear()
    }

    fun getCached(start: Long): PageSlice? {
        return pageCache[start.normalized()]
    }

    suspend fun getOrBuild(
        start: Long,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        allowCache: Boolean
    ): PageSlice {
        val normalizedStart = start.normalized()
        if (allowCache) {
            val cached = pageCache[normalizedStart]
            if (cached != null) {
                return cached
            }
        }
        val computed = paginator.pageAt(
            startOffset = normalizedStart,
            config = config,
            constraints = constraints
        )
        pageCache[normalizedStart] = computed
        return computed
    }

    private fun Long.normalized(): Long {
        val maxOffset = maxOffsetProvider()
        return coerceIn(0L, maxOffset)
    }
}
