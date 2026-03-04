package com.ireader.engines.common.android.reflow

import com.ireader.engines.common.cache.LruCache
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

class ReflowPageSliceCache(
    private val paginator: ReflowPaginator,
    maxPageCache: Int,
    private val maxOffsetProvider: () -> Long
) {
    private val pageCache = LruCache<Long, ReflowPageSlice>(maxPageCache)

    fun clear() {
        pageCache.clear()
    }

    fun getCached(start: Long): ReflowPageSlice? {
        return pageCache[start.normalized()]
    }

    suspend fun getOrBuild(
        start: Long,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        allowCache: Boolean
    ): ReflowPageSlice {
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

