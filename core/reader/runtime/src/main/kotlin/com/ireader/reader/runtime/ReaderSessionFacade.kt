package com.ireader.reader.runtime

import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.Locator
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.model.annotation.AnnotationDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ReaderSessionInfo(
    val bookId: Long,
    val openEpoch: Long,
    val format: BookFormat,
    val capabilities: DocumentCapabilities,
    val resources: ResourceProvider?,
    val supportsTextBreakPatches: Boolean
)

data class ReaderViewportSnapshot(
    val sessionInfo: ReaderSessionInfo,
    val layoutConstraints: LayoutConstraints,
    val textLayouterFactory: TextLayouterFactory?
)

interface ReaderSessionFacade {
    val surfaceHandle: StateFlow<ReaderHandle?>
    val sessionInfo: StateFlow<ReaderSessionInfo?>
    val renderState: StateFlow<RenderState?>
    val events: Flow<ReaderEvent>

    fun attach(bookId: Long, handle: ReaderHandle, openEpoch: Long)

    fun clearSession()

    fun updateLayout(constraints: LayoutConstraints)

    fun currentLayout(): LayoutConstraints?

    fun updateLayouter(factory: TextLayouterFactory)

    fun currentLayouterFactory(): TextLayouterFactory?

    fun snapshotIfReady(): ReaderViewportSnapshot?

    suspend fun bindViewportIfReady(
        withNavigationLock: suspend (suspend () -> ReaderResult<Unit>) -> ReaderResult<Unit>
    ): ReaderResult<Unit>?

    fun isCurrent(openEpoch: Long): Boolean

    fun bindCollectors(
        progressJob: Job?,
        stateJob: Job?,
        eventJob: Job?,
        settingsJob: Job?
    )

    fun cancelCollectors()

    suspend fun closeCurrent(
        saveProgress: suspend (bookId: Long, locator: Locator, progression: Double) -> Unit
    )

    suspend fun setConfig(config: RenderConfig): ReaderResult<Unit>

