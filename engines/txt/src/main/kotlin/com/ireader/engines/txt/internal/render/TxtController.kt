@file:Suppress("LongParameterList", "TooManyFunctions")

package com.ireader.engines.txt.internal.render

import android.util.Log
import com.ireader.engines.common.android.controller.BaseCoroutineReaderController
import com.ireader.engines.common.cache.LruCache
import com.ireader.engines.txt.internal.link.LinkDetector
import com.ireader.engines.txt.internal.locator.TxtAnchorLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.runtime.BreakResolver
import com.ireader.engines.txt.internal.softbreak.BreakMapState
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
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
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.Progression
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

private fun initialRenderState(
    initialOffset: Long,
    maxOffset: Long,
    config: RenderConfig.ReflowText,
    blockIndex: TxtBlockIndex,
    revision: Int
): RenderState {
    val safeMax = maxOffset.coerceAtLeast(0L)
    val start = initialOffset.coerceIn(0L, safeMax)
    val percent = if (safeMax == 0L) {
        0.0
    } else {
        start.toDouble() / safeMax.toDouble()
    }.coerceIn(0.0, 1.0)

    return RenderState(
        locator = TxtAnchorLocatorCodec.locatorForOffset(
            offset = start,
            blockIndex = blockIndex,
            revision = revision,
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
    documentKey: String,
    private val store: Utf16TextStore,
    private val meta: TxtMeta,
    private val blockIndex: TxtBlockIndex,
    private val breakResolver: BreakResolver,
    private val blockStore: BlockStore,
    private val initialLocator: Locator?,
    initialOffset: Long,
    initialConfig: RenderConfig.ReflowText,
    maxPageCache: Int,
    persistPagination: Boolean,
    files: TxtBookFiles,
    private val annotationProvider: AnnotationProvider?,
    private val ioDispatcher: CoroutineDispatcher,
    private val paginationDispatcher: CoroutineDispatcher,
    defaultDispatcher: CoroutineDispatcher
) : BaseCoroutineReaderController(
    initialState = initialRenderState(
        initialOffset = initialOffset,
        maxOffset = store.lengthCodeUnits,
        config = initialConfig,
        blockIndex = blockIndex,
        revision = meta.contentRevision
    ),
    dispatcher = defaultDispatcher
) {
    private val pagination = PaginationCoordinator(
        documentKey = documentKey,
        store = store,
        blockStore = blockStore,
        breakResolver = breakResolver,
        maxPageCache = maxPageCache,
        persistPagination = persistPagination,
        files = files,
        paginationDispatcher = paginationDispatcher
    ).apply {
        bindInitialConfig(initialConfig)
    }

    private val pageLinksCache = LruCache<PageRangeKey, List<DocumentLink>>((maxPageCache * 3).coerceAtLeast(8))
    private val pageDecorCache = LruCache<DecorKey, List<Decoration>>((maxPageCache * 3).coerceAtLeast(8))

    private var paginationGeneration: Long = 0L
    private var prefetchJob: Job? = null
    private var pageCompletionJob: Job? = null
    private var annotationObserverJob: Job? = null
    private var annotationRevision: Long = 0L
    private var hasAnyAnnotations: Boolean = false
    private var textLayouterFactoryKey: String? = null

    private val initialStart = initialOffset.coerceIn(0L, store.lengthCodeUnits)
    private val navigation = TxtNavigationState(
        initialStart = initialStart,
        blockIndex = blockIndex,
        revision = meta.contentRevision
    )

    private var constraints: LayoutConstraints? = null
    private var currentConfig: RenderConfig.ReflowText = initialConfig

    init {
        observeAnnotationChangesIfNeeded()
    }

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setTextLayouterFactory(factory: TextLayouterFactory): ReaderResult<Unit> {
        return guardControllerCall("setTextLayouterFactory") {
            mutex.withLock {
                if (textLayouterFactoryKey == factory.environmentKey) {
                    return@withLock ReaderResult.Ok(Unit)
                }
                textLayouterFactoryKey = factory.environmentKey
                pagination.setTextLayouterFactory(factory)
                invalidatePaginationLocked()
                ReaderResult.Ok(Unit)
            }
        }
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return guardControllerCall("setLayoutConstraints") {
            mutex.withLock {
                this.constraints = constraints
                pagination.setLayoutConstraints(constraints)
                invalidatePaginationLocked()
                updateStateLocked()
                ReaderResult.Ok(Unit)
            }
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val reflow = config as? RenderConfig.ReflowText
            ?: return ReaderResult.Err(ReaderError.Internal("TXT requires ReflowText config"))
        val sanitized = reflow.sanitized()
        return guardControllerCall("setConfig") {
            mutex.withLock {
                currentConfig = sanitized
                stateMutable.value = stateMutable.value.copy(config = sanitized)
                pagination.setConfig(sanitized)
                invalidatePaginationLocked()
                ReaderResult.Ok(Unit)
            }
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        return guardControllerCall("render") {
            val snapshotResult = mutex.withLock { renderSnapshotLocked(policy) }
            val renderResult = when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
            if (renderResult is ReaderResult.Ok && policy.prefetchNeighbors > 0) {
                mutex.withLock {
                    schedulePrefetchLocked(policy.prefetchNeighbors)
                }
            }
            renderResult
        }
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return guardControllerCall("next") {
            if (textLayouterFactoryKey == null) {
                return@guardControllerCall ReaderResult.Err(ReaderError.Internal("TXT text layouter not set"))
            }
            val snapshotResult = mutex.withLock {
                val current = pagination.pageAt(
                    startOffset = navigation.currentStart,
                    allowCache = true
                ).slice
                if (current.endOffset >= store.lengthCodeUnits) {
                    return@withLock prepareRenderSnapshotLocked(
                        slice = current,
                        renderTimeMs = 0L,
                        cacheHit = true
                    )
                }
                navigation.moveTo(current.endOffset, store.lengthCodeUnits)
                renderSnapshotLocked(policy)
            }
            when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return guardControllerCall("prev") {
            if (textLayouterFactoryKey == null) {
                return@guardControllerCall ReaderResult.Err(ReaderError.Internal("TXT text layouter not set"))
            }
            val snapshotResult = mutex.withLock {
                if (!navigation.canGoPrev()) {
                    return@withLock renderSnapshotLocked(policy)
                }
                val target = pagination.previousStart(navigation.currentStart)
                navigation.moveTo(target, store.lengthCodeUnits)
                renderSnapshotLocked(policy)
            }
            when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return guardControllerCall("goTo") {
            val offset = TxtAnchorLocatorCodec.parseOffset(
                locator = locator,
                blockIndex = blockIndex,
                expectedRevision = meta.contentRevision,
                maxOffset = store.lengthCodeUnits
            ) ?: return@guardControllerCall ReaderResult.Err(
                ReaderError.Internal("Unsupported TXT locator: ${locator.scheme}:${locator.value}")
            )
            val snapshotResult = mutex.withLock {
                navigation.moveTo(offset, store.lengthCodeUnits)
                renderSnapshotLocked(policy)
            }
            when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        return guardControllerCall("goToProgress") {
            val clamped = percent.coerceIn(0.0, 1.0)
            val snapshotResult = mutex.withLock {
                val target = pagination.startForProgress(clamped)
                navigation.moveTo(target, store.lengthCodeUnits)
                renderSnapshotLocked(policy)
            }
            when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
        }
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        return guardControllerCall("prefetchNeighbors") {
            if (count <= 0 || textLayouterFactoryKey == null) {
                return@guardControllerCall ReaderResult.Ok(Unit)
            }
            val expectedGeneration = mutex.withLock { paginationGeneration }
            prefetchNeighbors(count = count, expectedGeneration = expectedGeneration)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return guardControllerCall("invalidate") {
            mutex.withLock {
                invalidatePaginationLocked()
                ReaderResult.Ok(Unit)
            }
        }
    }

    override fun onClose() {
        prefetchJob?.cancel()
        pageCompletionJob?.cancel()
        annotationObserverJob?.cancel()
        pagination.close()
    }

    suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage> {
        return guardControllerCall("applyBreakPatch") {
            val offset = TxtAnchorLocatorCodec.parseOffset(
                locator = locator,
                blockIndex = blockIndex,
                expectedRevision = meta.contentRevision,
                maxOffset = store.lengthCodeUnits
            ) ?: return@guardControllerCall ReaderResult.Err(
                ReaderError.Internal("Unsupported TXT locator: ${locator.scheme}:${locator.value}")
            )
            val snapshotResult = mutex.withLock {
                val newlineOffset = findNearestNewlineOffset(
                    fromOffset = offset,
                    direction = direction
                ) ?: return@withLock ReaderResult.Err(
                    ReaderError.Internal("No newline found near the current TXT anchor")
                )
                breakResolver.patch(newlineOffset, state.toBreakMapState())
                invalidateBreakProjectionLocked()
                renderSnapshotLocked(RenderPolicy.Default)
            }
            when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
        }
    }

    suspend fun clearBreakPatches(): ReaderResult<RenderPage> {
        return guardControllerCall("clearBreakPatches") {
            val snapshotResult = mutex.withLock {
                breakResolver.clearPatches()
                invalidateBreakProjectionLocked()
                renderSnapshotLocked(RenderPolicy.Default)
            }
            when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
        }
    }

    private suspend fun renderSnapshotLocked(policy: RenderPolicy): ReaderResult<RenderSnapshot> {
        val constraintsLocal = constraints
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
        if (textLayouterFactoryKey == null) {
            return ReaderResult.Err(ReaderError.Internal("TXT text layouter not set"))
        }

        var lookup: PaginationCoordinator.PageLookup? = null
        val elapsed = measureTimeMillis {
            lookup = pagination.pageAt(
                startOffset = navigation.currentStart,
                allowCache = policy.allowCache
            )
        }
        val pageLookup = lookup ?: return ReaderResult.Err(ReaderError.Internal("Failed to paginate TXT page"))
        val finalSlice = pageLookup.slice
        val snapshot = prepareRenderSnapshotLocked(
            slice = finalSlice,
            renderTimeMs = elapsed,
            cacheHit = pageLookup.cacheHit
        )
        if (snapshot is ReaderResult.Ok) {
            maybeSchedulePageCompletionLocked()
        }
        return snapshot
    }

    private fun prepareRenderSnapshotLocked(
        slice: TxtPageSlice,
        renderTimeMs: Long,
        cacheHit: Boolean
    ): ReaderResult<RenderSnapshot> {
        navigation.updateFromSlice(
            startOffset = slice.startOffset,
            endOffset = slice.endOffset
        )
        updateStateLocked()
        val pageRange = TxtAnchorLocatorCodec.rangeForOffsets(
            startOffset = slice.startOffset,
            endOffset = slice.endOffset,
            blockIndex = blockIndex,
            revision = meta.contentRevision
        )
        return ReaderResult.Ok(
            RenderSnapshot(
                id = PageId("${slice.startOffset}-${slice.endOffset}"),
                locator = navigation.locatorFor(store.lengthCodeUnits),
                text = slice.text,
                startOffset = slice.startOffset,
                endOffset = slice.endOffset,
                continuesParagraph = slice.continuesParagraph,
                range = pageRange,
                renderTimeMs = renderTimeMs,
                cacheHit = cacheHit,
                projectedBoundaryToRawOffsets = slice.projectedBoundaryToRawOffsets
            )
        )
    }

    private suspend fun buildPageResult(snapshot: RenderSnapshot): ReaderResult<RenderPage> {
        val extras = pageExtrasFor(
            startOffset = snapshot.startOffset,
            endOffset = snapshot.endOffset,
            text = snapshot.text,
            range = snapshot.range,
            projectedBoundaryToRawOffsets = snapshot.projectedBoundaryToRawOffsets
        )
        val page = RenderPage(
            id = snapshot.id,
            locator = snapshot.locator,
            content = RenderContent.Text(
                text = snapshot.text,
                mapping = TxtTextMapping(
                    pageStart = snapshot.startOffset,
                    pageEnd = snapshot.endOffset,
                    blockIndex = blockIndex,
                    revision = meta.contentRevision,
                    projectedBoundaryToRawOffsets = snapshot.projectedBoundaryToRawOffsets
                ),
                justifyVisibleLastLine = snapshot.continuesParagraph
            ),
            links = extras.links,
            decorations = extras.decorations,
            metrics = RenderMetrics(
                renderTimeMs = snapshot.renderTimeMs,
                cacheHit = snapshot.cacheHit
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
        range: LocatorRange,
        projectedBoundaryToRawOffsets: LongArray
    ): PageExtras {
        val links = linksFor(
            startOffset = startOffset,
            endOffset = endOffset,
            text = text,
            projectedBoundaryToRawOffsets = projectedBoundaryToRawOffsets
        )
        val decorations = decorationsFor(
            startOffset = startOffset,
            endOffset = endOffset,
            range = range
        )
        return PageExtras(links = links, decorations = decorations)
    }

    private suspend fun linksFor(
        startOffset: Long,
        endOffset: Long,
        text: CharSequence,
        projectedBoundaryToRawOffsets: LongArray
    ): List<DocumentLink> {
        val key = PageRangeKey(startOffset = startOffset, endOffset = endOffset)
        mutex.withLock {
            pageLinksCache[key]?.let { return it }
        }
        val detected = LinkDetector.detect(
            text = text,
            pageStartOffset = startOffset,
            blockIndex = blockIndex,
            revision = meta.contentRevision,
            projectedBoundaryToRawOffsets = projectedBoundaryToRawOffsets
        )
        return mutex.withLock {
            pageLinksCache[key] ?: detected.also { pageLinksCache[key] = it }
        }
    }

    private suspend fun decorationsFor(
        startOffset: Long,
        endOffset: Long,
        range: LocatorRange
    ): List<Decoration> {
        val provider = annotationProvider ?: return emptyList()
        val lookup = mutex.withLock {
            if (!hasAnyAnnotations) {
                return@withLock DecorLookup(
                    key = null,
                    revision = annotationRevision,
                    cached = emptyList(),
                    shouldQuery = false
                )
            }
            val revision = annotationRevision
            val key = DecorKey(startOffset = startOffset, endOffset = endOffset, rev = revision)
            DecorLookup(
                key = key,
                revision = revision,
                cached = pageDecorCache[key],
                shouldQuery = true
            )
        }
        lookup.cached?.let { return it }
        if (!lookup.shouldQuery || lookup.key == null) {
            return emptyList()
        }

        val queried = provider
            .decorationsFor(AnnotationQuery(range = range))
            .getOrNull()
            ?: emptyList()
        return mutex.withLock {
            val latestRevision = annotationRevision
            if (latestRevision != lookup.revision) {
                val latestKey = DecorKey(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    rev = latestRevision
                )
                pageDecorCache[latestKey] ?: queried
            } else {
                pageDecorCache[lookup.key] = queried
                queried
            }
        }
    }

    private fun updateStateLocked() {
        stateMutable.value = stateMutable.value.copy(
            locator = navigation.locatorFor(store.lengthCodeUnits),
            progression = navigation.progressionFor(store.lengthCodeUnits),
            nav = NavigationAvailability(
                canGoPrev = navigation.canGoPrev(),
                canGoNext = navigation.canGoNext(store.lengthCodeUnits)
            ),
            config = currentConfig
        )
    }

    private fun maybeSchedulePageCompletionLocked() {
        if (pageCompletionJob?.isActive == true) {
            return
        }
        val expectedGeneration = paginationGeneration
        val start = navigation.currentStart
        pageCompletionJob = launchSafely("page-checkpoint-warmup") {
            if (isPaginationGenerationCurrentLocked(expectedGeneration)) {
                pagination.warmForward(fromStart = start, maxPages = 6)
            }
        }
    }

    private fun observeAnnotationChangesIfNeeded() {
        val provider = annotationProvider ?: return
        annotationObserverJob = launchSafely("observe-annotations") {
            provider.observeAll().collect { list ->
                mutex.withLock {
                    annotationRevision++
                    hasAnyAnnotations = list.isNotEmpty()
                    pageDecorCache.clear()
                }
            }
        }
    }

    private fun invalidatePaginationLocked() {
        paginationGeneration++
        cancelPaginationJobsLocked()
        pagination.invalidate()
        pageLinksCache.clear()
        pageDecorCache.clear()
    }

    private fun invalidateBreakProjectionLocked() {
        paginationGeneration++
        cancelPaginationJobsLocked()
        pagination.invalidateProjectedContent()
        pageLinksCache.clear()
        pageDecorCache.clear()
    }

    private fun findNearestNewlineOffset(
        fromOffset: Long,
        direction: TextBreakPatchDirection
    ): Long? {
        if (store.lengthCodeUnits <= 0L) {
            return null
        }
        return when (direction) {
            TextBreakPatchDirection.NEXT -> findNextNewlineOffset(fromOffset)
            TextBreakPatchDirection.PREVIOUS -> findPreviousNewlineOffset(fromOffset)
        }
    }

    private fun findNextNewlineOffset(fromOffset: Long): Long? {
        var blockId = blockIndex.blockIdForOffset(fromOffset)
        while (blockId < blockIndex.blockCount) {
            val blockStart = blockIndex.blockStartOffset(blockId)
            val blockEnd = blockIndex.blockEndOffset(blockId)
            val localStart = if (blockId == blockIndex.blockIdForOffset(fromOffset)) {
                fromOffset.coerceIn(blockStart, blockEnd)
            } else {
                blockStart
            }
            val raw = store.readString(localStart, (blockEnd - localStart).toInt().coerceAtLeast(0))
            val newlineIndex = raw.indexOf('\n')
            if (newlineIndex >= 0) {
                return localStart + newlineIndex.toLong()
            }
            blockId++
        }
        return null
    }

    private fun findPreviousNewlineOffset(fromOffset: Long): Long? {
        var blockId = blockIndex.blockIdForOffset(fromOffset)
        while (blockId >= 0) {
            val blockStart = blockIndex.blockStartOffset(blockId)
            val blockEnd = blockIndex.blockEndOffset(blockId)
            val localEndExclusive = if (blockId == blockIndex.blockIdForOffset(fromOffset)) {
                fromOffset.coerceIn(blockStart, blockEnd)
            } else {
                blockEnd
            }
            val raw = store.readString(blockStart, (localEndExclusive - blockStart).toInt().coerceAtLeast(0))
            val newlineIndex = raw.lastIndexOf('\n')
            if (newlineIndex >= 0) {
                return blockStart + newlineIndex.toLong()
            }
            blockId--
        }
        return null
    }

    private fun cancelPaginationJobsLocked() {
        prefetchJob?.cancel()
        prefetchJob = null
        pageCompletionJob?.cancel()
        pageCompletionJob = null
    }

    private fun isPaginationGenerationCurrentLocked(expectedGeneration: Long): Boolean {
        return paginationGeneration == expectedGeneration
    }

    private fun schedulePrefetchLocked(count: Int) {
        if (count <= 0 || textLayouterFactoryKey == null) {
            return
        }
        prefetchJob?.cancel()
        val expectedGeneration = paginationGeneration
        val start = navigation.currentStart
        prefetchJob = launchSafely("prefetch-neighbors") {
            if (isPaginationGenerationCurrentLocked(expectedGeneration)) {
                pagination.prefetchAround(currentStart = start, count = count)
            }
        }
    }

    private suspend fun prefetchNeighbors(
        count: Int,
        expectedGeneration: Long
    ): ReaderResult<Unit> {
        if (count <= 0) {
            return ReaderResult.Ok(Unit)
        }
        return withContext(ioDispatcher) {
            mutex.withLock {
                if (!isPaginationGenerationCurrentLocked(expectedGeneration) || textLayouterFactoryKey == null) {
                    return@withLock ReaderResult.Ok(Unit)
                }
                val start = navigation.currentStart
                pagination.prefetchAround(currentStart = start, count = count)
                ReaderResult.Ok(Unit)
            }
        }
    }

    private suspend fun <T> guardControllerCall(
        name: String,
        block: suspend () -> ReaderResult<T>
    ): ReaderResult<T> {
        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            logWarn(TAG, "TXT controller call failed: $name", t)
            ReaderResult.Err(
                ReaderError.Internal(
                    t.message?.takeIf(String::isNotBlank)
                        ?: "TXT controller call failed: $name"
                )
            )
        }
    }

    override fun onCoroutineError(name: String, throwable: Throwable) {
        logWarn(TAG, "TXT controller background task failed: $name", throwable)
    }

    private data class RenderSnapshot(
        val id: PageId,
        val locator: Locator,
        val text: CharSequence,
        val startOffset: Long,
        val endOffset: Long,
        val continuesParagraph: Boolean,
        val range: LocatorRange,
        val renderTimeMs: Long,
        val cacheHit: Boolean,
        val projectedBoundaryToRawOffsets: LongArray
    )

    private data class PageRangeKey(
        val startOffset: Long,
        val endOffset: Long
    )

    private data class DecorKey(
        val startOffset: Long,
        val endOffset: Long,
        val rev: Long
    )

    private data class DecorLookup(
        val key: DecorKey?,
        val revision: Long,
        val cached: List<Decoration>?,
        val shouldQuery: Boolean
    )

    private data class PageExtras(
        val links: List<DocumentLink>,
        val decorations: List<Decoration>
    )

    private companion object {
        private const val TAG = "TxtController"

        private fun logWarn(tag: String, message: String, throwable: Throwable?) {
            runCatching {
                if (throwable == null) {
                    Log.w(tag, message)
                } else {
                    Log.w(tag, message, throwable)
                }
            }
        }

        private fun TextBreakPatchState.toBreakMapState(): BreakMapState {
            return when (this) {
                TextBreakPatchState.HARD_PARAGRAPH -> BreakMapState.HARD_PARAGRAPH
                TextBreakPatchState.SOFT_JOIN -> BreakMapState.SOFT_JOIN
                TextBreakPatchState.SOFT_SPACE -> BreakMapState.SOFT_SPACE
                TextBreakPatchState.PRESERVE -> BreakMapState.PRESERVE
                TextBreakPatchState.UNKNOWN -> BreakMapState.UNKNOWN
            }
        }
    }
}
