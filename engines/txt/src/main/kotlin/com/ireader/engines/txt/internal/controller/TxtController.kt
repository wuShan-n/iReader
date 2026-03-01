package com.ireader.engines.txt.internal.controller

import android.os.SystemClock
import com.ireader.engines.txt.internal.paging.PageSlice
import com.ireader.engines.txt.internal.paging.PaginationCache
import com.ireader.engines.txt.internal.paging.RenderKey
import com.ireader.engines.txt.internal.paging.TxtLastPositionStore
import com.ireader.engines.txt.internal.paging.TxtPager
import com.ireader.engines.txt.internal.paging.TxtPaginationStore
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.engines.txt.toReaderError
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
    initialStartChar: Int = 0
) : ReaderController {

    private val mutex = Mutex()
    private val cache = PaginationCache()
    private val historyStarts = ArrayDeque<Int>()

    private var constraints: LayoutConstraints? = null
    private var config: RenderConfig.ReflowText = RenderConfig.ReflowText()

    private var currentStartChar: Int = initialStartChar.coerceAtLeast(0)
    private var currentSlice: PageSlice? = null
    private var totalCharsCache: Int = -1

    private var renderKey: RenderKey? = null
    private var pageStarts = paginationStore.getOrCreate(
        RenderKey.placeholder(documentId.value, store.charset.name())
    )
    private var restoredForKey: RenderKey? = null
    private var charsPerPageEstimate: Int = 0
    private var lastSavedStartChar: Int = -1
    private var lastSavedAtMs: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var prefetchJob: Job? = null

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 64)
    override val events: Flow<ReaderEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(
        RenderState(
            locator = Locator(LocatorSchemes.TXT_OFFSET, currentStartChar.toString()),
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
            cache.clear()
            currentSlice = null

            val total = totalCharsLocked()
            currentStartChar = if (total == 0) 0 else currentStartChar.coerceIn(0, total - 1)
            ensureRenderBucketLocked()

            val key = renderKey
            if (!explicitInitial && key != null && restoredForKey != key) {
                restoredForKey = key
                val restoredStart = lastPositionStore.load(key)
                if (restoredStart != null && total > 0) {
                    currentStartChar = restoredStart.coerceIn(0, total - 1)
                    pageStarts.seedIfEmpty(0, currentStartChar)
                    pageStarts.addStart(currentStartChar)
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
            cache.clear()
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

            pushHistoryLocked(currentStartChar)
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

            if (currentStartChar <= 0 && historyStarts.isEmpty()) {
                return@withLock renderLocked(c, policy)
            }

            val previous = if (historyStarts.isNotEmpty()) {
                historyStarts.removeLast()
            } else {
                computePrevStartLocked(c)
            }

            currentStartChar = previous.coerceAtLeast(0)
            currentSlice = null
            renderLocked(c, policy)
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val target = parseTxtOffset(locator)
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("Unsupported locator: ${locator.scheme}"))

            val c = constraints
            if (c == null) {
                currentStartChar = target.coerceAtLeast(0)
                currentSlice = null
                return@withLock ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
            }

            val total = totalCharsLocked()
            if (total == 0) {
                currentStartChar = 0
                currentSlice = null
                return@withLock renderLocked(c, policy)
            }

            val clampedTarget = target.coerceIn(0, total - 1)
            val destinationStart = findReasonablePageStartLocked(clampedTarget, c)
            if (destinationStart != currentStartChar) {
                pushHistoryLocked(currentStartChar)
                currentStartChar = destinationStart
                currentSlice = null
            }
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
            if (destinationStart != currentStartChar) {
                pushHistoryLocked(currentStartChar)
                currentStartChar = destinationStart
                currentSlice = null
            }
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

            var probe = slice.startChar - 1
            repeat(count) {
                if (probe < 0) return@repeat
                val prevStart = pageStarts.floor(probe) ?: return@repeat
                if (prevStart >= slice.startChar) return@repeat
                prefetchAtLocked(prevStart, c)
                probe = prevStart - 1
            }
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            cache.clear()
            currentSlice = null
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        prefetchJob?.cancel()
        scope.cancel()
        renderKey?.let { key ->
            val start = currentSlice?.startChar ?: currentStartChar
            lastPositionStore.save(key, start, force = true)
            paginationStore.flush(key, pageStarts)
        }
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
            var newAdds = 0
            if (pageStarts.addStart(slice.startChar)) newAdds += 1
            if (slice.endChar in 1 until total) {
                if (pageStarts.addStart(slice.endChar)) newAdds += 1
            }
            renderKey?.let { key ->
                paginationStore.maybePersist(key, pageStarts, newAdds)
            }

            val locator = Locator(LocatorSchemes.TXT_OFFSET, slice.startChar.toString())
            val endLocator = Locator(LocatorSchemes.TXT_OFFSET, slice.endChar.toString())

            val estimate = charsPerPageEstimate.coerceAtLeast(1)
            val totalPagesEstimate = ((total + estimate - 1) / estimate).coerceAtLeast(1)
            val currentPage = (pageStarts.floorIndexOf(slice.startChar) + 1).coerceAtLeast(1)
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
                    mapping = TxtTextMapping(slice.startChar)
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
                    lastPositionStore.save(key, start)
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
            cache.get(startChar)?.let { return it to true }
        }

        val slice = pager.pageAt(startChar, constraints, config)
        if (policy.allowCache) {
            cache.put(slice)
        }
        return slice to false
    }

    private suspend fun prefetchAtLocked(
        startChar: Int,
        constraints: LayoutConstraints
    ): PageSlice? {
        cache.get(startChar)?.let { return it }
        val total = totalCharsLocked()
        if (startChar >= total) return null

        val slice = pager.pageAt(startChar, constraints, config)
        cache.put(slice)
        return slice
    }

    private suspend fun totalCharsLocked(): Int {
        if (totalCharsCache >= 0) return totalCharsCache
        totalCharsCache = store.totalChars().coerceAtLeast(0)
        return totalCharsCache
    }

    private fun parseTxtOffset(locator: Locator): Int? {
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) return null
        return locator.value.toIntOrNull()
    }

    private suspend fun findReasonablePageStartLocked(
        targetChar: Int,
        constraints: LayoutConstraints
    ): Int {
        val total = totalCharsLocked()
        if (total <= 0) return 0
        val target = targetChar.coerceIn(0, total - 1)

        ensureRenderBucketLocked()
        var start = pageStarts.floor(target) ?: 0
        start = start.coerceIn(0, total - 1)

        repeat(32) {
            val slice = pager.pageAt(start, constraints, config)
            cache.put(slice)
            pageStarts.addStart(slice.startChar)
            if (slice.endChar in 1 until total) {
                pageStarts.addStart(slice.endChar)
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

        pageStarts.floor(currentStartChar - 1)?.let { known ->
            if (known < currentStartChar) return known
        }

        val total = totalCharsLocked()
        val estimate = pager.estimateCharsPerPage(constraints, config)
        var probe = (currentStartChar - (estimate * 2)).coerceAtLeast(0)
        var previousStart = 0

        repeat(24) {
            val slice = pager.pageAt(probe, constraints, config)
            cache.put(slice)
            pageStarts.addStart(slice.startChar)
            if (slice.endChar in 1 until total) {
                pageStarts.addStart(slice.endChar)
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
        if (renderKey != key) {
            renderKey = key
            pageStarts = paginationStore.getOrCreate(key)
        }
        pageStarts.seedIfEmpty(0, currentStartChar)
        charsPerPageEstimate = pager.estimateCharsPerPage(c, config)
    }

    private fun schedulePrefetch(n: Int) {
        val count = n.coerceIn(0, 6)
        if (count <= 0) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            runCatching { prefetchNeighbors(count) }
        }
    }

    private fun pushHistoryLocked(startChar: Int) {
        val safe = startChar.coerceAtLeast(0)
        if (historyStarts.isNotEmpty() && historyStarts.last() == safe) return
        if (historyStarts.size >= 128) {
            historyStarts.removeFirst()
        }
        historyStarts.addLast(safe)
    }
}
