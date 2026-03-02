@file:Suppress("LongParameterList")

package com.ireader.engines.pdf.internal.render

import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.cache.TileCache
import com.ireader.engines.pdf.internal.cache.TileInflight
import com.ireader.engines.pdf.internal.util.pageLocator
import com.ireader.engines.pdf.internal.util.progressionForPage
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.error.getOrNull
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderMetrics
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.measureTimeMillis

internal class PdfController(
    private val backend: PdfBackend,
    private val pageCount: Int,
    initialPageIndex: Int,
    initialConfig: RenderConfig.FixedPage,
    private val annotationProvider: AnnotationProvider?,
    private val engineConfig: PdfEngineConfig
) : ReaderController {

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + engineConfig.renderDispatcher)
    private val tileCache = TileCache(engineConfig.tileCacheMaxBytes)
    private val tileInflight = TileInflight(scope)
    private var activeTileProvider: PdfTileProvider? = null
    private var prefetchJob: Job? = null

    private var currentPage = initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1)
    private var currentConfig = initialConfig
    private var constraints: LayoutConstraints? = null

    private val eventsMutable = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 32)
    override val events: Flow<ReaderEvent> = eventsMutable.asSharedFlow()

    private val stateMutable = MutableStateFlow(
        RenderState(
            locator = pageLocator(currentPage, pageCount),
            progression = progressionForPage(currentPage, pageCount),
            nav = NavigationAvailability(
                canGoPrev = currentPage > 0,
                canGoNext = currentPage < pageCount - 1
            ),
            config = initialConfig
        )
    )
    override val state: StateFlow<RenderState> = stateMutable.asStateFlow()

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return mutex.withLock {
            this.constraints = constraints
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val fixed = config as? RenderConfig.FixedPage
            ?: return ReaderResult.Err(ReaderError.Internal("PDF requires RenderConfig.FixedPage"))
        return mutex.withLock {
            currentConfig = fixed
            stateMutable.value = stateMutable.value.copy(config = fixed)
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        val result = mutex.withLock { renderLocked(policy) }
        schedulePrefetch(policy.prefetchNeighbors)
        return result
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            if (currentPage < pageCount - 1) currentPage++
            renderLocked(policy)
        }.also { schedulePrefetch(policy.prefetchNeighbors) }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            if (currentPage > 0) currentPage--
            renderLocked(policy)
        }.also { schedulePrefetch(policy.prefetchNeighbors) }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        val page = locator.toPdfPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Unsupported locator for PDF: ${locator.scheme}"))
        return mutex.withLock {
            currentPage = page
            renderLocked(policy)
        }.also { schedulePrefetch(policy.prefetchNeighbors) }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        val page = ((pageCount - 1).coerceAtLeast(0) * percent.coerceIn(0.0, 1.0))
            .toInt()
            .coerceIn(0, pageCount.coerceAtLeast(1) - 1)
        return mutex.withLock {
            currentPage = page
            renderLocked(policy)
        }.also { schedulePrefetch(policy.prefetchNeighbors) }
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        if (count <= 0) return ReaderResult.Ok(Unit)
        return mutex.withLock {
            val start = (currentPage - count).coerceAtLeast(0)
            val end = (currentPage + count).coerceAtMost(pageCount - 1)
            for (page in start..end) {
                if (page == currentPage) continue
                runCatching { backend.pageSize(page) }
            }
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            runCatching { activeTileProvider?.close() }
            activeTileProvider = null
            runCatching { runBlocking { tileCache.clear() } }
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        prefetchJob?.cancel()
        runCatching { activeTileProvider?.close() }
        activeTileProvider = null
        runCatching { runBlocking { tileInflight.clear() } }
        runCatching { runBlocking { tileCache.clear() } }
        scope.cancel()
    }

    private suspend fun renderLocked(policy: RenderPolicy): ReaderResult<RenderPage> {
        val layout = constraints
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val pageSize = runCatching { backend.pageSize(currentPage) }
            .getOrElse { return ReaderResult.Err(ReaderError.Internal("Failed to read page size", it)) }

        val transform = computePageTransform(
            pageWidthPt = pageSize.widthPt,
            pageHeightPt = pageSize.heightPt,
            config = currentConfig,
            constraints = layout
        )

        val pageLocator = pageLocator(currentPage, pageCount)
        val links = if (backend.capabilities.links) {
            runCatching { backend.pageLinks(currentPage) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val decorations = annotationProvider
            ?.decorationsFor(AnnotationQuery(page = pageLocator))
            ?.getOrNull()
            ?: emptyList()

        runCatching { activeTileProvider?.close() }
        val tileProvider = PdfTileProvider(
            pageIndex = currentPage,
            renderConfig = currentConfig,
            backend = backend,
            cache = tileCache,
            inflight = tileInflight,
            config = engineConfig,
            scope = CoroutineScope(SupervisorJob() + engineConfig.renderDispatcher)
        )
        activeTileProvider = tileProvider

        var metrics: RenderMetrics? = null
        val elapsed = measureTimeMillis {
            metrics = RenderMetrics(
                renderTimeMs = 0L,
                cacheHit = policy.allowCache
            )
        }

        val page = RenderPage(
            id = PageId(
                buildString {
                    append("pdf:")
                    append(currentPage)
                    append(':')
                    append(transform.pageWidthPx)
                    append('x')
                    append(transform.pageHeightPx)
                    append(':')
                    append(currentConfig.zoom)
                    append(':')
                    append(currentConfig.rotationDegrees)
                }
            ),
            locator = pageLocator,
            content = RenderContent.Tiles(
                pageWidthPx = transform.pageWidthPx,
                pageHeightPx = transform.pageHeightPx,
                tileProvider = tileProvider
            ),
            links = links,
            decorations = decorations,
            metrics = metrics?.copy(renderTimeMs = elapsed)
        )

        updateStateLocked()
        eventsMutable.tryEmit(ReaderEvent.PageChanged(pageLocator))
        eventsMutable.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        return ReaderResult.Ok(page)
    }

    private fun updateStateLocked() {
        stateMutable.value = stateMutable.value.copy(
            locator = pageLocator(currentPage, pageCount),
            progression = progressionForPage(currentPage, pageCount),
            nav = NavigationAvailability(
                canGoPrev = currentPage > 0,
                canGoNext = currentPage < pageCount - 1
            ),
            config = currentConfig
        )
    }

    private fun schedulePrefetch(count: Int) {
        if (count <= 0) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            prefetchNeighbors(count)
        }
    }
}

