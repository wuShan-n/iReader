@file:Suppress("LongParameterList", "TooManyFunctions")

package com.ireader.engines.txt.internal.render

import android.util.Log
import com.ireader.engines.common.android.controller.BaseCoroutineReaderController
import com.ireader.engines.txt.internal.locator.TxtLocatorResolver
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.engines.txt.internal.softbreak.BreakMapState
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
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
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.Progression
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

private fun initialRenderState(
    initialOffset: Long,
    maxOffset: Long,
    config: RenderConfig.ReflowText,
    blockIndex: TxtBlockIndex,
    contentFingerprint: String,
    projectionEngine: TextProjectionEngine
): RenderState {
    val safeMax = maxOffset.coerceAtLeast(0L)
    val start = initialOffset.coerceIn(0L, safeMax)
    val percent = if (safeMax == 0L) {
        0.0
    } else {
        start.toDouble() / safeMax.toDouble()
    }.coerceIn(0.0, 1.0)

    return RenderState(
        locator = TxtLocatorResolver.locatorForOffset(
            offset = start,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            maxOffset = maxOffset,
            projectionEngine = projectionEngine,
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
    private val projectionEngine: TextProjectionEngine,
    private val blockStore: BlockStore,
    initialOffset: Long,
    initialConfig: RenderConfig.ReflowText,
    maxPageCache: Int,
    persistPagination: Boolean,
    private val files: TxtBookFiles,
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
        contentFingerprint = meta.contentFingerprint,
        projectionEngine = projectionEngine
    ),
    dispatcher = defaultDispatcher
) {
    private val pagination = TxtPaginationService(
        documentKey = documentKey,
        store = store,
        blockStore = blockStore,
        projectionEngine = projectionEngine,
        maxPageCache = maxPageCache,
        persistPagination = persistPagination,
        files = files,
        initialConfig = initialConfig,
        ioDispatcher = ioDispatcher,
        paginationDispatcher = paginationDispatcher,
        launchTask = ::launchSafely
    )

    private val initialStart = initialOffset.coerceIn(0L, store.lengthCodeUnits)
    private val navigation = TxtNavigationService(
        initialStart = initialStart,
        blockIndex = blockIndex,
        contentFingerprint = meta.contentFingerprint,
        projectionEngine = projectionEngine,
        maxOffset = store.lengthCodeUnits,
        projectionVersionProvider = ::projectionVersion
    )
    private val pageExtras = TxtPageExtrasService(
        maxPageCache = maxPageCache,
        blockIndex = blockIndex,
        contentFingerprint = meta.contentFingerprint,
        projectionEngine = projectionEngine,
        annotationProvider = annotationProvider,
        launchTask = ::launchSafely
    )

    private var constraints: LayoutConstraints? = null
    private var currentConfig: RenderConfig.ReflowText = initialConfig

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setTextLayouterFactory(factory: TextLayouterFactory): ReaderResult<Unit> {
        return guardControllerCall("setTextLayouterFactory") {
            mutex.withLock {
                pagination.setTextLayouterFactory(factory)
                pageExtras.invalidate()
                ReaderResult.Ok(Unit)
            }
        }
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return guardControllerCall("setLayoutConstraints") {
            mutex.withLock {
                this.constraints = constraints
                pagination.setLayoutConstraints(constraints)
                pageExtras.invalidate()
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
                pageExtras.invalidate()
                ReaderResult.Ok(Unit)
            }
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        return guardControllerCall("render") {
            val snapshotResult = mutex.withLock {
                renderSnapshotLocked(policy)
            }
            when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
        }
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return guardControllerCall("next") {
            if (!pagination.hasTextLayouterFactory()) {
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
                navigation.moveTo(current.endOffset)
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
            if (!pagination.hasTextLayouterFactory()) {
                return@guardControllerCall ReaderResult.Err(ReaderError.Internal("TXT text layouter not set"))
            }
            val snapshotResult = mutex.withLock {
                if (!navigation.canGoPrev()) {
                    return@withLock renderSnapshotLocked(policy)
                }
                val target = pagination.previousStart(navigation.currentStart)
                navigation.moveTo(target)
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
            val offset = navigation.moveToLocator(locator) ?: return@guardControllerCall ReaderResult.Err(
                ReaderError.Internal("Unsupported TXT locator: ${locator.scheme}:${locator.value}")
            )
            val snapshotResult = mutex.withLock {
                navigation.moveTo(offset)
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
                navigation.moveTo(target)
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
            if (count <= 0 || !pagination.hasTextLayouterFactory()) {
                return@guardControllerCall ReaderResult.Ok(Unit)
            }
            pagination.prefetchNeighbors(
                count = count,
                currentStart = mutex.withLock { navigation.currentStart }
            )
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return guardControllerCall("invalidate") {
            mutex.withLock {
                pagination.invalidate()
                pageExtras.invalidate()
                ReaderResult.Ok(Unit)
            }
        }
    }

    override fun onClose() {
        pageExtras.close()
        pagination.close()
    }

    suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage> {
        return guardControllerCall("applyBreakPatch") {
            val offset = navigation.moveToLocator(locator) ?: return@guardControllerCall ReaderResult.Err(
                ReaderError.Internal("Unsupported TXT locator: ${locator.scheme}:${locator.value}")
            )
            val snapshotResult = mutex.withLock {
                val newlineOffset = findNearestNewlineOffset(
                    fromOffset = offset,
                    direction = direction
                ) ?: return@withLock ReaderResult.Err(
                    ReaderError.Internal("No newline found near the current TXT anchor")
                )
                projectionEngine.patch(newlineOffset, state.toBreakMapState())
                pagination.invalidateProjectedContent()
                pageExtras.invalidate()
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
                projectionEngine.clearPatches()
                pagination.invalidateProjectedContent()
                pageExtras.invalidate()
                renderSnapshotLocked(RenderPolicy.Default)
            }
            when (snapshotResult) {
                is ReaderResult.Err -> snapshotResult
                is ReaderResult.Ok -> buildPageResult(snapshotResult.value)
            }
        }
    }

    private suspend fun renderSnapshotLocked(policy: RenderPolicy): ReaderResult<RenderSnapshot> {
        if (constraints == null) {
            return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
        }
        if (!pagination.hasTextLayouterFactory()) {
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
            pagination.onPageRendered(
                currentStart = navigation.currentStart,
                policy = policy
            )
        }
        return snapshot
    }

    private fun prepareRenderSnapshotLocked(
        slice: TxtPageSlice,
        renderTimeMs: Long,
        cacheHit: Boolean
    ): ReaderResult<RenderSnapshot> {
        navigation.updateFromSlice(slice)
        updateStateLocked()
        val pageRange = navigation.rangeForSlice(slice)
        return ReaderResult.Ok(
            RenderSnapshot(
                id = PageId("${slice.startOffset}-${slice.endOffset}"),
                locator = navigation.locator(),
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
        val extras = pageExtras.pageExtrasFor(
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
                    contentFingerprint = meta.contentFingerprint,
                    projectionEngine = projectionEngine,
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

    private fun updateStateLocked() {
        stateMutable.value = stateMutable.value.copy(
            locator = navigation.locator(),
            progression = navigation.progression(),
            nav = NavigationAvailability(
                canGoPrev = navigation.canGoPrev(),
                canGoNext = navigation.canGoNext()
            ),
            config = currentConfig
        )
    }

    private fun projectionVersion(): String = com.ireader.engines.txt.internal.locator.TxtProjectionVersion.current(files, meta)

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
