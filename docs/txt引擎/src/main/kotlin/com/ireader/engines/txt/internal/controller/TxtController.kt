package com.ireader.engines.txt.internal.controller

import android.os.SystemClock
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.internal.paging.PageSlice
import com.ireader.engines.txt.internal.paging.PageStartsIndex
import com.ireader.engines.txt.internal.paging.RenderKey
import com.ireader.engines.txt.internal.paging.TxtLastPositionStore
import com.ireader.engines.txt.internal.paging.TxtPager
import com.ireader.engines.txt.internal.paging.TxtPaginationStore
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.engines.txt.internal.util.toReaderError
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
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
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.Progression
import kotlin.math.min
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal class TxtController(
    private val store: TxtTextStore,
    private val pager: TxtPager,
    private val ioDispatcher: CoroutineDispatcher,
    private val annotations: AnnotationProvider?,
    private val paginationStore: TxtPaginationStore,
    private val lastPositionStore: TxtLastPositionStore,
    private val explicitInitial: Boolean,
    private val documentId: DocumentId,
    initialStartChar: Int = 0,
    private val locatorMapper: TxtLocatorMapper,
    private val engineConfig: TxtEngineConfig
) : ReaderController {
    private companion object {
        private const val PROGRESSIVE_BATCH_PAGES = 12
    }

    private data class RenderSnapshot(
        val revision: Long,
        val startChar: Int,
        val constraints: LayoutConstraints,
        val config: RenderConfig.ReflowText,
        val renderKey: RenderKey
    )

    private data class InflightKey(
        val renderKey: RenderKey,
        val startChar: Int
    )

    private data class CommitResult(
        val page: RenderPage,
        val locator: Locator
    )

    private data class CloseState(
        val key: RenderKey?,
        val start: Int,
        val total: Int,
        val starts: PageStartsIndex?
    )

    private val mutex = Mutex()
    private val pageCache = TxtPageSliceCache(maxPages = engineConfig.pageCacheSize)
    private val navigationHistory = TxtNavigationHistory()
    private val persistenceQueue = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

    private val inflightSlices = mutableMapOf<InflightKey, Deferred<PageSlice>>()

    private var constraints: LayoutConstraints? = null
    private var config: RenderConfig.ReflowText = RenderConfig.ReflowText()

    private var currentStartChar: Int = initialStartChar.coerceAtLeast(0)
    private var currentSlice: PageSlice? = null
    private var totalCharsCache: Int = -1

    private var renderKey: RenderKey? = null
    private var pageStarts: PageStartsIndex? = null
    private var restoredForKey: RenderKey? = null
    private var charsPerPageEstimate: Int = 0
    private var lastSavedStartChar: Int = -1
    private var lastSavedAtMs: Long = 0L
    private var stateRevision: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    private var progressiveJob: Job? = null
    private var progressiveKey: RenderKey? = null
    private var progressiveFrontierStart: Int = 0
    private var knownPageCount: Int? = null
    private val persistenceWorker = persistenceScope.launch {
        for (task in persistenceQueue) {
            runCatching { task() }
        }
    }

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 64)
    override val events: Flow<ReaderEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(
        RenderState(
            locator = locatorMapper.locatorForBoundaryOffset(currentStartChar, (currentStartChar + 1).coerceAtLeast(1)),
            progression = Progression(percent = 0.0, label = "0%"),
            nav = NavigationAvailability(canGoPrev = false, canGoNext = false),
            titleInView = null,
            config = config
        )
    )
    override val state: StateFlow<RenderState> = _state

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        val restoreKey = mutex.withLock {
            this.constraints = constraints
            clearTransientStateLocked()

            val total = totalCharsCache
            currentStartChar = if (total <= 0) {
                0
            } else {
                currentStartChar.coerceIn(0, total - 1)
            }

            ensureRenderBucketLocked()
            val key = renderKey
            val starts = pageStarts
            if (!explicitInitial && key != null && starts != null && restoredForKey != key) {
                restoredForKey = key
                key
            } else {
                null
            }
        }

        if (restoreKey != null) {
            val total = totalChars()
            val restoredLocator = lastPositionStore.load(restoreKey)
            if (restoredLocator != null && total > 0) {
                mutex.withLock {
                    if (renderKey == restoreKey && this.constraints != null) {
                        currentStartChar = locatorMapper.offsetForLocator(restoredLocator, total)
                            .coerceIn(0, total - 1)
                        pageStarts?.seedIfEmpty(0, currentStartChar)
                        pageStarts?.addStart(currentStartChar)
                        currentSlice = null
                        bumpRevisionLocked()
                    }
                }
            }
        }

        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        return mutex.withLock {
            this.config = when (config) {
                is RenderConfig.ReflowText -> config
                else -> RenderConfig.ReflowText()
            }
            clearTransientStateLocked()
            _state.value = _state.value.copy(config = this.config)
            ensureRenderBucketLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        repeat(2) {
            val snapshot = mutex.withLock {
                createSnapshotLocked()
            } ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

            try {
                val rendered = renderFromSnapshot(snapshot, policy)
                if (rendered != null) {
                    return ReaderResult.Ok(rendered)
                }
            } catch (t: Throwable) {
                _events.tryEmit(ReaderEvent.Error(t))
                return ReaderResult.Err(t.toReaderError())
            }
        }

        return ReaderResult.Err(ReaderError.Internal("Render conflicted with newer state"))
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val slice = ensureCurrentSlice(snapshot, policy.allowCache)
            ?: return ReaderResult.Err(ReaderError.Internal("Failed to render current page"))

        val total = totalChars()
        if (slice.endChar >= total) {
            return render(policy)
        }

        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = true)) return@withLock
            setCurrentStartLocked(slice.endChar, pushHistory = true)
        }

        return render(policy)
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val hasHistoryOrProgress = mutex.withLock {
            snapshotMatchesLocked(snapshot, requireStartMatch = true) &&
                (currentStartChar > 0 || !navigationHistory.isEmpty())
        }
        if (!hasHistoryOrProgress) {
            return render(policy)
        }

        val historyStart = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = true)) {
                null
            } else {
                navigationHistory.popOrNull()
            }
        }

        val destination = historyStart ?: computePrevStart(snapshot)

        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) return@withLock
            setCurrentStartLocked(destination, pushHistory = false)
        }

        return render(policy)
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (locator.scheme) {
            LocatorSchemes.TXT_OFFSET -> goToTxtOffset(locator, policy)
            LocatorSchemes.REFLOW_PAGE -> goToReflowPage(locator, policy)
            else -> ReaderResult.Err(ReaderError.Internal("Unsupported locator: ${locator.scheme}"))
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val total = totalChars()
        if (total <= 0) {
            mutex.withLock {
                setCurrentStartLocked(0, pushHistory = false)
            }
            return render(policy)
        }

        val safePercent = percent.coerceIn(0.0, 1.0)
        val target = ((total - 1) * safePercent).toInt().coerceIn(0, total - 1)
        val destinationStart = findReasonablePageStart(snapshot, target)

        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) return@withLock
            setCurrentStartLocked(destinationStart, pushHistory = true)
        }

        return render(policy)
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        if (count <= 0) return ReaderResult.Ok(Unit)

        val snapshot = mutex.withLock { createSnapshotLocked() } ?: return ReaderResult.Ok(Unit)
        val current = ensureCurrentSlice(snapshot, allowCache = true) ?: return ReaderResult.Ok(Unit)
        val total = totalChars()

        val behindCount = min(count, engineConfig.prefetchBehind.coerceAtLeast(0))
        val behindStarts = mutableListOf<Int>()
        mutex.withLock {
            val starts = pageStarts
            if (starts != null) {
                var probe = current.startChar - 1
                repeat(behindCount) {
                    if (probe < 0) return@repeat
                    val prevStart = starts.floor(probe) ?: return@repeat
                    if (prevStart >= current.startChar) return@repeat
                    behindStarts.add(prevStart)
                    probe = prevStart - 1
                }
            }
        }

        val semaphore = Semaphore(permits = 2)
        coroutineScope {
            val jobs = ArrayList<Deferred<Unit>>(behindStarts.size + 1)

            jobs += async {
                var nextStart = current.endChar
                repeat(count) {
                    if (nextStart >= total) return@async
                    val nextSlice = semaphore.withPermit {
                        getOrBuildSlice(
                            startChar = nextStart,
                            constraints = snapshot.constraints,
                            config = snapshot.config,
                            renderKey = snapshot.renderKey,
                            allowCache = true
                        ).first
                    }
                    if (nextSlice.endChar <= nextStart) return@async
                    nextStart = nextSlice.endChar
                }
            }

            for (start in behindStarts) {
                jobs += async<Unit> {
                    semaphore.withPermit {
                        getOrBuildSlice(
                            startChar = start,
                            constraints = snapshot.constraints,
                            config = snapshot.config,
                            renderKey = snapshot.renderKey,
                            allowCache = true
                        )
                    }
                    Unit
                }
            }

            jobs.awaitAll()
        }

        return ReaderResult.Ok(Unit)
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            clearTransientStateLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        prefetchJob?.cancel()
        progressiveJob?.cancel()
        runBlocking {
            val closeState = mutex.withLock {
                cancelProgressiveLocked(resetState = false)
                cancelInflightLocked()
                val key = renderKey
                val start = currentSlice?.startChar ?: currentStartChar
                val total = if (totalCharsCache > 0) totalCharsCache else (start + 1)
                val starts = pageStarts
                CloseState(key = key, start = start, total = total, starts = starts)
            }

            val key = closeState.key
            if (key != null) {
                val start = closeState.start
                val total = closeState.total
                val starts = closeState.starts
                val locator = locatorMapper.locatorForOffsetFast(start, total.coerceAtLeast(1))
                runCatching {
                    persistenceQueue.send {
                        lastPositionStore.save(key, locator)
                        starts?.let { paginationStore.flush(key, it) }
                    }
                }
            }

            persistenceQueue.close()
            persistenceWorker.join()
        }

        persistenceScope.cancel()
        scope.cancel()
    }

    private suspend fun goToTxtOffset(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        val total = totalChars()
        val c = mutex.withLock { constraints }
        if (c == null) {
            val offset = locatorMapper.offsetForLocator(locator, total).coerceAtLeast(0)
            mutex.withLock {
                setCurrentStartLocked(offset, pushHistory = false)
            }
            return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
        }

        if (total <= 0) {
            mutex.withLock {
                setCurrentStartLocked(0, pushHistory = false)
            }
            return render(policy)
        }

        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val target = locatorMapper.offsetForLocator(locator, total)
        val clampedTarget = target.coerceIn(0, total - 1)
        val destinationStart = findReasonablePageStart(snapshot, clampedTarget)

        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) return@withLock
            setCurrentStartLocked(destinationStart, pushHistory = true)
        }

        return render(policy)
    }

    private suspend fun goToReflowPage(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val pageIndex = parsePageIndex(locator)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid reflow page locator"))

        val destination = findPageStartForIndex(snapshot, pageIndex)
        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) return@withLock
            setCurrentStartLocked(destination, pushHistory = true)
        }

        return render(policy)
    }

    private suspend fun renderFromSnapshot(
        snapshot: RenderSnapshot,
        policy: RenderPolicy
    ): RenderPage? {
        val startMs = SystemClock.elapsedRealtime()
        val (slice, cacheHit) = getOrBuildSlice(
            startChar = snapshot.startChar,
            constraints = snapshot.constraints,
            config = snapshot.config,
            renderKey = snapshot.renderKey,
            allowCache = policy.allowCache
        )

        val total = totalChars()
        val safeTotal = total.coerceAtLeast(1)
        val baseLocator = locatorMapper.locatorForOffsetFast(slice.startChar, safeTotal)
        val endLocator = locatorMapper.locatorForBoundaryOffset(slice.endChar, safeTotal)
        val decorations = loadDecorations(baseLocator, endLocator)

        val committed = mutex.withLock {
            commitRenderLocked(
                snapshot = snapshot,
                slice = slice,
                totalChars = total,
                locator = baseLocator,
                decorations = decorations,
                cacheHit = cacheHit,
                startMs = startMs
            )
        } ?: return null

        _events.tryEmit(ReaderEvent.PageChanged(committed.locator))
        _events.tryEmit(ReaderEvent.Rendered(committed.page.id, committed.page.metrics))
        schedulePrefetch(policy.prefetchNeighbors)
        scheduleProgressivePagination()
        return committed.page
    }

    private suspend fun ensureCurrentSlice(
        snapshot: RenderSnapshot,
        allowCache: Boolean
    ): PageSlice? {
        val existing = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = true)) {
                null
            } else {
                currentSlice?.takeIf { it.startChar == snapshot.startChar }
            }
        }
        if (existing != null) return existing

        val (slice, _) = getOrBuildSlice(
            startChar = snapshot.startChar,
            constraints = snapshot.constraints,
            config = snapshot.config,
            renderKey = snapshot.renderKey,
            allowCache = allowCache
        )

        mutex.withLock {
            if (snapshotMatchesLocked(snapshot, requireStartMatch = true)) {
                currentSlice = slice
            }
        }

        return slice
    }

    private suspend fun getOrBuildSlice(
        startChar: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        renderKey: RenderKey,
        allowCache: Boolean
    ): Pair<PageSlice, Boolean> {
        if (allowCache) {
            val cached = mutex.withLock { pageCache.get(startChar, renderKey) }
            if (cached != null) return cached to true
        }

        val inflightKey = InflightKey(renderKey = renderKey, startChar = startChar)
        val deferred = mutex.withLock {
            inflightSlices[inflightKey] ?: scope.async(start = CoroutineStart.LAZY) {
                pager.pageAt(startChar, constraints, config)
            }.also { created ->
                inflightSlices[inflightKey] = created
            }
        }

        val built = try {
            deferred.await()
        } finally {
            mutex.withLock {
                if (inflightSlices[inflightKey] === deferred) {
                    inflightSlices.remove(inflightKey)
                }
            }
        }

        if (allowCache) {
            mutex.withLock {
                if (this.renderKey == renderKey) {
                    pageCache.put(built, renderKey)
                }
            }
        }

        return built to false
    }

    private suspend fun totalChars(): Int {
        val cached = totalCharsCache
        if (cached >= 0) return cached

        val loaded = store.totalChars().coerceAtLeast(0)
        return mutex.withLock {
            if (totalCharsCache < 0) {
                totalCharsCache = loaded
            }
            totalCharsCache.coerceAtLeast(0)
        }
    }

    private suspend fun findReasonablePageStart(
        snapshot: RenderSnapshot,
        targetChar: Int
    ): Int {
        val total = totalChars()
        if (total <= 0) return 0

        val target = targetChar.coerceIn(0, total - 1)
        var start = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                0
            } else {
                pageStarts?.floor(target) ?: 0
            }
        }.coerceIn(0, total - 1)

        repeat(32) {
            val (slice, _) = getOrBuildSlice(
                startChar = start,
                constraints = snapshot.constraints,
                config = snapshot.config,
                renderKey = snapshot.renderKey,
                allowCache = true
            )

            mutex.withLock {
                if (snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                    pageStarts?.addStart(slice.startChar)
                    if (slice.endChar in 1 until total) {
                        pageStarts?.addStart(slice.endChar)
                    }
                }
            }

            if (target < slice.endChar || slice.endChar >= total || slice.endChar <= start) {
                return slice.startChar
            }
            start = slice.endChar
        }

        return start
    }

    private suspend fun computePrevStart(snapshot: RenderSnapshot): Int {
        if (snapshot.startChar <= 0) return 0

        val known = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                null
            } else {
                pageStarts?.floor(snapshot.startChar - 1)
            }
        }
        if (known != null && known < snapshot.startChar) {
            return known
        }

        val total = totalChars()
        val estimate = pager.estimateCharsPerPage(snapshot.constraints, snapshot.config)
        var probe = (snapshot.startChar - (estimate * 2)).coerceAtLeast(0)
        var previousStart = 0

        repeat(24) {
            val (slice, _) = getOrBuildSlice(
                startChar = probe,
                constraints = snapshot.constraints,
                config = snapshot.config,
                renderKey = snapshot.renderKey,
                allowCache = true
            )

            mutex.withLock {
                if (snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                    pageStarts?.addStart(slice.startChar)
                    if (slice.endChar in 1 until total) {
                        pageStarts?.addStart(slice.endChar)
                    }
                }
            }

            if (slice.endChar >= snapshot.startChar || slice.endChar <= probe) {
                return previousStart.coerceAtLeast(0)
            }
            previousStart = slice.startChar
            probe = slice.endChar
        }

        return (snapshot.startChar - estimate).coerceAtLeast(0)
    }

    private suspend fun findPageStartForIndex(snapshot: RenderSnapshot, targetPageIndex: Int): Int {
        val safeTarget = targetPageIndex.coerceAtLeast(0)
        if (safeTarget == 0) return 0

        val known = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                Triple<Int?, Int?, Int?>(null, null, null)
            } else {
                val starts = pageStarts
                val knownCount = knownPageCount
                val boundedTarget = if (knownCount != null && knownCount > 0) {
                    safeTarget.coerceAtMost(knownCount - 1)
                } else {
                    safeTarget
                }
                val direct = starts?.getAtOrNull(boundedTarget)
                val lastKnown = if (knownCount != null && knownCount > 0) {
                    starts?.getAtOrNull(knownCount - 1)
                } else {
                    null
                }
                Triple(direct, lastKnown, knownCount)
            }
        }
        val directKnown = known.first
        if (directKnown != null) return directKnown
        val lastKnown = known.second
        val knownCount = known.third
        if (knownCount != null && lastKnown != null && safeTarget >= knownCount) {
            return lastKnown
        }

        val total = totalChars()
        if (total <= 0) return 0

        val estimate = pager.estimateCharsPerPage(snapshot.constraints, snapshot.config).coerceAtLeast(1)
        val estimatedTotalPages = ((total + estimate - 1) / estimate).coerceAtLeast(1)
        val percent = (safeTarget.toDouble() / estimatedTotalPages.toDouble()).coerceIn(0.0, 1.0)
        val targetChar = ((total - 1) * percent).toInt().coerceIn(0, total - 1)
        return findReasonablePageStart(snapshot, targetChar)
    }

    private suspend fun loadDecorations(locator: Locator, endLocator: Locator): List<com.ireader.reader.api.annotation.Decoration> {
        val provider = annotations ?: return emptyList()
        return when (
            val result = provider.decorationsFor(
                AnnotationQuery(
                    range = LocatorRange(
                        start = locator,
                        end = endLocator
                    )
                )
            )
        ) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> emptyList()
        }
    }

    private fun commitRenderLocked(
        snapshot: RenderSnapshot,
        slice: PageSlice,
        totalChars: Int,
        locator: Locator,
        decorations: List<com.ireader.reader.api.annotation.Decoration>,
        cacheHit: Boolean,
        startMs: Long
    ): CommitResult? {
        if (!snapshotMatchesLocked(snapshot, requireStartMatch = true)) {
            return null
        }

        currentSlice = slice
        currentStartChar = slice.startChar

        val starts = pageStarts ?: return null
        val newAdds = recordPageBoundaries(starts, slice, totalChars)
        if (newAdds > 0) {
            renderKey?.let { key ->
                enqueuePersistence {
                    paginationStore.maybePersist(key, starts, newAdds)
                }
            }
        }

        val knownPages = knownPageCount?.coerceAtLeast(1)
        val estimate = charsPerPageEstimate.coerceAtLeast(1)
        val totalPagesEstimate = ((totalChars + estimate - 1) / estimate).coerceAtLeast(1)
        val pageCount = knownPages ?: totalPagesEstimate
        val currentPage = (starts.floorIndexOf(slice.startChar) + 1).coerceAtLeast(1)
        val safeCurrentPage = currentPage.coerceAtMost(pageCount)
        val pageIndex = (safeCurrentPage - 1).coerceAtLeast(0)
        val percent = if (totalChars <= 0) {
            0.0
        } else {
            (slice.startChar.toDouble() / totalChars.toDouble()).coerceIn(0.0, 1.0)
        }

        val enrichedLocator = locator.withPageExtras(
            pageIndex = pageIndex,
            pageCountEstimate = totalPagesEstimate,
            pageCount = pageCount,
            pageCountKnown = (knownPages != null)
        )

        val progression = Progression(
            percent = percent,
            label = "$safeCurrentPage/$pageCount"
        )
        val nav = NavigationAvailability(
            canGoPrev = slice.startChar > 0,
            canGoNext = slice.endChar < totalChars
        )

        val pageId = PageId("txt:${slice.startChar}")
        val metrics = RenderMetrics(
            renderTimeMs = (SystemClock.elapsedRealtime() - startMs),
            cacheHit = cacheHit
        )
        val page = RenderPage(
            id = pageId,
            locator = enrichedLocator,
            content = RenderContent.Text(
                text = slice.text,
                mapping = TxtTextMapping(
                    pageStartChar = slice.startChar,
                    pageEndCharExclusive = slice.endChar
                )
            ),
            links = emptyList(),
            decorations = decorations,
            metrics = metrics
        )

        _state.value = RenderState(
            locator = enrichedLocator,
            progression = progression,
            nav = nav,
            titleInView = null,
            config = config
        )

        renderKey?.let { key ->
            val now = SystemClock.elapsedRealtime()
            val start = slice.startChar
            if (start != lastSavedStartChar && (now - lastSavedAtMs) >= lastPositionStore.minIntervalMs) {
                lastSavedStartChar = start
                lastSavedAtMs = now
                enqueuePersistence { lastPositionStore.save(key, enrichedLocator) }
            }
        }

        return CommitResult(page = page, locator = enrichedLocator)
    }

    private fun Locator.withPageExtras(
        pageIndex: Int,
        pageCountEstimate: Int,
        pageCount: Int,
        pageCountKnown: Boolean
    ): Locator {
        val extras = LinkedHashMap(this.extras)
        extras["pageIndex"] = pageIndex.toString()
        extras["pageCountEstimate"] = pageCountEstimate.toString()
        extras["pageCount"] = pageCount.toString()
        extras["pageCountKnown"] = pageCountKnown.toString()
        return copy(extras = extras)
    }

    private fun parsePageIndex(locator: Locator): Int? {
        val raw = locator.extras["pageIndex"] ?: locator.value
        return raw.toIntOrNull()?.coerceAtLeast(0)
    }

    private fun createSnapshotLocked(): RenderSnapshot? {
        val c = constraints ?: return null
        ensureRenderBucketLocked()
        val key = renderKey ?: return null
        return RenderSnapshot(
            revision = stateRevision,
            startChar = currentStartChar,
            constraints = c,
            config = config,
            renderKey = key
        )
    }

    private fun snapshotMatchesLocked(snapshot: RenderSnapshot, requireStartMatch: Boolean): Boolean {
        if (stateRevision != snapshot.revision) return false
        if (renderKey != snapshot.renderKey) return false
        if (requireStartMatch && currentStartChar != snapshot.startChar) return false
        return true
    }

    private fun ensureRenderBucketLocked() {
        val c = constraints ?: return
        val key = RenderKey.of(
            docId = documentId.value,
            charset = store.charset.name(),
            constraints = c,
            config = config
        )
        if (renderKey != key || pageStarts == null) {
            if (progressiveKey != key) {
                progressiveJob?.cancel()
                progressiveJob = null
                progressiveFrontierStart = 0
                knownPageCount = null
                progressiveKey = key
            }
            renderKey = key
            pageStarts = paginationStore.getOrCreate(key)
        }
        pageStarts?.seedIfEmpty(0, currentStartChar)
        charsPerPageEstimate = pager.estimateCharsPerPage(c, config)
    }

    private fun clearTransientStateLocked() {
        pageCache.clear()
        currentSlice = null
        prefetchJob?.cancel()
        prefetchJob = null
        cancelProgressiveLocked()
        cancelInflightLocked()
        bumpRevisionLocked()
    }

    private fun cancelInflightLocked() {
        inflightSlices.values.forEach { it.cancel() }
        inflightSlices.clear()
    }

    private fun cancelProgressiveLocked(resetState: Boolean = true) {
        progressiveJob?.cancel()
        progressiveJob = null
        if (resetState) {
            progressiveKey = null
            progressiveFrontierStart = 0
            knownPageCount = null
        }
    }

    private fun schedulePrefetch(n: Int) {
        val count = (if (n > 0) n else engineConfig.prefetchAhead).coerceIn(0, 8)
        if (count <= 0) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            runCatching { prefetchNeighbors(count) }
        }
    }

    private suspend fun scheduleProgressivePagination() {
        val key = mutex.withLock {
            val currentKey = renderKey ?: return@withLock null
            if (constraints == null) return@withLock null
            if (knownPageCount != null) return@withLock null

            if (progressiveKey != currentKey) {
                progressiveJob?.cancel()
                progressiveJob = null
                progressiveFrontierStart = 0
                knownPageCount = null
                progressiveKey = currentKey
            }

            if (progressiveJob?.isActive == true) {
                return@withLock null
            }
            currentKey
        } ?: return

        val job = scope.launch {
            runCatching { runProgressivePagination(key) }
        }

        mutex.withLock {
            if (renderKey == key && progressiveKey == key && knownPageCount == null) {
                progressiveJob = job
            } else {
                job.cancel()
            }
        }
    }

    private suspend fun runProgressivePagination(key: RenderKey) {
        val runningJob = coroutineContext[Job]
        try {
            while (true) {
                val seed = mutex.withLock {
                    if (renderKey != key || knownPageCount != null) return@withLock null
                    val currentConstraints = constraints ?: return@withLock null
                    ProgressiveSeed(
                        startChar = progressiveFrontierStart.coerceAtLeast(0),
                        constraints = currentConstraints,
                        config = config
                    )
                } ?: return

                val total = totalChars()
                if (total <= 0) {
                    mutex.withLock {
                        if (renderKey == key) {
                            knownPageCount = pageStarts?.size?.coerceAtLeast(1) ?: 1
                        }
                    }
                    return
                }

                var start = seed.startChar.coerceIn(0, total)
                var madeProgress = false

                repeat(PROGRESSIVE_BATCH_PAGES) {
                    if (start >= total) return@repeat

                    val slice = getOrBuildSlice(
                        startChar = start,
                        constraints = seed.constraints,
                        config = seed.config,
                        renderKey = key,
                        allowCache = true
                    ).first

                    val next = slice.endChar.coerceIn(0, total)
                    val shouldStop = mutex.withLock {
                        if (renderKey != key) {
                            true
                        } else {
                            val starts = pageStarts ?: return@withLock true
                            val newAdds = recordPageBoundaries(starts, slice, total)
                            if (newAdds > 0) {
                                enqueuePersistence {
                                    paginationStore.maybePersist(key, starts, newAdds)
                                }
                            }
                            progressiveFrontierStart = next
                            if (next >= total || next <= start) {
                                knownPageCount = starts.size.coerceAtLeast(1)
                                true
                            } else {
                                false
                            }
                        }
                    }

                    if (shouldStop) return
                    madeProgress = true
                    start = next
                }

                if (!madeProgress) {
                    mutex.withLock {
                        if (renderKey == key) {
                            knownPageCount = pageStarts?.size?.coerceAtLeast(1)
                        }
                    }
                    return
                }

                yield()
            }
        } finally {
            mutex.withLock {
                if (progressiveJob === runningJob) {
                    progressiveJob = null
                }
            }
        }
    }

    private data class ProgressiveSeed(
        val startChar: Int,
        val constraints: LayoutConstraints,
        val config: RenderConfig.ReflowText
    )

    private fun setCurrentStartLocked(destinationStart: Int, pushHistory: Boolean) {
        val safeDestination = destinationStart.coerceAtLeast(0)
        if (safeDestination == currentStartChar) return
        if (pushHistory) {
            navigationHistory.push(currentStartChar)
        }
        currentStartChar = safeDestination
        currentSlice = null
        bumpRevisionLocked()
    }

    private fun recordPageBoundaries(
        starts: PageStartsIndex,
        slice: PageSlice,
        totalChars: Int
    ): Int {
        var additions = 0
        if (starts.addStart(slice.startChar)) additions += 1
        if (slice.endChar in 1 until totalChars && starts.addStart(slice.endChar)) {
            additions += 1
        }
        return additions
    }

    private fun enqueuePersistence(task: suspend () -> Unit) {
        persistenceQueue.trySend(task)
    }

    private fun bumpRevisionLocked() {
        stateRevision += 1
    }
}
