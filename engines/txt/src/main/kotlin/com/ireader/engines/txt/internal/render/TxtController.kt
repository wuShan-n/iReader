@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions"
)

package com.ireader.engines.txt.internal.render

import android.util.Log
import com.ireader.engines.common.android.controller.BaseCoroutineReaderController
import com.ireader.engines.common.android.reflow.ReflowPageSlice
import com.ireader.engines.common.android.reflow.ReflowPageSliceCache
import com.ireader.engines.common.android.reflow.ReflowPaginationIndexStore
import com.ireader.engines.common.android.reflow.ReflowPaginator
import com.ireader.engines.common.android.reflow.SOFT_BREAK_PROFILE_EXTRA_KEY
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.common.cache.LruCache
import com.ireader.engines.txt.internal.link.LinkDetector
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.provider.ChapterDetector
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.annotation.Decoration
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
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.Progression
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

private fun initialRenderState(
    initialOffset: Long,
    maxOffset: Long,
    config: RenderConfig.ReflowText
): RenderState {
    val safeMax = maxOffset.coerceAtLeast(0L)
    val start = initialOffset.coerceIn(0L, safeMax)
    val percent = if (safeMax == 0L) {
        0.0
    } else {
        start.toDouble() / safeMax.toDouble()
    }.coerceIn(0.0, 1.0)

    return RenderState(
        locator = TxtBlockLocatorCodec.locatorForOffset(
            offset = start,
            maxOffset = safeMax,
            extras = mapOf(LocatorExtraKeys.PROGRESSION to String.format(Locale.US, "%.6f", percent))
        ),
        progression = Progression(
            percent = percent,
            label = "${(percent * 100.0).roundToInt()}%"
        ),
        nav = NavigationAvailability(
            canGoPrev = start > 0L,
            canGoNext = start < safeMax
        ),
        config = config
    )
}

