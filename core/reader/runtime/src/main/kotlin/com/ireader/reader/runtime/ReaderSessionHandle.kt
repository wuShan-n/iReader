package com.ireader.reader.runtime

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ReaderSessionHandle(
    override val document: ReaderDocument,
    override val session: ReaderSession
) : ReaderHandle {
    private val delegate by lazy(LazyThreadSafetyMode.NONE) { DefaultReaderHandle(this) }

    // 方便 feature 直接拿 controller/providers
    override val controller = session.controller
    override val outline = session.outline
    override val search = session.search
    val text = session.text
    override val annotations = session.annotations
    override val resources = session.resources
    override val selection = session.selection
    override val selectionController = session.selectionController
    override val format: BookFormat get() = delegate.format
    override val documentId: DocumentId get() = delegate.documentId
    override val capabilities get() = delegate.capabilities
    override val state: StateFlow<RenderState> get() = delegate.state
    override val events: Flow<ReaderEvent> get() = delegate.events
    override val supportsTextBreakPatches: Boolean get() = delegate.supportsTextBreakPatches

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> =
        delegate.bindSurface(surface)

    override suspend fun unbindSurface(): ReaderResult<Unit> =
        delegate.unbindSurface()

    override suspend fun bindViewport(
        constraints: LayoutConstraints,
        textLayouterFactory: TextLayouterFactory
    ): ReaderResult<Unit> = delegate.bindViewport(constraints, textLayouterFactory)

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> =
        delegate.setConfig(config)

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> =
        delegate.render(policy)

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> =
        delegate.next(policy)

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> =
        delegate.prev(policy)

    override suspend fun goTo(
        locator: Locator,
        policy: RenderPolicy
    ): ReaderResult<RenderPage> = delegate.goTo(locator, policy)

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> =
        delegate.goToProgress(percent, policy)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> =
        delegate.invalidate(reason)

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> =
        delegate.getOutline()

    override fun search(
        query: String,
        options: SearchOptions
    ) = delegate.search(query, options)

    override suspend fun currentSelection() = delegate.currentSelection()

    override suspend fun startSelection(locator: Locator): ReaderResult<Unit> =
        delegate.startSelection(locator)

    override suspend fun updateSelection(locator: Locator): ReaderResult<Unit> =
        delegate.updateSelection(locator)

    override suspend fun finishSelection(): ReaderResult<Unit> =
        delegate.finishSelection()

    override suspend fun clearSelection(): ReaderResult<Unit> =
        delegate.clearSelection()

    override suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage> = delegate.applyBreakPatch(locator, direction, state)

    override suspend fun clearBreakPatches(): ReaderResult<RenderPage> =
        delegate.clearBreakPatches()

    override fun close() {
        // 逆序关闭更安全
        runCatching { session.close() }
        runCatching { document.close() }
    }
}