    suspend fun render(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun next(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun prev(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun goTo(locator: Locator, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun goToProgress(
        percent: Double,
        policy: RenderPolicy = RenderPolicy.Default
    ): ReaderResult<RenderPage>

    suspend fun invalidate(reason: InvalidateReason = InvalidateReason.CONTENT_CHANGED): ReaderResult<Unit>

    suspend fun getOutline(): ReaderResult<List<OutlineNode>>

    fun search(
        query: String,
        options: SearchOptions = SearchOptions()
    ): Flow<ReaderResult<SearchHit>>

    suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?>

    suspend fun startSelection(locator: Locator): ReaderResult<Unit>

    suspend fun updateSelection(locator: Locator): ReaderResult<Unit>

    suspend fun finishSelection(): ReaderResult<Unit>

    suspend fun clearSelection(): ReaderResult<Unit>

    suspend fun createAnnotation(draft: AnnotationDraft): ReaderResult<Unit>

    suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage>

    suspend fun clearBreakPatches(): ReaderResult<RenderPage>
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultReaderSessionFacade(
    scope: CoroutineScope
) : ReaderSessionFacade {
    private val handleStore = MutableStateFlow<ReaderHandle?>(null)
    private val sessionInfoStore = MutableStateFlow<ReaderSessionInfo?>(null)

    override val surfaceHandle: StateFlow<ReaderHandle?> = handleStore.asStateFlow()
    override val sessionInfo: StateFlow<ReaderSessionInfo?> = sessionInfoStore.asStateFlow()
    override val renderState: StateFlow<RenderState?> = handleStore
        .flatMapLatest { handle ->
            handle?.state?.map<RenderState, RenderState?> { it } ?: flowOf(null)
        }
        .stateIn(scope, SharingStarted.Eagerly, null)
    override val events: Flow<ReaderEvent> = handleStore
        .flatMapLatest { handle -> handle?.events ?: emptyFlow() }

    private var layoutConstraints: LayoutConstraints? = null
    private var textLayouterFactory: TextLayouterFactory? = null
    private var appliedLayoutConstraints: LayoutConstraints? = null
    private var appliedTextLayouterFactoryKey: String? = null

    private var progressJob: Job? = null
    private var stateJob: Job? = null
    private var eventJob: Job? = null
    private var settingsJob: Job? = null

    override fun attach(bookId: Long, handle: ReaderHandle, openEpoch: Long) {
        cancelCollectors()
        appliedLayoutConstraints = null
        appliedTextLayouterFactoryKey = null
        sessionInfoStore.value = ReaderSessionInfo(
            bookId = bookId,
            openEpoch = openEpoch,
            format = handle.format,
            capabilities = handle.capabilities,
            resources = handle.resources,
            supportsTextBreakPatches = handle.supportsTextBreakPatches
        )
        handleStore.value = handle
    }

    override fun clearSession() {
        appliedLayoutConstraints = null
        appliedTextLayouterFactoryKey = null
        sessionInfoStore.value = null
        handleStore.value = null
    }

    override fun updateLayout(constraints: LayoutConstraints) {
        layoutConstraints = constraints
    }

    override fun currentLayout(): LayoutConstraints? = layoutConstraints

    override fun updateLayouter(factory: TextLayouterFactory) {
        textLayouterFactory = factory
    }

    override fun currentLayouterFactory(): TextLayouterFactory? = textLayouterFactory

    override fun snapshotIfReady(): ReaderViewportSnapshot? {
        val info = sessionInfoStore.value ?: return null
        val constraints = layoutConstraints ?: return null
        val layouter = textLayouterFactory
        if (info.capabilities.reflowable && layouter == null) {
            return null
        }
        return ReaderViewportSnapshot(
            sessionInfo = info,
            layoutConstraints = constraints,
            textLayouterFactory = layouter
        )
    }

    override suspend fun bindViewportIfReady(
        withNavigationLock: suspend (suspend () -> ReaderResult<Unit>) -> ReaderResult<Unit>
    ): ReaderResult<Unit>? {
        val snapshot = snapshotIfReady() ?: return null
        val handle = handleStore.value ?: return null
        if (sessionInfoStore.value?.openEpoch != snapshot.sessionInfo.openEpoch) {
            return null
        }
        val environmentKey = snapshot.textLayouterFactory?.environmentKey
        if (appliedLayoutConstraints == snapshot.layoutConstraints &&
            appliedTextLayouterFactoryKey == environmentKey
        ) {
            return ReaderResult.Ok(Unit)
        }
        return when (
            val result = withNavigationLock {
                handle.bindViewport(
                    constraints = snapshot.layoutConstraints,
                    textLayouterFactory = snapshot.textLayouterFactory
                )
            }
        ) {
            is ReaderResult.Ok -> {
                appliedLayoutConstraints = snapshot.layoutConstraints
                appliedTextLayouterFactoryKey = environmentKey
                result
            }

            is ReaderResult.Err -> result
        }
    }

    override fun isCurrent(openEpoch: Long): Boolean {
        return sessionInfoStore.value?.openEpoch == openEpoch && handleStore.value != null
    }

    override fun bindCollectors(
        progressJob: Job?,
        stateJob: Job?,
        eventJob: Job?,
        settingsJob: Job?
    ) {
        cancelCollectors()
        this.progressJob = progressJob
        this.stateJob = stateJob
        this.eventJob = eventJob
        this.settingsJob = settingsJob
    }

    override fun cancelCollectors() {
        progressJob?.cancel()
        progressJob = null
        stateJob?.cancel()
        stateJob = null
        eventJob?.cancel()
        eventJob = null
        settingsJob?.cancel()
        settingsJob = null
    }

    override suspend fun closeCurrent(
        saveProgress: suspend (bookId: Long, locator: Locator, progression: Double) -> Unit
    ) {
        val currentHandle = handleStore.value
        val currentInfo = sessionInfoStore.value
        cancelCollectors()
        clearSession()

        if (currentHandle != null && currentInfo != null && currentInfo.bookId > 0L) {
            runCatching {
                val state = currentHandle.state.value
                saveProgress(currentInfo.bookId, state.locator, state.progression.percent)
            }
        }

        if (currentHandle != null) {
            runCatching { currentHandle.close() }
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        return withCurrentHandle { it.setConfig(config) }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        return withCurrentHandle { it.render(policy) }
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return withCurrentHandle { it.next(policy) }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return withCurrentHandle { it.prev(policy) }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return withCurrentHandle { it.goTo(locator, policy) }
    }

    override suspend fun goToProgress(
        percent: Double,
        policy: RenderPolicy
    ): ReaderResult<RenderPage> {
        return withCurrentHandle { it.goToProgress(percent, policy) }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return withCurrentHandle { it.invalidate(reason) }
    }

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        return withCurrentHandle { it.getOutline() }
    }

    override fun search(query: String, options: SearchOptions): Flow<ReaderResult<SearchHit>> {
        val handle = handleStore.value
            ?: return flowOf(ReaderResult.Err(ReaderError.Internal("Reader session is not active")))
        return handle.search(query, options)
    }

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return withCurrentHandle { it.currentSelection() }
    }

    override suspend fun startSelection(locator: Locator): ReaderResult<Unit> {
        return withCurrentHandle { it.startSelection(locator) }
    }

    override suspend fun updateSelection(locator: Locator): ReaderResult<Unit> {
        return withCurrentHandle { it.updateSelection(locator) }
    }

    override suspend fun finishSelection(): ReaderResult<Unit> {
        return withCurrentHandle { it.finishSelection() }
    }

    override suspend fun clearSelection(): ReaderResult<Unit> {
        return withCurrentHandle { it.clearSelection() }
    }

    override suspend fun createAnnotation(draft: AnnotationDraft): ReaderResult<Unit> {
        return withCurrentHandle { it.createAnnotation(draft) }
    }

    override suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage> {
        return withCurrentHandle { it.applyBreakPatch(locator, direction, state) }
    }

    override suspend fun clearBreakPatches(): ReaderResult<RenderPage> {
        return withCurrentHandle { it.clearBreakPatches() }
    }

    private suspend inline fun <T> withCurrentHandle(
        crossinline block: suspend (ReaderHandle) -> ReaderResult<T>
    ): ReaderResult<T> {
        val handle = handleStore.value
            ?: return ReaderResult.Err(ReaderError.Internal("Reader session is not active"))
        return block(handle)
    }
}
