package com.ireader.engines.epub.internal.readium

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
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

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("EPUB navigator session does not produce RenderPage"))

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (val result = navigatorAdapter.next()) {
            is ReaderResult.Ok -> ReaderResult.Err(ReaderError.Internal("EPUB navigator session does not produce RenderPage"))
            is ReaderResult.Err -> result
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (val result = navigatorAdapter.prev()) {
            is ReaderResult.Ok -> ReaderResult.Err(ReaderError.Internal("EPUB navigator session does not produce RenderPage"))
            is ReaderResult.Err -> result
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (val result = navigatorAdapter.goTo(locator)) {
            is ReaderResult.Ok -> ReaderResult.Err(ReaderError.Internal("EPUB navigator session does not produce RenderPage"))
            is ReaderResult.Err -> result
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        if (positionLocators.isEmpty()) {
            return ReaderResult.Err(ReaderError.Internal("No position map for progression seek"))
        }
        val safePercent = percent.coerceIn(0.0, 1.0)
        val index = (safePercent * (positionLocators.size - 1)).roundToInt()
            .coerceIn(0, positionLocators.lastIndex)
        return goTo(positionLocators[index], policy)
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)

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
            nav = NavigationAvailability(canGoPrev = true, canGoNext = true),
            titleInView = locator.extras["title"],
            config = config
        )
    }
}
