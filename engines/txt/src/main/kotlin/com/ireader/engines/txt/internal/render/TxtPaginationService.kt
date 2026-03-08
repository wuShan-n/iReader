package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.TextLayouterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class TxtPaginationService(
    documentKey: String,
    store: Utf16TextStore,
    blockStore: BlockStore,
    projectionEngine: TextProjectionEngine,
    maxPageCache: Int,
    persistPagination: Boolean,
    files: TxtBookFiles,
    initialConfig: RenderConfig.ReflowText,
    private val ioDispatcher: CoroutineDispatcher,
    paginationDispatcher: CoroutineDispatcher,
    private val launchTask: (String, suspend CoroutineScope.() -> Unit) -> Job
) {
    private val coordinator = PaginationCoordinator(
        documentKey = documentKey,
        store = store,
        blockStore = blockStore,
        projectionEngine = projectionEngine,
        maxPageCache = maxPageCache,
        persistPagination = persistPagination,
        files = files,
        paginationDispatcher = paginationDispatcher
    ).apply {
        bindInitialConfig(initialConfig)
    }

    private var generation: Long = 0L
    private var prefetchJob: Job? = null
    private var pageCompletionJob: Job? = null
    private var textLayouterFactoryKey: String? = null

    fun hasTextLayouterFactory(): Boolean = textLayouterFactoryKey != null

    fun setTextLayouterFactory(factory: TextLayouterFactory) {
        if (textLayouterFactoryKey == factory.environmentKey) {
            return
        }
        textLayouterFactoryKey = factory.environmentKey
        coordinator.setTextLayouterFactory(factory)
        invalidate()
    }

    fun setLayoutConstraints(constraints: LayoutConstraints) {
        coordinator.setLayoutConstraints(constraints)
        invalidate()
    }

    fun setConfig(config: RenderConfig.ReflowText) {
        coordinator.setConfig(config)
        invalidate()
    }

    suspend fun pageAt(
        startOffset: Long,
        allowCache: Boolean
    ): PaginationCoordinator.PageLookup {
        return coordinator.pageAt(startOffset = startOffset, allowCache = allowCache)
    }

    suspend fun previousStart(fromStart: Long): Long {
        return coordinator.previousStart(fromStart)
    }

    fun startForProgress(percent: Double): Long {
        return coordinator.startForProgress(percent)
    }

    fun onPageRendered(
        currentStart: Long,
        policy: RenderPolicy
    ) {
        maybeSchedulePageCompletion(currentStart)
        if (policy.prefetchNeighbors > 0) {
            schedulePrefetch(
                currentStart = currentStart,
                count = policy.prefetchNeighbors
            )
        }
    }

    suspend fun prefetchNeighbors(
        count: Int,
        currentStart: Long
    ): ReaderResult<Unit> {
        if (count <= 0 || !hasTextLayouterFactory()) {
            return ReaderResult.Ok(Unit)
        }
        val expectedGeneration = generation
        return withContext(ioDispatcher) {
            if (!isGenerationCurrent(expectedGeneration) || !hasTextLayouterFactory()) {
                return@withContext ReaderResult.Ok(Unit)
            }
            coordinator.prefetchAround(currentStart = currentStart, count = count)
            ReaderResult.Ok(Unit)
        }
    }

    fun cancelBackgroundWork() {
        prefetchJob?.cancel()
        prefetchJob = null
        pageCompletionJob?.cancel()
        pageCompletionJob = null
    }

    fun invalidate() {
        generation++
        cancelBackgroundWork()
        coordinator.invalidate()
    }

    fun invalidateProjectedContent() {
        generation++
        cancelBackgroundWork()
        coordinator.invalidateProjectedContent()
    }

    fun close() {
        cancelBackgroundWork()
        coordinator.close()
    }

    private fun maybeSchedulePageCompletion(currentStart: Long) {
        if (pageCompletionJob?.isActive == true) {
            return
        }
        val expectedGeneration = generation
        pageCompletionJob = launchTask("page-checkpoint-warmup") {
            delay(BACKGROUND_PAGINATION_DELAY_MS)
            if (isGenerationCurrent(expectedGeneration)) {
                coordinator.warmForward(
                    fromStart = currentStart,
                    maxPages = PAGE_COMPLETION_WARMUP_PAGES
                )
            }
        }
    }

    private fun schedulePrefetch(
        currentStart: Long,
        count: Int
    ) {
        if (count <= 0 || !hasTextLayouterFactory() || pageCompletionJob?.isActive == true) {
            return
        }
        prefetchJob?.cancel()
        val expectedGeneration = generation
        prefetchJob = launchTask("prefetch-neighbors") {
            if (isGenerationCurrent(expectedGeneration)) {
                coordinator.prefetchAround(currentStart = currentStart, count = count)
            }
        }
    }

    private fun isGenerationCurrent(expectedGeneration: Long): Boolean {
        return generation == expectedGeneration
    }

    private companion object {
        private const val BACKGROUND_PAGINATION_DELAY_MS = 450L
        private const val PAGE_COMPLETION_WARMUP_PAGES = 2
    }
}
