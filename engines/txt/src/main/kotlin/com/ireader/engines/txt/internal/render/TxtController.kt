@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions"
)

package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.link.LinkDetector
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.pagination.PageSlice
import com.ireader.engines.txt.internal.pagination.TxtPaginator
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
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
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.model.Locator
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.measureTimeMillis

internal class TxtController(
    private val documentKey: String,
    private val store: Utf16TextStore,
    private val meta: TxtMeta,
    initialOffset: Long,
    initialConfig: RenderConfig.ReflowText,
    maxPageCache: Int,
    persistPagination: Boolean,
    private val files: TxtBookFiles,
    private val annotationProvider: AnnotationProvider?,
    private val ioDispatcher: CoroutineDispatcher,
    defaultDispatcher: CoroutineDispatcher
) : ReaderController {

    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val mutex = Mutex()

    private var softBreakIndex: SoftBreakIndex? = SoftBreakIndex.openIfValid(files.softBreakIdx, meta)
    private val paginator = TxtPaginator(store, meta, softBreakIndex)
    private val sliceCache = TxtPageSliceCache(
        paginator = paginator,
        maxPageCache = maxPageCache,
        maxOffsetProvider = { store.lengthChars }
    )
    private val paginationIndex = TxtPaginationIndexStore(
        enabled = persistPagination,
        documentKey = documentKey,
        paginationDir = files.paginationDir
    )
    private var pageCompletionJob: Job? = null

    private val eventsMutable = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 32)
    override val events: Flow<ReaderEvent> = eventsMutable.asSharedFlow()

    private val initialStart = initialOffset.coerceIn(0L, store.lengthChars)
    private val navigation = TxtNavigationState(initialStart)

    private var constraints: LayoutConstraints? = null
    private var currentConfig: RenderConfig.ReflowText = initialConfig
    private var currentEffectiveConfig: RenderConfig.ReflowText = initialConfig.toTxtEffectiveConfig()

    private val stateMutable = MutableStateFlow(
        RenderState(
            locator = navigation.locatorFor(store.lengthChars),
            progression = navigation.progressionFor(store.lengthChars),
            nav = NavigationAvailability(
                canGoPrev = navigation.canGoPrev(),
                canGoNext = navigation.canGoNext(store.lengthChars)
            ),
            config = initialConfig
        )
    )
    override val state: StateFlow<RenderState> = stateMutable.asStateFlow()

    init {
        if (meta.hardWrapLikely && softBreakIndex == null) {
            scope.launch {
                runCatching {
                    SoftBreakIndexBuilder.buildIfNeeded(files, meta, ioDispatcher)
                    val loaded = SoftBreakIndex.openIfValid(files.softBreakIdx, meta)
                    mutex.withLock {
                        softBreakIndex?.close()
                        softBreakIndex = loaded
                        paginator.setSoftBreakIndex(loaded)
                        sliceCache.clear()
                    }
                }
            }
        }
    }

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun unbindSurface(): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return mutex.withLock {
            this.constraints = constraints
            sliceCache.clear()
            reloadPaginationIndexIfNeededLocked()
            updateStateLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val reflow = config as? RenderConfig.ReflowText
            ?: return ReaderResult.Err(ReaderError.Internal("TXT requires ReflowText config"))
        return mutex.withLock {
            currentConfig = reflow
            currentEffectiveConfig = reflow.toTxtEffectiveConfig()
            stateMutable.value = stateMutable.value.copy(config = reflow)
            sliceCache.clear()
            reloadPaginationIndexIfNeededLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        val renderResult = mutex.withLock {
            renderLocked(policy)
        }
        if (renderResult is ReaderResult.Ok && policy.prefetchNeighbors > 0) {
            scope.launch {
                runCatching { prefetchNeighbors(policy.prefetchNeighbors) }
            }
        }
        return renderResult
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val constraintsLocal = constraints
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
            val current = sliceCache.getOrBuild(
                start = navigation.currentStart,
                constraints = constraintsLocal,
                config = currentEffectiveConfig,
                allowCache = true
            )
            if (current.endOffset >= store.lengthChars) {
                return@withLock buildPageResultLocked(
                    slice = current,
                    renderTimeMs = 0L,
                    cacheHit = true
                )
            }
            navigation.moveTo(current.endOffset, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val constraintsLocal = constraints
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
            if (!navigation.canGoPrev()) {
                return@withLock renderLocked(policy)
            }
            val target = navigation.findPreviousStart(
                fromStart = navigation.currentStart,
                maxOffset = store.lengthChars,
                constraints = constraintsLocal
            ) { start, constraintsArg ->
                sliceCache.getOrBuild(
                    start = start,
                    constraints = constraintsArg,
                    config = currentEffectiveConfig,
                    allowCache = true
                )
            }
            navigation.moveTo(target, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        val offset = TxtBlockLocatorCodec.parseOffset(locator, store.lengthChars)
            ?: return ReaderResult.Err(ReaderError.Internal("Unsupported TXT locator: ${locator.scheme}:${locator.value}"))
        return mutex.withLock {
            navigation.moveTo(offset, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        val clamped = percent.coerceIn(0.0, 1.0)
        return mutex.withLock {
            val target = paginationIndex.startForProgress(clamped)
                ?: (store.lengthChars * clamped).toLong()
            navigation.moveTo(target, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        if (count <= 0) {
            return ReaderResult.Ok(Unit)
        }
        return mutex.withLock {
            val constraintsLocal = constraints ?: return@withLock ReaderResult.Ok(Unit)
            val currentSlice = sliceCache.getOrBuild(
                start = navigation.currentStart,
                constraints = constraintsLocal,
                config = currentEffectiveConfig,
                allowCache = true
            )

            var forwardStart = currentSlice.endOffset
            repeat(count) {
                if (forwardStart >= store.lengthChars) return@repeat
                val next = sliceCache.getOrBuild(
                    start = forwardStart,
                    constraints = constraintsLocal,
                    config = currentEffectiveConfig,
                    allowCache = true
                )
                if (next.endOffset <= forwardStart) {
                    return@repeat
                }
                forwardStart = next.endOffset
            }

            var backwardStart = currentSlice.startOffset
            repeat(count) {
                if (backwardStart <= 0L) return@repeat
                val prevStart = navigation.findPreviousStart(
                    fromStart = backwardStart,
                    maxOffset = store.lengthChars,
                    constraints = constraintsLocal
                ) { start, constraintsArg ->
                    sliceCache.getOrBuild(
                        start = start,
                        constraints = constraintsArg,
                        config = currentEffectiveConfig,
                        allowCache = true
                    )
                }
                if (prevStart >= backwardStart) {
                    return@repeat
                }
                sliceCache.getOrBuild(
                    start = prevStart,
                    constraints = constraintsLocal,
                    config = currentEffectiveConfig,
                    allowCache = true
                )
                backwardStart = prevStart
            }
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            sliceCache.clear()
            if (reason == InvalidateReason.CONFIG_CHANGED || reason == InvalidateReason.LAYOUT_CHANGED) {
                paginationIndex.invalidateProfile()
                pageCompletionJob?.cancel()
            }
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        runCatching { paginationIndex.saveIfDirty() }
        pageCompletionJob?.cancel()
        runCatching { softBreakIndex?.close() }
        scope.cancel()
    }

    private suspend fun renderLocked(policy: RenderPolicy): ReaderResult<RenderPage> {
        val constraintsLocal = constraints
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        var builtSlice: PageSlice? = null
        var cacheHit = false
        val elapsed = measureTimeMillis {
            if (policy.allowCache) {
                builtSlice = sliceCache.getCached(navigation.currentStart)
                if (builtSlice != null) {
                    cacheHit = true
                }
            }
            if (builtSlice == null) {
                builtSlice = sliceCache.getOrBuild(
                    start = navigation.currentStart,
                    constraints = constraintsLocal,
                    config = currentEffectiveConfig,
                    allowCache = false
                )
            }
        }
        val slice = builtSlice ?: return ReaderResult.Err(ReaderError.Internal("Failed to paginate TXT page"))

        val page = buildPageResultLocked(
            slice = slice,
            renderTimeMs = elapsed,
            cacheHit = cacheHit
        )
        if (page is ReaderResult.Ok) {
            paginationIndex.record(slice.startOffset)
            maybeSchedulePageCompletionLocked()
        }
        return page
    }

    private suspend fun buildPageResultLocked(
        slice: PageSlice,
        renderTimeMs: Long,
        cacheHit: Boolean
    ): ReaderResult<RenderPage> {
        navigation.updateFromSlice(slice)
        updateStateLocked()
        val pageRange = TxtBlockLocatorCodec.rangeForOffsets(
            startOffset = slice.startOffset,
            endOffset = slice.endOffset,
            maxOffset = store.lengthChars
        )
        val decorations = annotationProvider
            ?.decorationsFor(AnnotationQuery(range = pageRange))
            ?.getOrNull()
            ?: emptyList()
        val links = LinkDetector.detect(slice.text)

        val page = RenderPage(
            id = PageId("${slice.startOffset}-${slice.endOffset}"),
            locator = navigation.locatorFor(store.lengthChars),
            content = RenderContent.Text(
                text = slice.text,
                mapping = TxtTextMapping(slice.startOffset, slice.endOffset)
            ),
            links = links,
            decorations = decorations,
            metrics = RenderMetrics(
                renderTimeMs = renderTimeMs,
                cacheHit = cacheHit
            )
        )
        eventsMutable.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        eventsMutable.tryEmit(ReaderEvent.PageChanged(page.locator))
        return ReaderResult.Ok(page)
    }

    private fun updateStateLocked() {
        stateMutable.value = stateMutable.value.copy(
            locator = navigation.locatorFor(store.lengthChars),
            progression = navigation.progressionFor(store.lengthChars),
            nav = NavigationAvailability(
                canGoPrev = navigation.canGoPrev(),
                canGoNext = navigation.canGoNext(store.lengthChars)
            ),
            config = currentConfig
        )
    }

    private fun reloadPaginationIndexIfNeededLocked() {
        paginationIndex.reloadIfNeeded(
            constraints = constraints,
            profileConfig = currentEffectiveConfig
        )
        pageCompletionJob?.cancel()
    }

    private fun maybeSchedulePageCompletionLocked() {
        if (!paginationIndex.hasActiveProfile()) {
            return
        }
        if (pageCompletionJob?.isActive == true) {
            return
        }
        val constraintsLocal = constraints ?: return
        pageCompletionJob = scope.launch {
            runCatching {
                mutex.withLock {
                    completePageMapForwardLocked(constraintsLocal, maxPages = 24)
                }
            }
        }
    }

    private suspend fun completePageMapForwardLocked(
        constraints: LayoutConstraints,
        maxPages: Int
    ) {
        if (maxPages <= 0 || store.lengthChars <= 0L) {
            return
        }
        var cursor = paginationIndex.lastKnownStart() ?: navigation.currentStart
        cursor = cursor.coerceIn(0L, store.lengthChars)
        var steps = 0
        while (cursor < store.lengthChars && steps < maxPages) {
            val slice = sliceCache.getOrBuild(
                start = cursor,
                constraints = constraints,
                config = currentEffectiveConfig,
                allowCache = true
            )
            paginationIndex.record(slice.startOffset)
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            steps++
        }
        paginationIndex.saveIfDirty()
    }
}
