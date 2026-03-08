package com.ireader.reader.testkit.fake

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
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

open class RecordingReaderController(
    initialConfig: RenderConfig = RenderConfig.ReflowText(),
    private val requireLayoutBeforeRender: Boolean = false
) : ReaderController {
    var nextCalls: Int = 0
    var prevCalls: Int = 0
    var setConfigCalls: Int = 0
    var setLayoutConstraintsCalls: Int = 0
    var renderCalls: Int = 0
    var invalidateCalls: Int = 0
    private var hasLayoutConstraints: Boolean = false

    private val stateStore = MutableStateFlow(
        RenderState(
            locator = Locator(scheme = "txt.stable.anchor", value = "0:0"),
            progression = Progression(0.0),
            nav = NavigationAvailability(canGoPrev = true, canGoNext = true),
            config = initialConfig
        )
    )

    override val state = stateStore
    override val events: Flow<ReaderEvent> = MutableSharedFlow()

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        setLayoutConstraintsCalls += 1
        hasLayoutConstraints = true
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setTextLayouterFactory(factory: TextLayouterFactory): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        setConfigCalls += 1
        stateStore.value = stateStore.value.copy(config = config)
        return ReaderResult.Ok(Unit)
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        renderCalls += 1
        if (requireLayoutBeforeRender && !hasLayoutConstraints) {
            return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
        }
        return ReaderResult.Ok(renderPage("render"))
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        nextCalls += 1
        return ReaderResult.Ok(renderPage("next-$nextCalls"))
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        prevCalls += 1
        return ReaderResult.Ok(renderPage("prev-$prevCalls"))
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        stateStore.value = stateStore.value.copy(locator = locator)
        return ReaderResult.Ok(renderPage("goto-${locator.value}", locator))
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        stateStore.value = stateStore.value.copy(progression = Progression(percent))
        return ReaderResult.Ok(renderPage("progress-$percent"))
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        invalidateCalls += 1
        return ReaderResult.Ok(Unit)
    }

    override fun close() = Unit

    protected fun renderPage(
        id: String,
        locator: Locator = stateStore.value.locator
    ): RenderPage {
        return RenderPage(
            id = PageId(id),
            locator = locator,
            content = RenderContent.Text(text = id)
        )
    }
}

class DelayedRenderController(
    private val delayMs: Long,
    private val pageId: String
) : ReaderController {
    var renderCalls: Int = 0
    private var hasLayoutConstraints: Boolean = false
    private val stateStore = MutableStateFlow(
        RenderState(
            locator = Locator(scheme = "txt.stable.anchor", value = "0:0"),
            progression = Progression(0.0),
            nav = NavigationAvailability(canGoPrev = true, canGoNext = true),
            config = RenderConfig.ReflowText()
        )
    )

    override val state = stateStore
    override val events: Flow<ReaderEvent> = MutableSharedFlow()

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        hasLayoutConstraints = true
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setTextLayouterFactory(factory: TextLayouterFactory): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        renderCalls += 1
        if (!hasLayoutConstraints) {
            return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
        }
        delay(delayMs)
        return ReaderResult.Ok(
            RenderPage(
                id = PageId(pageId),
                locator = stateStore.value.locator,
                content = RenderContent.Text(text = pageId)
            )
        )
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> = render(policy)

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> = render(policy)

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> = render(policy)

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> =
        render(policy)

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override fun close() = Unit
}
