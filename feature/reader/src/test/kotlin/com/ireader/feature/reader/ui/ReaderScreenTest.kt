package com.ireader.feature.reader.ui

import android.view.KeyEvent
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.ReaderLayerState
import com.ireader.feature.reader.presentation.ReaderSheet
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        controller = NoOpReaderController()
    )
}

private class NoOpReaderController : ReaderController {
    private val stateStore = MutableStateFlow(
        RenderState(
            locator = Locator(scheme = "txt.offset", value = "0"),
            progression = Progression(0.0),
            nav = NavigationAvailability(canGoPrev = true, canGoNext = true),
            config = com.ireader.reader.api.render.RenderConfig.ReflowText()
        )
    )

    override val state = stateStore
    override val events: Flow<ReaderEvent> = MutableSharedFlow()

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setTextLayouterFactory(
        factory: com.ireader.reader.api.render.TextLayouterFactory
    ): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setConfig(config: com.ireader.reader.api.render.RenderConfig): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Ok(page("render"))

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Ok(page("next"))

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Ok(page("prev"))

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Ok(page("goto"))

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Ok(page("progress"))

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override fun close() = Unit

    private fun page(id: String): RenderPage {
        return RenderPage(
            id = PageId(id),
            locator = stateStore.value.locator,
            content = RenderContent.Text(text = id)
        )
    }
}