internal class TxtController(
    private val documentKey: String,
    private val store: Utf16TextStore,
    private val meta: TxtMeta,
    private val initialLocator: Locator?,
    initialOffset: Long,
    initialConfig: RenderConfig.ReflowText,
    maxPageCache: Int,
    persistPagination: Boolean,
    private val files: TxtBookFiles,
    private val annotationProvider: AnnotationProvider?,
    private val ioDispatcher: CoroutineDispatcher,
    defaultDispatcher: CoroutineDispatcher
) : BaseCoroutineReaderController(
    initialState = initialRenderState(
        initialOffset = initialOffset,
        maxOffset = store.lengthChars,
        config = initialConfig
    ),
    dispatcher = defaultDispatcher
) {

    private var softBreakProfile: SoftBreakTuningProfile = resolveSoftBreakProfile(initialConfig)
    private var softBreakIndex: SoftBreakIndex? = openSoftBreakIndex(softBreakProfile)
    private val paginator = ReflowPaginator(
        source = TxtTextSource(store),
        hardWrapLikely = meta.hardWrapLikely,
        softBreakIndex = softBreakIndex,
        pageEndAdjuster = TxtPageEndAdjuster(ChapterDetector())
    )
    private val sliceCache = ReflowPageSliceCache(
        paginator = paginator,
        maxPageCache = maxPageCache,
        maxOffsetProvider = { store.lengthChars }
    )
    private val paginationIndex = ReflowPaginationIndexStore(
        enabled = persistPagination,
        documentKey = documentKey,
        paginationDir = files.paginationDir
    )
    private val pageExtrasCache = LruCache<PageExtrasCacheKey, PageExtras>((maxPageCache * 3).coerceAtLeast(8))

    private var pageCompletionJob: Job? = null
    private var annotationObserverJob: Job? = null
    private var annotationRevision: Long = 0L
    private var restoredLocatorAnchors = false

    private val initialStart = initialOffset.coerceIn(0L, store.lengthChars)
    private val navigation = TxtNavigationState(initialStart)

    private var constraints: LayoutConstraints? = null
    private var currentConfig: RenderConfig.ReflowText = initialConfig

    init {
        if (softBreakIndex == null) {
            buildSoftBreakIndexAsync(softBreakProfile)
        }
        observeAnnotationChangesIfNeeded()
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
            pageExtrasCache.clear()
            reloadPaginationIndexIfNeededLocked()
            updateStateLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val reflow = config as? RenderConfig.ReflowText
            ?: return ReaderResult.Err(ReaderError.Internal("TXT requires ReflowText config"))
        val sanitized = reflow.sanitized()
        return mutex.withLock {
            currentConfig = sanitized
            stateMutable.value = stateMutable.value.copy(config = sanitized)
            val newProfile = resolveSoftBreakProfile(sanitized)
            val profileChanged = newProfile != softBreakProfile
            if (profileChanged) {
                softBreakProfile = newProfile
                runCatching { softBreakIndex?.close() }
                softBreakIndex = openSoftBreakIndex(newProfile)
                paginator.setSoftBreakIndex(softBreakIndex)
                if (softBreakIndex == null) {
                    buildSoftBreakIndexAsync(newProfile)
                }
            }
            sliceCache.clear()
            pageExtrasCache.clear()
            reloadPaginationIndexIfNeededLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        val renderResult = mutex.withLock {
            renderLocked(policy)
        }
        if (renderResult is ReaderResult.Ok && policy.prefetchNeighbors > 0) {
            launchSafely("prefetch-neighbors") { prefetchNeighbors(policy.prefetchNeighbors) }
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
                config = currentConfig,
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
                    config = currentConfig,
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
                config = currentConfig,
                allowCache = true
            )

            var forwardStart = currentSlice.endOffset
            repeat(count) {
                if (forwardStart >= store.lengthChars) return@repeat
                val next = sliceCache.getOrBuild(
                    start = forwardStart,
                    constraints = constraintsLocal,
                    config = currentConfig,
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
                        config = currentConfig,
                        allowCache = true
                    )
                }
                if (prevStart >= backwardStart) {
                    return@repeat
                }
                sliceCache.getOrBuild(
                    start = prevStart,
                    constraints = constraintsLocal,
                    config = currentConfig,
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
            pageExtrasCache.clear()
            if (reason == InvalidateReason.CONFIG_CHANGED || reason == InvalidateReason.LAYOUT_CHANGED) {
                paginationIndex.invalidateProfile()
                pageCompletionJob?.cancel()
            }
            ReaderResult.Ok(Unit)
        }
    }

    override fun onClose() {
        runCatching { paginationIndex.saveIfDirty() }
            .onFailure { Log.w(TAG, "TXT controller failed to save pagination index", it) }
        pageCompletionJob?.cancel()
        annotationObserverJob?.cancel()
        runCatching { softBreakIndex?.close() }
            .onFailure { Log.w(TAG, "TXT controller failed to close soft-break index", it) }
    }

    private fun resolveSoftBreakProfile(config: RenderConfig.ReflowText): SoftBreakTuningProfile {
        return SoftBreakTuningProfile.fromStorageValue(config.extra[SOFT_BREAK_PROFILE_EXTRA_KEY])
    }

    private fun openSoftBreakIndex(profile: SoftBreakTuningProfile): SoftBreakIndex? {
        val ruleConfig = SoftBreakRuleConfig.forProfile(profile)
        return SoftBreakIndex.openIfValid(
            file = files.softBreakIdx,
            meta = meta,
            profile = profile,
            rulesVersion = ruleConfig.rulesVersion
        )
    }

    private fun buildSoftBreakIndexAsync(profile: SoftBreakTuningProfile) {
        launchSafely("soft-break-index") {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = ioDispatcher,
                profile = profile
            )
            val loaded = openSoftBreakIndex(profile)
            mutex.withLock {
                if (profile != softBreakProfile) {
                    runCatching { loaded?.close() }
                    return@withLock
                }
                softBreakIndex?.close()
                softBreakIndex = loaded
                paginator.setSoftBreakIndex(loaded)
                sliceCache.clear()
                pageExtrasCache.clear()
            }
        }
    }

    private suspend fun renderLocked(policy: RenderPolicy): ReaderResult<RenderPage> {
        val constraintsLocal = constraints
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        var builtSlice: ReflowPageSlice? = null
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
                    config = currentConfig,
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
        slice: ReflowPageSlice,
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
        val extras = pageExtrasFor(
            startOffset = slice.startOffset,
            endOffset = slice.endOffset,
            text = slice.text,
            range = pageRange
        )

        val page = RenderPage(
            id = PageId("${slice.startOffset}-${slice.endOffset}"),
            locator = navigation.locatorFor(store.lengthChars),
            content = RenderContent.Text(
                text = slice.text,
                mapping = TxtTextMapping(slice.startOffset, slice.endOffset)
            ),
            links = extras.links,
            decorations = extras.decorations,
            metrics = RenderMetrics(
                renderTimeMs = renderTimeMs,
                cacheHit = cacheHit
            )
        )
        eventsMutable.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        eventsMutable.tryEmit(ReaderEvent.PageChanged(page.locator))
        return ReaderResult.Ok(page)
    }

    private suspend fun pageExtrasFor(
        startOffset: Long,
        endOffset: Long,
        text: CharSequence,
        range: LocatorRange
    ): PageExtras {
        val key = PageExtrasCacheKey(
            startOffset = startOffset,
            endOffset = endOffset,
            annotationRevision = annotationRevision
        )
        val cached = pageExtrasCache[key]
        if (cached != null) {
            return cached
        }
        val computed = PageExtras(
            links = LinkDetector.detect(
                text = text,
                pageStartOffset = startOffset,
                maxOffset = store.lengthChars
            ),
            decorations = annotationProvider
                ?.decorationsFor(AnnotationQuery(range = range))
                ?.getOrNull()
                ?: emptyList()
        )
        pageExtrasCache[key] = computed
        return computed
    }

    private fun updateStateLocked() {
        val locatorExtras = paginationIndex.locatorExtras()
        stateMutable.value = stateMutable.value.copy(
            locator = navigation.locatorFor(store.lengthChars, extras = locatorExtras),
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
            profileConfig = currentConfig
        )
        if (!restoredLocatorAnchors) {
            paginationIndex.mergeLocatorAnchors(initialLocator?.extras.orEmpty())
            restoredLocatorAnchors = true
        }
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
        pageCompletionJob = launchSafely("page-map-completion") {
            completePageMapForward(
                constraints = constraintsLocal,
                maxPages = 24,
                batchSize = PAGE_COMPLETION_BATCH_SIZE
            )
        }
    }

    private suspend fun completePageMapForward(
        constraints: LayoutConstraints,
        maxPages: Int,
        batchSize: Int
    ) {
        if (maxPages <= 0 || store.lengthChars <= 0L) {
            return
        }
        var remaining = maxPages
        var cursor = mutex.withLock {
            (paginationIndex.lastKnownStart() ?: navigation.currentStart).coerceIn(0L, store.lengthChars)
        }
        while (remaining > 0 && cursor < store.lengthChars) {
            val batchResult = mutex.withLock {
                completePageMapBatchLocked(
                    constraints = constraints,
                    startCursor = cursor,
                    maxPages = minOf(batchSize, remaining)
                )
            }
            cursor = batchResult.nextCursor
            if (batchResult.steps <= 0) {
                break
            }
            remaining -= batchResult.steps
        }
        mutex.withLock {
            paginationIndex.saveIfDirty()
        }
    }

    private suspend fun completePageMapBatchLocked(
        constraints: LayoutConstraints,
        startCursor: Long,
        maxPages: Int
    ): PageCompletionBatchResult {
        if (maxPages <= 0 || startCursor >= store.lengthChars) {
            return PageCompletionBatchResult(nextCursor = startCursor, steps = 0)
        }
        var cursor = startCursor.coerceIn(0L, store.lengthChars)
        var steps = 0
        while (cursor < store.lengthChars && steps < maxPages) {
            val slice = sliceCache.getOrBuild(
                start = cursor,
                constraints = constraints,
                config = currentConfig,
                allowCache = true
            )
            paginationIndex.record(slice.startOffset)
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            steps++
        }
        return PageCompletionBatchResult(nextCursor = cursor, steps = steps)
    }

    private fun observeAnnotationChangesIfNeeded() {
        val provider = annotationProvider ?: return
        annotationObserverJob = launchSafely("observe-annotations") {
            provider.observeAll().collect {
                mutex.withLock {
                    annotationRevision++
                    pageExtrasCache.clear()
                }
            }
        }
    }

    override fun onCoroutineError(name: String, throwable: Throwable) {
        Log.w(TAG, "TXT controller background task failed: $name", throwable)
    }

    private data class PageCompletionBatchResult(
        val nextCursor: Long,
        val steps: Int
    )

    private data class PageExtrasCacheKey(
        val startOffset: Long,
        val endOffset: Long,
        val annotationRevision: Long
    )

    private data class PageExtras(
        val links: List<DocumentLink>,
        val decorations: List<Decoration>
    )

    private companion object {
        private const val TAG = "TxtController"
        private const val PAGE_COMPLETION_BATCH_SIZE = 4
    }
}
