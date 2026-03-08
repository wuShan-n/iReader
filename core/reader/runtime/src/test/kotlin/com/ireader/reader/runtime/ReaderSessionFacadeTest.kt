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
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.TextLayoutInput
import com.ireader.reader.api.render.TextLayoutMeasureResult
import com.ireader.reader.api.render.TextLayouter
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.model.Progression
import com.ireader.reader.model.annotation.AnnotationDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionFacadeTest {

    @Test
    fun `bindViewportIfReady should skip duplicate environment`() = runTest {
        val facade = DefaultReaderSessionFacade(backgroundScope)
        val handle = CountingHandle()
        val layout = LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 3f,
            fontScale = 1f
        )

        facade.attach(bookId = 1L, handle = handle, openEpoch = 1L)
        facade.updateLayout(layout)
        facade.updateLayouter(FakeLayouterFactory("env-a"))

        facade.bindViewportIfReady { block -> block() }
        facade.bindViewportIfReady { block -> block() }

        assertEquals(1, handle.bindViewportCalls)
    }

    @Test
    fun `bindViewportIfReady should rebind when layout or layouter changes`() = runTest {
        val facade = DefaultReaderSessionFacade(backgroundScope)
        val handle = CountingHandle()

        facade.attach(bookId = 1L, handle = handle, openEpoch = 1L)
        facade.updateLayout(
            LayoutConstraints(
                viewportWidthPx = 1080,
                viewportHeightPx = 1920,
                density = 3f,
                fontScale = 1f
            )
        )
        facade.updateLayouter(FakeLayouterFactory("env-a"))
        facade.bindViewportIfReady { block -> block() }

        facade.updateLayout(
            LayoutConstraints(
                viewportWidthPx = 1200,
                viewportHeightPx = 1920,
                density = 3f,
                fontScale = 1f
            )
        )
        facade.bindViewportIfReady { block -> block() }
        facade.updateLayouter(FakeLayouterFactory("env-b"))
        facade.bindViewportIfReady { block -> block() }

        assertEquals(3, handle.bindViewportCalls)
    }

    @Test
    fun `renderState should follow current attached handle`() = runTest {
        val facade = DefaultReaderSessionFacade(backgroundScope)
        val first = CountingHandle(
            locator = Locator("txt.stable.anchor", "0:0")
        )
        val second = CountingHandle(
            locator = Locator("txt.stable.anchor", "42:0")
        )

        runCurrent()
        facade.attach(bookId = 1L, handle = first, openEpoch = 1L)
        assertEquals(
            "0:0",
            facade.renderState.first { it?.locator?.value == "0:0" }?.locator?.value
        )

        facade.attach(bookId = 2L, handle = second, openEpoch = 2L)
        assertEquals(
            "42:0",
            facade.renderState.first { it?.locator?.value == "42:0" }?.locator?.value
        )
    }

    @Test
    fun `closeCurrent should save progress and clear session`() = runTest {
        val facade = DefaultReaderSessionFacade(backgroundScope)
        val handle = CountingHandle(
            locator = Locator("txt.stable.anchor", "8:0"),
            progression = Progression(0.4)
        )
        var savedBookId: Long? = null
        var savedLocator: Locator? = null
        var savedProgression: Double? = null

        facade.attach(bookId = 9L, handle = handle, openEpoch = 3L)
        facade.closeCurrent { bookId, locator, progression ->
            savedBookId = bookId
            savedLocator = locator
            savedProgression = progression
        }

        assertTrue(handle.closed)
        assertEquals(9L, savedBookId)
        assertEquals("8:0", savedLocator?.value)
        assertEquals(0.4, savedProgression)
        assertNull(facade.sessionInfo.value)
        assertNull(facade.surfaceHandle.value)
    }
}

private class CountingHandle(
    locator: Locator = Locator("txt.stable.anchor", "0:0"),
    progression: Progression = Progression(0.0)
) : ReaderHandle {
    var bindViewportCalls: Int = 0
    var closed: Boolean = false

    override val format: BookFormat = BookFormat.TXT
    override val documentId: DocumentId = DocumentId("doc")
    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = true,
        fixedLayout = false,
        outline = false,
        search = false,
        textExtraction = false,
        annotations = false,
        selection = false,
        links = false
    )
    override val resources: ResourceProvider? = null
    override val state = MutableStateFlow(
        RenderState(
            locator = locator,
            progression = progression,
            nav = NavigationAvailability(canGoPrev = true, canGoNext = true),
            config = RenderConfig.ReflowText()
        )
    )
    override val events: Flow<ReaderEvent> = MutableSharedFlow()
    override val supportsTextBreakPatches: Boolean = false

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun bindViewport(
        constraints: LayoutConstraints,
        textLayouterFactory: TextLayouterFactory?
    ): ReaderResult<Unit> {
        bindViewportCalls += 1
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> = ReaderResult.Ok(emptyList())

    override fun search(query: String, options: SearchOptions): Flow<ReaderResult<SearchHit>> = flowOf()

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> = ReaderResult.Ok(null)

    override suspend fun startSelection(locator: Locator): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun updateSelection(locator: Locator): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun finishSelection(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun clearSelection(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun createAnnotation(draft: AnnotationDraft): ReaderResult<Unit> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun clearBreakPatches(): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override fun close() {
        closed = true
    }
}

private class FakeLayouterFactory(
    override val environmentKey: String
) : TextLayouterFactory {
    override fun create(cacheSize: Int): TextLayouter {
        return object : TextLayouter {
            override fun measure(
                text: CharSequence,
                input: TextLayoutInput
            ): TextLayoutMeasureResult {
                return TextLayoutMeasureResult(
                    endChar = text.length,
                    lineCount = 1,
                    lastVisibleLine = 0
                )
            }
        }
    }
}
