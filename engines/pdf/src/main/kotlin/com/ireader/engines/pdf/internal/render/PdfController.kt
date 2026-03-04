@file:Suppress("LongParameterList")

package com.ireader.engines.pdf.internal.render

import android.graphics.Bitmap
import com.ireader.engines.common.android.controller.BaseCoroutineReaderController
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
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderMetrics
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.TileRequest
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.math.roundToInt

internal class PdfController(
    private val backend: PdfBackend,
    private val pageCount: Int,
    initialPageIndex: Int,
    initialConfig: RenderConfig.FixedPage,
    private val annotationProvider: AnnotationProvider?,
    private val engineConfig: PdfEngineConfig
) : BaseCoroutineReaderController(
    initialState = RenderState(
        locator = pageLocator(
            initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1),
            pageCount
        ),
        progression = progressionForPage(
            initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1),
            pageCount
        ),
        nav = NavigationAvailability(
            canGoPrev = initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1) > 0,
            canGoNext = initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1) < pageCount - 1
        ),
        config = initialConfig
    ),
    dispatcher = engineConfig.renderDispatcher
) {
    private val tileCache = TileCache(engineConfig.tileCacheMaxBytes)
    private val tileInflight = TileInflight(scope)
    private var activeTileProvider: PdfTileProvider? = null
    private var prefetchJob: Job? = null

    private var currentPage = initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1)
    private var currentConfig = initialConfig
    private var constraints: LayoutConstraints? = null

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
            val sanitized = fixed.sanitized()
            currentConfig = sanitized
            stateMutable.value = stateMutable.value.copy(config = sanitized)
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
            val layout = constraints ?: return@withLock ReaderResult.Ok(Unit)
            val start = (currentPage - count).coerceAtLeast(0)
            val end = (currentPage + count).coerceAtMost(pageCount - 1)
            for (page in start..end) {
                if (page == currentPage) continue
                runCatching { prewarmNeighborPageLocked(page, layout) }
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

    override fun onClose() {
        prefetchJob?.cancel()
        runCatching { activeTileProvider?.close() }
        activeTileProvider = null
        runCatching { runBlocking { tileInflight.clear() } }
        runCatching { runBlocking { tileCache.clear() } }
    }

    private suspend fun renderLocked(policy: RenderPolicy): ReaderResult<RenderPage> {
        val layout = constraints
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val startTimeNs = System.nanoTime()

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
            runCatching { backend.pageLinks(currentPage) }
                .getOrDefault(emptyList())
                .rotateBy(currentConfig.rotationDegrees)
        } else {
            emptyList()
        }
        val decorations = annotationProvider
            ?.decorationsFor(AnnotationQuery(page = pageLocator))
            ?.getOrNull()
            ?: emptyList()

        runCatching { activeTileProvider?.close() }
        activeTileProvider = null

        val content = runCatching {
            if (backend.capabilities.preciseRegionRendering) {
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
                RenderContent.Tiles(
                    pageWidthPx = transform.pageWidthPx,
                    pageHeightPx = transform.pageHeightPx,
                    baseTileSizePx = engineConfig.tileBaseSizePx,
                    tileProvider = tileProvider
                )
            } else {
                RenderContent.BitmapPage(
                    bitmap = renderSingleBitmapPage(
                        pageIndex = currentPage,
                        widthPx = transform.pageWidthPx,
                        heightPx = transform.pageHeightPx,
                        quality = policy.quality
                    )
                )
            }
        }.getOrElse {
            return ReaderResult.Err(ReaderError.Internal("Failed to render PDF content", it))
        }
        val elapsedMs = ((System.nanoTime() - startTimeNs) / 1_000_000L).coerceAtLeast(0L)

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
            content = content,
            links = links,
            decorations = decorations,
            metrics = RenderMetrics(
                renderTimeMs = elapsedMs,
                cacheHit = false
            )
        )

        updateStateLocked()
        eventsMutable.tryEmit(ReaderEvent.PageChanged(pageLocator))
        eventsMutable.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        return ReaderResult.Ok(page)
    }

    private suspend fun renderSingleBitmapPage(
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
        quality: RenderPolicy.Quality
    ): Bitmap {
        val safeWidth = widthPx.coerceAtLeast(1)
        val safeHeight = heightPx.coerceAtLeast(1)
        val maxEdge = 4096
        val maxSide = maxOf(safeWidth, safeHeight)
        val scale = if (maxSide <= maxEdge) 1f else maxEdge.toFloat() / maxSide.toFloat()
        val outputWidth = (safeWidth * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (safeHeight * scale).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        backend.renderRegion(
            pageIndex = pageIndex,
            bitmap = bitmap,
            regionLeftPx = 0,
            regionTopPx = 0,
            regionWidthPx = outputWidth,
            regionHeightPx = outputHeight,
            quality = quality
        )
        return bitmap
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

    private suspend fun prewarmNeighborPageLocked(pageIndex: Int, layout: LayoutConstraints) {
        val pageSize = backend.pageSize(pageIndex)
        if (!backend.capabilities.preciseRegionRendering) {
            return
        }
        val transform = computePageTransform(
            pageWidthPt = pageSize.widthPt,
            pageHeightPt = pageSize.heightPt,
            config = currentConfig,
            constraints = layout
        )
        val tileProvider = PdfTileProvider(
            pageIndex = pageIndex,
            renderConfig = currentConfig,
            backend = backend,
            cache = tileCache,
            inflight = tileInflight,
            config = engineConfig,
            scope = CoroutineScope(SupervisorJob() + engineConfig.renderDispatcher)
        )
        try {
            val tileSize = engineConfig.tileBaseSizePx.coerceAtLeast(128)
            tileProvider.renderTile(
                TileRequest(
                    leftPx = 0,
                    topPx = 0,
                    widthPx = min(tileSize, transform.pageWidthPx).coerceAtLeast(1),
                    heightPx = min(tileSize, transform.pageHeightPx).coerceAtLeast(1),
                    scale = 1f,
                    quality = RenderPolicy.Quality.DRAFT
                )
            )
        } finally {
            tileProvider.close()
        }
    }

    private fun List<DocumentLink>.rotateBy(rotationDegrees: Int): List<DocumentLink> {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return this
        return map { link ->
            val bounds = link.bounds
                ?.map { rect -> rotateRect(rect, normalized) }
                ?.takeIf { it.isNotEmpty() }
            link.copy(bounds = bounds)
        }
    }

    private fun rotateRect(rect: NormalizedRect, rotationDegrees: Int): NormalizedRect {
        val points = listOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.right to rect.bottom,
            rect.left to rect.bottom
        ).map { (x, y) ->
            when (rotationDegrees) {
                90 -> (1f - y) to x
                180 -> (1f - x) to (1f - y)
                270 -> y to (1f - x)
                else -> x to y
            }
        }

        val minX = points.minOf { it.first }.coerceIn(0f, 1f)
        val maxX = points.maxOf { it.first }.coerceIn(0f, 1f)
        val minY = points.minOf { it.second }.coerceIn(0f, 1f)
        val maxY = points.maxOf { it.second }.coerceIn(0f, 1f)
        return NormalizedRect(
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY
        )
    }
}
