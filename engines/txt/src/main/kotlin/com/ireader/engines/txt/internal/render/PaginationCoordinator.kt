package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.pagination.ReflowPaginationProfile
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.runtime.BreakResolver
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextLayouterFactory
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PaginationCoordinator(
    private val documentKey: String,
    private val store: Utf16TextStore,
    private val blockStore: BlockStore,
    private val breakResolver: BreakResolver,
    maxPageCache: Int,
    persistPagination: Boolean,
    files: TxtBookFiles,
    private val paginationDispatcher: CoroutineDispatcher
) {
    private val pageCache = TxtPageCache(maxPageCache)
    private val checkpoints = TxtLayoutCheckpointStore(
        enabled = persistPagination,
        paginationDir = files.paginationDir
    )
    private val pageFitter = TxtPageFitter(
        store = store,
        blockStore = blockStore,
        breakResolver = breakResolver
    )

    private var constraints: LayoutConstraints? = null
    private var config: RenderConfig.ReflowText = RenderConfig.ReflowText()
    private var layoutHash: String? = null

    fun bindInitialConfig(config: RenderConfig.ReflowText) {
        this.config = config
    }

    fun setTextLayouterFactory(factory: TextLayouterFactory) {
        pageFitter.setTextLayouterFactory(factory)
    }

    fun setLayoutConstraints(constraints: LayoutConstraints) {
        this.constraints = constraints
        reloadLayout()
    }

    fun setConfig(config: RenderConfig.ReflowText) {
        this.config = config
        reloadLayout()
    }

    fun currentLayoutHash(): String? = layoutHash

    fun startForProgress(percent: Double): Long {
        return checkpoints.startForProgress(percent)
            ?: (store.lengthCodeUnits * percent.coerceIn(0.0, 1.0)).toLong()
    }

    suspend fun pageAt(startOffset: Long, allowCache: Boolean): PageLookup {
        val constraints = requireNotNull(constraints) { "LayoutConstraints not set" }
        val normalizedStart = startOffset.coerceIn(0L, store.lengthCodeUnits)
        if (allowCache) {
            pageCache.get(normalizedStart)?.let { return PageLookup(slice = it, cacheHit = true) }
        }
        val computed = withContext(paginationDispatcher) {
            pageFitter.fitPage(
                startOffset = normalizedStart,
                config = config,
                constraints = constraints
            )
        }
        checkpoints.record(computed.startOffset)
        if (allowCache) {
            pageCache.put(computed)
        }
        return PageLookup(slice = computed, cacheHit = false)
    }

    suspend fun previousStart(fromStart: Long): Long {
        if (fromStart <= 0L) {
            return 0L
        }
        val checkpoint = checkpoints.nearestStartBefore(fromStart)
            ?: 0L
        var cursor = checkpoint
        var previous = checkpoint
        var safety = 0
        while (cursor < fromStart && safety < MAX_BACKTRACK_PAGES) {
            coroutineContext.ensureActive()
            val slice = pageAt(cursor, allowCache = true).slice
            if (slice.endOffset >= fromStart) {
                return previous.coerceAtLeast(0L)
            }
            previous = slice.startOffset
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            safety++
        }
        return previous.coerceAtLeast(0L)
    }

    suspend fun prefetchAround(currentStart: Long, count: Int) {
        if (count <= 0) {
            return
        }
        coroutineContext.ensureActive()
        val current = pageAt(currentStart, allowCache = true).slice
        var cursor = current.endOffset
        repeat(count.coerceAtMost(MAX_FORWARD_PREFETCH)) {
            coroutineContext.ensureActive()
            if (cursor >= store.lengthCodeUnits) {
                return@repeat
            }
            val next = pageAt(cursor, allowCache = true).slice
            if (next.endOffset <= cursor) {
                return@repeat
            }
            cursor = next.endOffset
        }
        if (current.startOffset > 0L) {
            val previous = previousStart(current.startOffset)
            if (previous < current.startOffset) {
                pageAt(previous, allowCache = true)
            }
        }
    }

    suspend fun warmForward(fromStart: Long, maxPages: Int) {
        if (maxPages <= 0) {
            return
        }
        var cursor = fromStart.coerceIn(0L, store.lengthCodeUnits)
        var remaining = maxPages.coerceAtMost(MAX_FORWARD_WARMUP)
        while (remaining > 0 && cursor < store.lengthCodeUnits) {
            coroutineContext.ensureActive()
            val slice = pageAt(cursor, allowCache = true).slice
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            remaining--
        }
        checkpoints.saveIfDirty()
    }

    fun invalidate() {
        pageCache.clear()
        checkpoints.saveIfDirty()
    }

    fun invalidateProjectedContent() {
        pageCache.clear()
        checkpoints.invalidateAll()
    }

    fun close() {
        checkpoints.saveIfDirty()
    }

    private fun reloadLayout() {
        val constraints = constraints
        val nextHash = if (constraints == null) {
            null
        } else {
            ReflowPaginationProfile.keyFor(
                documentKey = documentKey,
                constraints = constraints,
                config = config
            )
        }
        if (layoutHash == nextHash) {
            return
        }
        layoutHash = nextHash
        pageCache.bindLayout(nextHash)
        checkpoints.bindLayout(nextHash)
    }

    private companion object {
        private const val MAX_BACKTRACK_PAGES = 256
        private const val MAX_FORWARD_PREFETCH = 2
        private const val MAX_FORWARD_WARMUP = 6
    }

    internal data class PageLookup(
        val slice: TxtPageSlice,
        val cacheHit: Boolean
    )
}
