package com.ireader.reader.runtime

import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
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
import com.ireader.reader.runtime.flow.asReaderResult
import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

interface ReaderHandle : Closeable {
    val document: ReaderDocument
    val session: ReaderSession
    val controller: ReaderController
    val outline: OutlineProvider?
    val search: SearchProvider?
    val annotations: AnnotationProvider?
    val selection: SelectionProvider?
    val selectionController: SelectionController?
    val format: BookFormat
    val documentId: DocumentId
    val capabilities: DocumentCapabilities
    val resources: ResourceProvider?
    val state: StateFlow<RenderState>
    val events: Flow<ReaderEvent>
    val supportsTextBreakPatches: Boolean

    suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit>

    suspend fun unbindSurface(): ReaderResult<Unit>

    suspend fun bindViewport(
        constraints: LayoutConstraints,
        textLayouterFactory: TextLayouterFactory
    ): ReaderResult<Unit>

    suspend fun setConfig(config: RenderConfig): ReaderResult<Unit>

    suspend fun render(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun next(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun prev(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun goTo(locator: Locator, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun goToProgress(percent: Double, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

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

    suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage>

    suspend fun clearBreakPatches(): ReaderResult<RenderPage>
}

internal class DefaultReaderHandle(
    private val sessionHandle: ReaderSessionHandle
) : ReaderHandle {
    private var viewportBound: Boolean = false

    override val document: ReaderDocument = sessionHandle.document
    override val session: ReaderSession = sessionHandle.session
    override val controller: ReaderController = sessionHandle.controller
    override val outline: OutlineProvider? = sessionHandle.outline
    override val search: SearchProvider? = sessionHandle.search
    override val annotations: AnnotationProvider? = sessionHandle.annotations
    override val selection: SelectionProvider? = sessionHandle.selection
    override val selectionController: SelectionController? = sessionHandle.selectionController
    override val format: BookFormat = sessionHandle.document.format
    override val documentId: DocumentId = sessionHandle.document.id
    override val capabilities: DocumentCapabilities = sessionHandle.document.capabilities
    override val resources: ResourceProvider? = sessionHandle.resources
    override val state: StateFlow<RenderState> = sessionHandle.controller.state
    override val events: Flow<ReaderEvent> = sessionHandle.controller.events
    override val supportsTextBreakPatches: Boolean = sessionHandle.session is com.ireader.reader.api.engine.TextBreakPatchSupport

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> {
        return controller.bindSurface(surface)
    }

    override suspend fun unbindSurface(): ReaderResult<Unit> {
        return controller.unbindSurface()
    }

    override suspend fun bindViewport(
        constraints: LayoutConstraints,
        textLayouterFactory: TextLayouterFactory
    ): ReaderResult<Unit> {
        when (val layouterResult = controller.setTextLayouterFactory(textLayouterFactory)) {
            is ReaderResult.Err -> return layouterResult
            is ReaderResult.Ok -> Unit
        }
        when (val layoutResult = controller.setLayoutConstraints(constraints)) {
            is ReaderResult.Err -> return layoutResult
            is ReaderResult.Ok -> Unit
        }
        viewportBound = true
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        return controller.setConfig(config)
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (val guard = requireViewportBound()) {
            is ReaderResult.Err -> guard
            is ReaderResult.Ok -> controller.render(policy)
        }
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (val guard = requireViewportBound()) {
            is ReaderResult.Err -> guard
            is ReaderResult.Ok -> controller.next(policy)
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (val guard = requireViewportBound()) {
            is ReaderResult.Err -> guard
            is ReaderResult.Ok -> controller.prev(policy)
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (val guard = requireViewportBound()) {
            is ReaderResult.Err -> guard
            is ReaderResult.Ok -> controller.goTo(locator, policy)
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (val guard = requireViewportBound()) {
            is ReaderResult.Err -> guard
            is ReaderResult.Ok -> controller.goToProgress(percent, policy)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return controller.invalidate(reason)
    }

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        val provider = outline
            ?: return ReaderResult.Err(ReaderError.Internal("Outline is not available"))
        return provider.getOutline()
    }

    override fun search(query: String, options: SearchOptions): Flow<ReaderResult<SearchHit>> {
        val provider = search
            ?: return flowOf(ReaderResult.Err(ReaderError.Internal("Search is not available")))
        return provider.search(query, options).asReaderResult()
    }

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        val provider = selection
            ?: return ReaderResult.Ok(null)
        return provider.currentSelection()
    }

    override suspend fun startSelection(locator: Locator): ReaderResult<Unit> {
        val selectionController = selectionController
            ?: return ReaderResult.Err(ReaderError.Internal("Selection is not available"))
        return selectionController.start(locator)
    }

    override suspend fun updateSelection(locator: Locator): ReaderResult<Unit> {
        val selectionController = selectionController
            ?: return ReaderResult.Err(ReaderError.Internal("Selection is not available"))
        return selectionController.update(locator)
    }

    override suspend fun finishSelection(): ReaderResult<Unit> {
        val selectionController = selectionController
            ?: return ReaderResult.Err(ReaderError.Internal("Selection is not available"))
        return selectionController.finish()
    }

    override suspend fun clearSelection(): ReaderResult<Unit> {
        selection?.clearSelection()
        val selectionController = selectionController ?: return ReaderResult.Ok(Unit)
        return selectionController.clear()
    }

    override suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage> {
        val patchSupport = session as? com.ireader.reader.api.engine.TextBreakPatchSupport
            ?: return ReaderResult.Err(ReaderError.Internal("Text break patching is not available"))
        return patchSupport.applyBreakPatch(locator, direction, state)
    }

    override suspend fun clearBreakPatches(): ReaderResult<RenderPage> {
        val patchSupport = session as? com.ireader.reader.api.engine.TextBreakPatchSupport
            ?: return ReaderResult.Err(ReaderError.Internal("Text break patching is not available"))
        return patchSupport.clearBreakPatches()
    }

    override fun close() {
        sessionHandle.close()
    }

    private fun requireViewportBound(): ReaderResult<Unit> {
        if (!capabilities.reflowable || viewportBound) {
            return ReaderResult.Ok(Unit)
        }
        return ReaderResult.Err(ReaderError.Internal("Reader viewport is not bound"))
    }
}
