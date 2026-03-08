package com.ireader.reader.testkit.fake

import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.runtime.ReaderHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeTextBreakPatchSupport(
    val page: RenderPage
) {
    var applyCalls: Int = 0
    var clearCalls: Int = 0
}

class FakeAnnotationSupport(
    var selection: SelectionProvider.Selection? = null,
    var createResult: ReaderResult<Unit> = ReaderResult.Ok(Unit)
) {
    var createAnnotationCalls: Int = 0
    var clearSelectionCalls: Int = 0
}

open class FakeReaderHandle(
    override val capabilities: DocumentCapabilities,
    override val format: BookFormat = if (capabilities.fixedLayout) BookFormat.PDF else BookFormat.TXT,
    private val controller: ReaderController = RecordingReaderController(
        initialConfig = if (capabilities.fixedLayout) {
            RenderConfig.FixedPage()
        } else {
            RenderConfig.ReflowText()
        }
    ),
    private val searchProvider: SearchProvider? = null,
    private val outlineResult: ReaderResult<List<OutlineNode>> = ReaderResult.Ok(emptyList()),
    override val resources: ResourceProvider? = null,
    private val textBreakPatchSupport: FakeTextBreakPatchSupport? = null,
    private val annotationSupport: FakeAnnotationSupport? = null
) : ReaderHandle {
    override val documentId: DocumentId = DocumentId("doc")
    override val state = controller.state
    override val events: Flow<ReaderEvent> = controller.events
    override val supportsTextBreakPatches: Boolean = textBreakPatchSupport != null

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> {
        return controller.bindSurface(surface)
    }

    override suspend fun unbindSurface(): ReaderResult<Unit> {
        return controller.unbindSurface()
    }

    override suspend fun bindViewport(
        constraints: LayoutConstraints,
        textLayouterFactory: TextLayouterFactory?
    ): ReaderResult<Unit> {
        if (capabilities.reflowable) {
            val factory = textLayouterFactory
                ?: return ReaderResult.Err(ReaderError.Internal("Reader text layouter is required"))
            when (val layouterResult = controller.setTextLayouterFactory(factory)) {
                is ReaderResult.Err -> return layouterResult
                is ReaderResult.Ok -> Unit
            }
        } else if (textLayouterFactory != null) {
            when (val layouterResult = controller.setTextLayouterFactory(textLayouterFactory)) {
                is ReaderResult.Err -> return layouterResult
                is ReaderResult.Ok -> Unit
            }
        }
        return controller.setLayoutConstraints(constraints)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        return controller.setConfig(config)
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        return controller.render(policy)
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return controller.next(policy)
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return controller.prev(policy)
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return controller.goTo(locator, policy)
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        return controller.goToProgress(percent, policy)
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return controller.invalidate(reason)
    }

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> = outlineResult

    override fun search(query: String, options: SearchOptions): Flow<ReaderResult<SearchHit>> {
        val provider = searchProvider ?: return flowOf(
            ReaderResult.Err(ReaderError.Internal("Search is not available"))
        )
        return provider.search(query, options).map { ReaderResult.Ok(it) }
    }

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return ReaderResult.Ok(annotationSupport?.selection)
    }

    override suspend fun startSelection(locator: Locator): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun updateSelection(locator: Locator): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun finishSelection(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun clearSelection(): ReaderResult<Unit> {
        annotationSupport?.let { it.clearSelectionCalls += 1 }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun createAnnotation(draft: AnnotationDraft): ReaderResult<Unit> {
        val support = annotationSupport ?: return ReaderResult.Err(ReaderError.Internal("Annotations are not available"))
        support.createAnnotationCalls += 1
        return support.createResult
    }

    override suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage> {
        val support = textBreakPatchSupport
            ?: return ReaderResult.Err(ReaderError.Internal("Text break patching is not available"))
        support.applyCalls += 1
        return ReaderResult.Ok(support.page)
    }

    override suspend fun clearBreakPatches(): ReaderResult<RenderPage> {
        val support = textBreakPatchSupport
            ?: return ReaderResult.Err(ReaderError.Internal("Text break patching is not available"))
        support.clearCalls += 1
        return ReaderResult.Ok(support.page)
    }

    override fun close() = Unit
}

fun readerHandle(
    controller: ReaderController = RecordingReaderController(),
    search: SearchProvider? = null,
    textBreakPatchSupport: FakeTextBreakPatchSupport? = null,
    annotationSupport: FakeAnnotationSupport? = null,
    fixedLayout: Boolean = false,
    documentFormat: BookFormat = if (fixedLayout) BookFormat.PDF else BookFormat.TXT
): ReaderHandle {
    return FakeReaderHandle(
        capabilities = if (fixedLayout) {
            fixedCapabilities(
                search = search != null,
                annotations = annotationSupport != null,
                selection = annotationSupport != null
            )
        } else {
            reflowCapabilities(
                search = search != null,
                annotations = annotationSupport != null,
                selection = annotationSupport != null
            )
        },
        format = documentFormat,
        controller = controller,
        searchProvider = search,
        textBreakPatchSupport = textBreakPatchSupport,
        annotationSupport = annotationSupport
    )
}
