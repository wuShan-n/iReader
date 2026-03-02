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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val mutex = Mutex()
    private val pageCache = TxtPageSliceCache(maxPages = engineConfig.pageCacheSize)
    private val navigationHistory = TxtNavigationHistory()
    private val persistenceQueue = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

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

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
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
        return mutex.withLock {
            this.constraints = constraints
            pageCache.clear()
            currentSlice = null

            val total = totalCharsLocked()
            currentStartChar = if (total == 0) 0 else currentStartChar.coerceIn(0, total - 1)
            ensureRenderBucketLocked()

            val key = renderKey
            val starts = pageStarts
            if (!explicitInitial && key != null && starts != null && restoredForKey != key) {
                restoredForKey = key
                val restoredLocator = lastPositionStore.load(key)
                if (restoredLocator != null && total > 0) {
                    currentStartChar = locatorMapper.offsetForLocator(restoredLocator, total)
                        .coerceIn(0, total - 1)
                    starts.seedIfEmpty(0, currentStartChar)
                    starts.addStart(currentStartChar)
                    currentSlice = null
                }
            }
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        return mutex.withLock {
            this.config = when (config) {
                is RenderConfig.ReflowText -> config
                else -> RenderConfig.ReflowText()
            }
            pageCache.clear()
            currentSlice = null
            _state.value = _state.value.copy(config = this.config)
            ensureRenderBucketLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val c = constraints ?: return@withLock ReaderResult.Err(
                ReaderError.Internal("LayoutConstraints not set")
            )
            renderLocked(c, policy)
        }
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val c = constraints ?: return@withLock ReaderResult.Err(
                ReaderError.Internal("LayoutConstraints not set")
            )
            val slice = ensureSliceLocked(c, policy)
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("Failed to render current page"))

            val total = totalCharsLocked()
            if (slice.endChar >= total) {
                return@withLock renderLocked(c, policy)
            }

            navigationHistory.push(currentStartChar)
            currentStartChar = slice.endChar
            currentSlice = null
            renderLocked(c, policy)
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val c = constraints ?: return@withLock ReaderResult.Err(
                ReaderError.Internal("LayoutConstraints not set")
            )

            if (currentStartChar <= 0 && navigationHistory.isEmpty()) {
                return@withLock renderLocked(c, policy)
            }

            val previous = navigationHistory.popOrNull() ?: computePrevStartLocked(c)

            currentStartChar = previous.coerceAtLeast(0)
            currentSlice = null
            renderLocked(c, policy)
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            if (locator.scheme != LocatorSchemes.TXT_OFFSET) {
                return@withLock ReaderResult.Err(ReaderError.Internal("Unsupported locator: ${locator.scheme}"))
            }

            val c = constraints
            if (c == null) {
                val total = totalCharsLocked()
                currentStartChar = locatorMapper.offsetForLocator(locator, total)
                    .coerceAtLeast(0)
                currentSlice = null
                return@withLock ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
            }

            val total = totalCharsLocked()
            if (total == 0) {
                currentStartChar = 0
                currentSlice = null
                return@withLock renderLocked(c, policy)
            }

            val target = locatorMapper.offsetForLocator(locator, total)
            val clampedTarget = target.coerceIn(0, total - 1)
            val destinationStart = findReasonablePageStartLocked(clampedTarget, c)
            moveToStartLocked(destinationStart)
            renderLocked(c, policy)
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val c = constraints ?: return@withLock ReaderResult.Err(
                ReaderError.Internal("LayoutConstraints not set")
            )
            val total = totalCharsLocked()
            if (total <= 0) {
                currentStartChar = 0
                currentSlice = null
                return@withLock renderLocked(c, policy)
            }

            val safePercent = percent.coerceIn(0.0, 1.0)
            val target = ((total - 1) * safePercent).toInt().coerceIn(0, total - 1)
            val destinationStart = findReasonablePageStartLocked(target, c)
            moveToStartLocked(destinationStart)
            renderLocked(c, policy)
        }
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        if (count <= 0) return ReaderResult.Ok(Unit)
        return mutex.withLock {
            val c = constraints ?: return@withLock ReaderResult.Ok(Unit)
            val slice = ensureSliceLocked(c, RenderPolicy.Default) ?: return@withLock ReaderResult.Ok(Unit)
            val total = totalCharsLocked()

            var nextStart = slice.endChar
            repeat(count) {
                if (nextStart >= total) return@repeat
                val nextSlice = prefetchAtLocked(nextStart, c)
                if (nextSlice == null || nextSlice.endChar <= nextStart) return@repeat
                nextStart = nextSlice.endChar
            }

            val behind = min(count, engineConfig.prefetchBehind.coerceAtLeast(0))
            val starts = pageStarts
            if (starts != null) {
                var probe = slice.startChar - 1
                repeat(behind) {
                    if (probe < 0) return@repeat
                    val prevStart = starts.floor(probe) ?: return@repeat
                    if (prevStart >= slice.startChar) return@repeat
                    prefetchAtLocked(prevStart, c)
                    probe = prevStart - 1
                }
            }
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            pageCache.clear()
            currentSlice = null
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        prefetchJob?.cancel()
        runBlocking {
            renderKey?.let { key ->
                val start = currentSlice?.startChar ?: currentStartChar
                val total = if (totalCharsCache > 0) totalCharsCache else (start + 1)
                val locator = locatorMapper.locatorForOffsetFast(start, total.coerceAtLeast(1))
                runCatching {
                    persistenceQueue.send {
                        lastPositionStore.save(key, locator)
                        pageStarts?.let { starts ->
                            paginationStore.flush(key, starts)
                        }
                    }
                }
            }
            persistenceQueue.close()
            persistenceWorker.join()
        }
        persistenceScope.cancel()
        scope.cancel()
    }

    private suspend fun renderLocked(
        constraints: LayoutConstraints,
        policy: RenderPolicy
    ): ReaderResult<RenderPage> {
        return try {
            ensureRenderBucketLocked()
            val startMs = SystemClock.elapsedRealtime()
            val (slice, cacheHit) = getOrBuildSliceLocked(currentStartChar, constraints, policy)
            currentSlice = slice
            currentStartChar = slice.startChar

            val total = totalCharsLocked()
            val starts = pageStarts
                ?: return ReaderResult.Err(ReaderError.Internal("Pagination bucket not initialized"))
            val newAdds = recordPageBoundaries(starts, slice, total)
            if (newAdds > 0) {
                renderKey?.let { key ->
                    enqueuePersistence {
                        paginationStore.maybePersist(key, starts, newAdds)
                    }
                }
            }

            val locator = locatorMapper.locatorForOffset(slice.startChar, total)
            val endLocator = locatorMapper.locatorForBoundaryOffset(slice.endChar, total)

            val estimate = charsPerPageEstimate.coerceAtLeast(1)
            val totalPagesEstimate = ((total + estimate - 1) / estimate).coerceAtLeast(1)
            val currentPage = (starts.floorIndexOf(slice.startChar) + 1).coerceAtLeast(1)
            val percent = if (total <= 0) {
                0.0
            } else {
                (slice.startChar.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
            }
            val progression = Progression(
                percent = percent,
                label = "$currentPage/$totalPagesEstimate"
            )
            val nav = NavigationAvailability(
                canGoPrev = slice.startChar > 0,
                canGoNext = slice.endChar < total
            )

            val decorations = annotations?.let { provider ->
                when (
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
            } ?: emptyList()

            val pageId = PageId("txt:${slice.startChar}")
            val metrics = RenderMetrics(
                renderTimeMs = (SystemClock.elapsedRealtime() - startMs),
                cacheHit = cacheHit
            )
            val page = RenderPage(
                id = pageId,
                locator = locator,
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
                locator = locator,
                progression = progression,
                nav = nav,
                titleInView = null,
                config = config
            )
            _events.tryEmit(ReaderEvent.PageChanged(locator))
            _events.tryEmit(ReaderEvent.Rendered(pageId, metrics))

            renderKey?.let { key ->
                val now = SystemClock.elapsedRealtime()
                val start = slice.startChar
                if (start != lastSavedStartChar && (now - lastSavedAtMs) >= lastPositionStore.minIntervalMs) {
                    lastSavedStartChar = start
                    lastSavedAtMs = now
                    enqueuePersistence { lastPositionStore.save(key, locator) }
                }
            }

            schedulePrefetch(policy.prefetchNeighbors)
            ReaderResult.Ok(page)
        } catch (t: Throwable) {
            _events.tryEmit(ReaderEvent.Error(t))
            ReaderResult.Err(t.toReaderError())
        }
    }

    private suspend fun ensureSliceLocked(
        constraints: LayoutConstraints,
        policy: RenderPolicy
    ): PageSlice? {
        currentSlice?.let { return it }
        val (slice) = getOrBuildSliceLocked(currentStartChar, constraints, policy)
        currentSlice = slice
        return slice
    }

    private suspend fun getOrBuildSliceLocked(
        startChar: Int,
        constraints: LayoutConstraints,
        policy: RenderPolicy
    ): Pair<PageSlice, Boolean> {
        if (policy.allowCache) {
            pageCache.get(startChar, renderKey)?.let { return it to true }
        }

        val slice = pager.pageAt(startChar, constraints, config)
        if (policy.allowCache) {
            pageCache.put(slice, renderKey)
        }
        return slice to false
    }

    private suspend fun prefetchAtLocked(
        startChar: Int,
        constraints: LayoutConstraints
    ): PageSlice? {
        pageCache.get(startChar, renderKey)?.let { return it }
        val total = totalCharsLocked()
        if (startChar >= total) return null

        val slice = pager.pageAt(startChar, constraints, config)
        pageCache.put(slice, renderKey)
        return slice
    }

    private suspend fun totalCharsLocked(): Int {
        if (totalCharsCache >= 0) return totalCharsCache
        totalCharsCache = store.totalChars().coerceAtLeast(0)
        return totalCharsCache
    }

    private suspend fun findReasonablePageStartLocked(
        targetChar: Int,
        constraints: LayoutConstraints
    ): Int {
        val total = totalCharsLocked()
        if (total <= 0) return 0
        val target = targetChar.coerceIn(0, total - 1)

        ensureRenderBucketLocked()
        val starts = pageStarts ?: return 0
        var start = starts.floor(target) ?: 0
        start = start.coerceIn(0, total - 1)

        repeat(32) {
            val slice = pager.pageAt(start, constraints, config)
            pageCache.put(slice, renderKey)
            starts.addStart(slice.startChar)
            if (slice.endChar in 1 until total) {
                starts.addStart(slice.endChar)
            }

            if (target < slice.endChar || slice.endChar >= total || slice.endChar <= start) {
                return slice.startChar
            }
            start = slice.endChar
        }
        return start
    }

    private suspend fun computePrevStartLocked(constraints: LayoutConstraints): Int {
        if (currentStartChar <= 0) return 0
        ensureRenderBucketLocked()
        val starts = pageStarts ?: return 0

        starts.floor(currentStartChar - 1)?.let { known ->
            if (known < currentStartChar) return known
        }

        val total = totalCharsLocked()
        val estimate = pager.estimateCharsPerPage(constraints, config)
        var probe = (currentStartChar - (estimate * 2)).coerceAtLeast(0)
        var previousStart = 0

        repeat(24) {
            val slice = pager.pageAt(probe, constraints, config)
            pageCache.put(slice, renderKey)
            starts.addStart(slice.startChar)
            if (slice.endChar in 1 until total) {
                starts.addStart(slice.endChar)
            }

            if (slice.endChar >= currentStartChar || slice.endChar <= probe) {
                return previousStart.coerceAtLeast(0)
            }
            previousStart = slice.startChar
            probe = slice.endChar
        }

        return (currentStartChar - estimate).coerceAtLeast(0)
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
            renderKey = key
            pageStarts = paginationStore.getOrCreate(key)
        }
        pageStarts?.seedIfEmpty(0, currentStartChar)
        charsPerPageEstimate = pager.estimateCharsPerPage(c, config)
    }

    private fun schedulePrefetch(n: Int) {
        val count = (if (n > 0) n else engineConfig.prefetchAhead).coerceIn(0, 8)
        if (count <= 0) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            runCatching { prefetchNeighbors(count) }
        }
    }

    private fun moveToStartLocked(destinationStart: Int) {
        if (destinationStart == currentStartChar) return
        navigationHistory.push(currentStartChar)
        currentStartChar = destinationStart
        currentSlice = null
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
}
