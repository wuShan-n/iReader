package com.ireader.engines.pdf.internal.render

import android.os.SystemClock
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.cache.TileCache
import com.ireader.engines.pdf.internal.cache.TileInflight
import com.ireader.engines.pdf.internal.cache.TieredTileCache
import com.ireader.engines.pdf.internal.cache.defaultPdfTieredCacheBudget
import com.ireader.engines.pdf.internal.provider.PdfHighlightStore
import com.ireader.engines.pdf.internal.util.pdfPageLocator
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.engines.pdf.internal.util.toReaderError
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
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
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.TileRequest
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.Progression
import com.ireader.reader.model.annotation.AnnotationStyle
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal class PdfController(
    private val documentId: DocumentId,
    private val backend: PdfBackend,
    startPageIndex: Int,
    initialConfig: RenderConfig.FixedPage,
    private val ioDispatcher: CoroutineDispatcher,
    private val linksResolver: suspend (pageIndex: Int, rotationDegrees: Int) -> List<PdfLinkRaw> =
        { _, _ -> emptyList() },
    private val highlightStore: PdfHighlightStore? = null
) : ReaderController {

    private val mutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var prefetchJob: Job? = null
    private var finalPreheatJob: Job? = null

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 64)
    override val events: Flow<ReaderEvent> = _events.asSharedFlow()

    private var constraints: LayoutConstraints? = null
    private var config: RenderConfig.FixedPage = initialConfig
    private val pageCount: Int = backend.pageCount
    private var pageIndex: Int = clampPage(startPageIndex)

    private val tierBudget = defaultPdfTieredCacheBudget()
    private val cache = TieredTileCache(
        draftCache = TileCache(maxBytes = tierBudget.draftBytes),
        finalCache = TileCache(maxBytes = tierBudget.finalBytes)
    )
    private val inflight = TileInflight()
    private val generation = AtomicLong(0L)
    private var currentProvider: PdfTileProvider? = null
    private val finalPreheatDebounceMs: Long = 120L

    private val _state = MutableStateFlow(buildState())
    override val state: StateFlow<RenderState> = _state

    init {
        highlightStore?.let { store ->
            scope.launch {
                store.updates.collect { updatedPage ->
                    val currentPage = mutex.withLock { pageIndex }
                    if (updatedPage == PdfHighlightStore.ALL_PAGES || updatedPage == currentPage) {
                        _events.tryEmit(ReaderEvent.PageChanged(pdfPageLocator(currentPage)))
                    }
                }
            }
        }
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> =
        withContext(ioDispatcher) {
            mutex.withLock {
                this@PdfController.constraints = constraints
                bumpGeneration()
                cache.evictAll()
                _state.value = buildState()
                ReaderResult.Ok(Unit)
            }
        }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            val fixed = config as? RenderConfig.FixedPage
                ?: return@withLock ReaderResult.Err(
                    ReaderError.Internal("PDF only supports RenderConfig.FixedPage")
                )

            this@PdfController.config = fixed
            bumpGeneration()
            cache.evictAll()
            _state.value = buildState()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> = withContext(ioDispatcher) {
        mutex.withLock {
            renderLocked(policy)
        }
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> = withContext(ioDispatcher) {
        mutex.withLock {
            if (pageIndex < pageCount - 1) {
                pageIndex += 1
                bumpGeneration()
                _events.tryEmit(ReaderEvent.PageChanged(pdfPageLocator(pageIndex)))
            }
            renderLocked(policy)
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> = withContext(ioDispatcher) {
        mutex.withLock {
            if (pageIndex > 0) {
                pageIndex -= 1
                bumpGeneration()
                _events.tryEmit(ReaderEvent.PageChanged(pdfPageLocator(pageIndex)))
            }
            renderLocked(policy)
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val target = locator.toPdfPageIndexOrNull()
                    ?: return@withLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Unsupported locator: ${locator.scheme}")
                    )
                val clamped = clampPage(target)
                if (clamped != pageIndex) {
                    pageIndex = clamped
                    bumpGeneration()
                    _events.tryEmit(ReaderEvent.PageChanged(pdfPageLocator(pageIndex)))
                }
                renderLocked(policy)
            }
        }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val safePercent = percent.coerceIn(0.0, 1.0)
                val target = if (pageCount <= 1) {
                    0
                } else {
                    floor((pageCount - 1).toDouble() * safePercent).toInt()
                }
                val clamped = clampPage(target)
                if (clamped != pageIndex) {
                    pageIndex = clamped
                    bumpGeneration()
                    _events.tryEmit(ReaderEvent.PageChanged(pdfPageLocator(pageIndex)))
                }
                renderLocked(policy)
            }
        }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = withContext(ioDispatcher) {
        try {
            val snapshot = mutex.withLock {
                val c = constraints ?: return@withLock null
                PrefetchSnapshot(
                    constraints = c,
                    pageIndex = pageIndex,
                    config = config
                )
            } ?: return@withContext ReaderResult.Ok(Unit)

            val safeCount = count.coerceIn(0, 4)
            if (safeCount == 0 || pageCount <= 1) return@withContext ReaderResult.Ok(Unit)

            val targets = buildList {
                for (offset in 1..safeCount) {
                    val prev = snapshot.pageIndex - offset
                    val next = snapshot.pageIndex + offset
                    if (prev >= 0) add(prev)
                    if (next < pageCount) add(next)
                }
            }
            if (targets.isEmpty()) return@withContext ReaderResult.Ok(Unit)

            val semaphore = Semaphore(permits = 2)
            coroutineScope {
                targets.forEach { target ->
                    launch {
                        semaphore.withPermit {
                            prefetchOnePage(target, snapshot)
                        }
                    }
                }
            }
            ReaderResult.Ok(Unit)
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            when (reason) {
                InvalidateReason.CONTENT_CHANGED,
                InvalidateReason.CONFIG_CHANGED,
                InvalidateReason.LAYOUT_CHANGED -> {
                    bumpGeneration()
                    inflight.clear()
                    cache.evictAll()
                }
            }
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        prefetchJob?.cancel()
        finalPreheatJob?.cancel()
        scope.cancel()
        runCatching { currentProvider?.close() }
        inflight.clear()
        cache.evictAll()
    }

    private suspend fun renderLocked(policy: RenderPolicy): ReaderResult<RenderPage> {
        return try {
            val c = constraints ?: return ReaderResult.Err(
                ReaderError.Internal("LayoutConstraints not set")
            )

            val startedAt = SystemClock.elapsedRealtime()
            val basePage = backend.pageSize(pageIndex)
            val transform = computeTransform(basePage, config, c)
            val currentGeneration = generation.get()

            val provider = PdfTileProvider(
                backend = backend,
                pageIndex = pageIndex,
                basePage = basePage,
                baseScale = transform.scale,
                rotationDegrees = config.rotationDegrees,
                allowCache = policy.allowCache,
                cacheDraft = policy.quality == RenderPolicy.Quality.DRAFT,
                cache = cache,
                inflight = inflight,
                ioDispatcher = ioDispatcher,
                isValid = { generation.get() == currentGeneration }
            )
            currentProvider?.close()
            currentProvider = provider

            val metrics = RenderMetrics(
                renderTimeMs = SystemClock.elapsedRealtime() - startedAt,
                cacheHit = policy.allowCache && cache.entryCount(policy.quality) > 0
            )
            val links: List<DocumentLink> = try {
                linksResolver(pageIndex, config.rotationDegrees).map { raw ->
                    DocumentLink(
                        target = raw.target,
                        title = null,
                        bounds = listOf(
                            NormalizedRect(
                                left = raw.bounds.left,
                                top = raw.bounds.top,
                                right = raw.bounds.right,
                                bottom = raw.bounds.bottom
                            )
                        )
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                emptyList()
            }
            val searchDecorations: List<Decoration> = highlightStore
                ?.rectsFor(pageIndex)
                ?.takeIf { it.isNotEmpty() }
                ?.let { rects ->
                    listOf(
                        Decoration.Fixed(
                            page = pdfPageLocator(pageIndex),
                            rects = rects,
                            style = AnnotationStyle(
                                colorArgb = 0xFFFFEB3B.toInt(),
                                opacity = 0.35f
                            )
                        )
                    )
                }
                ?: emptyList()
            val page = RenderPage(
                id = PageId(
                    "pdf:${documentId.value}:$pageIndex:${config.rotationDegrees}:${config.fitMode}:${config.zoom}"
                ),
                locator = pdfPageLocator(pageIndex),
                content = RenderContent.Tiles(
                    pageWidthPx = transform.pageWidthPx,
                    pageHeightPx = transform.pageHeightPx,
                    tileProvider = provider
                ),
                links = links,
                decorations = searchDecorations,
                metrics = metrics
            )

            _state.value = buildState()
            _events.tryEmit(ReaderEvent.Rendered(page.id, metrics))
            schedulePrefetch(policy.prefetchNeighbors)
            scheduleFinalPreheatIfIdle(
                constraints = c,
                targetPageIndex = pageIndex,
                targetConfig = config,
                generationAtSchedule = currentGeneration
            )
            ReaderResult.Ok(page)
        } catch (t: Throwable) {
            _events.tryEmit(ReaderEvent.Error(t))
            ReaderResult.Err(t.toReaderError())
        }
    }

    private suspend fun prefetchOnePage(targetPageIndex: Int, snapshot: PrefetchSnapshot) {
        val basePage = backend.pageSize(targetPageIndex)
        val transform = computeTransform(basePage, snapshot.config, snapshot.constraints)

        val width = snapshot.constraints.viewportWidthPx
            .coerceAtMost(transform.pageWidthPx)
            .coerceAtLeast(1)
        val height = snapshot.constraints.viewportHeightPx
            .coerceAtMost(transform.pageHeightPx)
            .coerceAtLeast(1)

        val provider = PdfTileProvider(
            backend = backend,
            pageIndex = targetPageIndex,
            basePage = basePage,
            baseScale = transform.scale,
            rotationDegrees = snapshot.config.rotationDegrees,
            allowCache = true,
            cacheDraft = true,
            cache = cache,
            inflight = inflight,
            ioDispatcher = ioDispatcher,
            isValid = { true }
        )

        try {
            provider.renderTile(
                TileRequest(
                    leftPx = 0,
                    topPx = 0,
                    widthPx = width,
                    heightPx = height,
                    scale = 1f,
                    quality = RenderPolicy.Quality.DRAFT
                )
            )
        } finally {
            provider.close()
        }
    }

    private fun buildState(): RenderState {
        val totalPages = pageCount.coerceAtLeast(1)
        val clampedIndex = pageIndex.coerceIn(0, totalPages - 1)
        val percent = if (totalPages <= 1) {
            0.0
        } else {
            clampedIndex.toDouble() / (totalPages - 1).toDouble()
        }

        return RenderState(
            locator = pdfPageLocator(clampedIndex),
            progression = Progression(
                percent = percent,
                label = "${clampedIndex + 1}/$totalPages",
                current = clampedIndex + 1,
                total = totalPages
            ),
            nav = NavigationAvailability(
                canGoPrev = clampedIndex > 0,
                canGoNext = clampedIndex < totalPages - 1
            ),
            titleInView = null,
            config = config
        )
    }

    private fun schedulePrefetch(count: Int) {
        val safeCount = count.coerceIn(0, 4)
        if (safeCount == 0) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            runCatching { prefetchNeighbors(safeCount) }
        }
    }

    private fun scheduleFinalPreheatIfIdle(
        constraints: LayoutConstraints,
        targetPageIndex: Int,
        targetConfig: RenderConfig.FixedPage,
        generationAtSchedule: Long
    ) {
        finalPreheatJob?.cancel()
        finalPreheatJob = scope.launch {
            delay(finalPreheatDebounceMs)
            if (generation.get() != generationAtSchedule) return@launch
            preheatCurrentPageFinal(
                constraints = constraints,
                targetPageIndex = targetPageIndex,
                targetConfig = targetConfig,
                generationAtSchedule = generationAtSchedule
            )
        }
    }

    private suspend fun preheatCurrentPageFinal(
        constraints: LayoutConstraints,
        targetPageIndex: Int,
        targetConfig: RenderConfig.FixedPage,
        generationAtSchedule: Long
    ) {
        val basePage = backend.pageSize(targetPageIndex)
        val transform = computeTransform(basePage, targetConfig, constraints)
        val width = constraints.viewportWidthPx
            .coerceAtMost(transform.pageWidthPx)
            .coerceAtLeast(1)
        val height = constraints.viewportHeightPx
            .coerceAtMost(transform.pageHeightPx)
            .coerceAtLeast(1)

        val provider = PdfTileProvider(
            backend = backend,
            pageIndex = targetPageIndex,
            basePage = basePage,
            baseScale = transform.scale,
            rotationDegrees = targetConfig.rotationDegrees,
            allowCache = true,
            cacheDraft = false,
            cache = cache,
            inflight = inflight,
            ioDispatcher = ioDispatcher,
            isValid = { generation.get() == generationAtSchedule }
        )

        try {
            provider.renderTile(
                TileRequest(
                    leftPx = 0,
                    topPx = 0,
                    widthPx = width,
                    heightPx = height,
                    scale = 1f,
                    quality = RenderPolicy.Quality.FINAL
                )
            )
        } finally {
            provider.close()
        }
    }

    private fun clampPage(index: Int): Int {
        val maxIndex = (pageCount - 1).coerceAtLeast(0)
        return index.coerceIn(0, maxIndex)
    }

    private fun bumpGeneration() {
        generation.incrementAndGet()
        finalPreheatJob?.cancel()
        currentProvider?.close()
    }

    private data class PrefetchSnapshot(
        val constraints: LayoutConstraints,
        val pageIndex: Int,
        val config: RenderConfig.FixedPage
    )
}
