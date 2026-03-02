package com.ireader.engines.epub.internal.readium

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
import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

internal class ReadiumEpubController(
    private val navigatorAdapter: ReadiumNavigatorAdapter,
    private val ioDispatcher: CoroutineDispatcher,
    initialConfig: RenderConfig.ReflowText,
    initialLocator: Locator?,
    private val positionLocators: List<Locator>
) : ReaderController {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private var config: RenderConfig.ReflowText = initialConfig
    private var constraints: LayoutConstraints? = null

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 32)
    override val events: Flow<ReaderEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(
        buildState(initialLocator ?: navigatorAdapter.locatorFlow.value ?: Locator("epub.cfi", "epubcfi(/)"))
    )
    override val state: StateFlow<RenderState> = _state

    init {
        scope.launch {
            navigatorAdapter.locatorFlow
                .filterNotNull()
                .collect { locator ->
                    _state.value = buildState(locator)
                    _events.tryEmit(ReaderEvent.PageChanged(locator))
                }
        }
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        this.constraints = constraints
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val reflow = (config as? RenderConfig.ReflowText)
            ?: return ReaderResult.Err(ReaderError.Internal("EPUB only supports ReflowText config"))
        this.config = reflow
        return navigatorAdapter.submitConfig(reflow)
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        val locator = navigatorAdapter.locatorFlow.value ?: state.value.locator
        val page = virtualPage(locator = locator, policy = policy, cacheHit = true)
        _events.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        return ReaderResult.Ok(page)
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return navigate(policy) { navigatorAdapter.next() }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return navigate(policy) { navigatorAdapter.prev() }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return navigate(policy) { navigatorAdapter.goTo(locator) }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        if (positionLocators.isEmpty()) {
            return render(policy)
        }
        val safePercent = percent.coerceIn(0.0, 1.0)
        val index = (safePercent * (positionLocators.size - 1)).roundToInt()
            .coerceIn(0, positionLocators.lastIndex)
        return goTo(positionLocators[index], policy)
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        val locator = navigatorAdapter.locatorFlow.value ?: state.value.locator
        _state.value = buildState(locator)
        return ReaderResult.Ok(Unit)
    }

    override fun close() {
        scope.cancel()
    }

    private fun buildState(locator: Locator): RenderState {
        val progression = locator.extras["progression"]?.toDoubleOrNull()
            ?: locator.extras["totalProgression"]?.toDoubleOrNull()
            ?: 0.0
        val percent = progression.coerceIn(0.0, 1.0)
        val current = (percent * 100.0).roundToInt()
        return RenderState(
            locator = locator,
            progression = Progression(
                percent = percent,
                label = "$current%",
                current = current,
                total = 100
            ),
            nav = NavigationAvailability(
                canGoPrev = percent > 0.00001,
                canGoNext = percent < 0.99999
            ),
            titleInView = locator.extras["title"],
            config = config
        )
    }

    private suspend fun navigate(
        policy: RenderPolicy,
        operation: suspend () -> ReaderResult<Unit>
    ): ReaderResult<RenderPage> {
        return when (val result = operation()) {
            is ReaderResult.Err -> result
            is ReaderResult.Ok -> {
                val locator = navigatorAdapter.locatorFlow.value ?: state.value.locator
                _state.value = buildState(locator)
                _events.tryEmit(ReaderEvent.PageChanged(locator))

                val page = virtualPage(locator = locator, policy = policy, cacheHit = false)
                _events.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
                ReaderResult.Ok(page)
            }
        }
    }

    private fun virtualPage(
        locator: Locator,
        policy: RenderPolicy,
        cacheHit: Boolean
    ): RenderPage {
        return RenderPage(
            id = PageId("epub:navigator:${locator.scheme}:${locator.value.hashCode()}"),
            locator = locator,
            content = RenderContent.Html(
                inlineHtml = "<!doctype html><html><body></body></html>"
            ),
            metrics = RenderMetrics(
                renderTimeMs = 0L,
                cacheHit = cacheHit && policy.allowCache
            )
        )
    }
}
