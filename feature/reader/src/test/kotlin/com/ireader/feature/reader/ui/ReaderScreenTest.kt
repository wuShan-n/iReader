package com.ireader.feature.reader.ui

import android.view.KeyEvent
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.ReaderLayerState
import com.ireader.feature.reader.presentation.ReaderSheet
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.model.Progression
import com.ireader.reader.runtime.ReaderHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class ReaderScreenTest {

    @Test
    fun `volume keys should not be consumed while sheet is open`() {
        val state = readerUiState(layerState = ReaderLayerState.Sheet(ReaderSheet.Search))

        assertFalse(shouldConsumeVolumeKeyPaging(state, KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(
            volumePagingIntentForKey(
                state = state,
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN
            )
        )
    }

    @Test
    fun `volume down should trigger next page only on key down while reading`() {
        val state = readerUiState()

        assertTrue(shouldConsumeVolumeKeyPaging(state, KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(
            ReaderIntent.Next,
            volumePagingIntentForKey(
                state = state,
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN
            )
        )
        assertNull(
            volumePagingIntentForKey(
                state = state,
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_UP
            )
        )
    }
}

private fun readerUiState(
    layerState: ReaderLayerState = ReaderLayerState.Reading
): ReaderUiState {
    return ReaderUiState(
        layerState = layerState,
        displayPrefs = ReaderDisplayPrefs(volumeKeyPagingEnabled = true),
        capabilities = DummyReaderHandle.capabilities
    )
}

private object DummyReaderHandle : ReaderHandle {
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
            locator = Locator("txt.stable.anchor", "0:0"),
            progression = Progression(0.0),
            nav = NavigationAvailability(canGoPrev = true, canGoNext = true),
            config = RenderConfig.ReflowText()
        )
    )
    override val events: Flow<ReaderEvent> = MutableSharedFlow()
    override val supportsTextBreakPatches: Boolean = false

    override suspend fun bindSurface(surface: com.ireader.reader.api.render.RenderSurface): ReaderResult<Unit> =
        ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun bindViewport(
        constraints: com.ireader.reader.api.render.LayoutConstraints,
        textLayouterFactory: com.ireader.reader.api.render.TextLayouterFactory?
    ): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun render(policy: com.ireader.reader.api.render.RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun next(policy: com.ireader.reader.api.render.RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun prev(policy: com.ireader.reader.api.render.RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun goTo(
        locator: Locator,
        policy: com.ireader.reader.api.render.RenderPolicy
    ): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun goToProgress(
        percent: Double,
        policy: com.ireader.reader.api.render.RenderPolicy
    ): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> = ReaderResult.Ok(emptyList())

    override fun search(query: String, options: SearchOptions): Flow<ReaderResult<SearchHit>> = flowOf()

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> = ReaderResult.Ok(null)

    override suspend fun startSelection(locator: Locator): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun updateSelection(locator: Locator): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun finishSelection(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun clearSelection(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun createAnnotation(
        draft: com.ireader.reader.model.annotation.AnnotationDraft
    ): ReaderResult<Unit> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun applyBreakPatch(
        locator: Locator,
        direction: com.ireader.reader.api.engine.TextBreakPatchDirection,
        state: com.ireader.reader.api.engine.TextBreakPatchState
    ): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun clearBreakPatches(): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override fun close() = Unit
}
