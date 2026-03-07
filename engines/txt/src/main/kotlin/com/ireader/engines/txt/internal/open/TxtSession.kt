package com.ireader.engines.txt.internal.open

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.provider.TxtSelectionManager
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.runtime.BreakResolver
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.engine.TextBreakPatchSupport
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher

internal class TxtSession(
    private val txtController: com.ireader.engines.txt.internal.render.TxtController,
    private val breakIndex: SoftBreakIndex,
    private val outlineProvider: TxtOutlineProvider,
    private val searchProvider: TxtSearchProviderPro,
    private val textProvider: TxtTextProvider,
    private val selectionManager: TxtSelectionManager,
    annotationsProvider: AnnotationProvider?
) : BaseReaderSession(
    id = SessionId(UUID.randomUUID().toString()),
    controller = txtController,
    outline = outlineProvider,
    search = searchProvider,
    text = textProvider,
    annotations = annotationsProvider,
    selection = selectionManager,
    selectionController = selectionManager
), TextBreakPatchSupport {
    init {
        outlineProvider.warmup()
        searchProvider.warmup()
    }

    override fun closeExtras() {
        runCatching { outlineProvider.close() }
        runCatching { searchProvider.close() }
        runCatching { breakIndex.close() }
    }

    override suspend fun applyBreakPatch(
        locator: com.ireader.reader.model.Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): com.ireader.reader.api.error.ReaderResult<RenderPage> {
        val result = txtController.applyBreakPatch(locator, direction, state)
        if (result is com.ireader.reader.api.error.ReaderResult.Ok) {
            outlineProvider.invalidate()
            searchProvider.invalidate()
            outlineProvider.warmup()
            searchProvider.warmup()
        }
        return result
    }

    override suspend fun clearBreakPatches(): com.ireader.reader.api.error.ReaderResult<RenderPage> {
        val result = txtController.clearBreakPatches()
        if (result is com.ireader.reader.api.error.ReaderResult.Ok) {
            outlineProvider.invalidate()
            searchProvider.invalidate()
            outlineProvider.warmup()
            searchProvider.warmup()
        }
        return result
    }

    companion object {
        fun create(
            controller: com.ireader.engines.txt.internal.render.TxtController,
            files: TxtBookFiles,
            meta: TxtMeta,
            blockIndex: TxtBlockIndex,
            breakIndex: SoftBreakIndex,
            breakResolver: BreakResolver,
            blockStore: BlockStore,
            ioDispatcher: CoroutineDispatcher,
            persistOutline: Boolean,
            annotationsProvider: AnnotationProvider?
        ): TxtSession {
            val outlineProvider = TxtOutlineProvider(
                files = files,
                meta = meta,
                blockIndex = blockIndex,
                breakResolver = breakResolver,
                blockStore = blockStore,
                ioDispatcher = ioDispatcher,
                persistOutline = persistOutline
            )
            val searchProvider = TxtSearchProviderPro(
                files = files,
                meta = meta,
                blockIndex = blockIndex,
                breakResolver = breakResolver,
                blockStore = blockStore,
                ioDispatcher = ioDispatcher
            )
            val textProvider = TxtTextProvider(
                blockIndex = blockIndex,
                revision = meta.contentRevision,
                blockStore = blockStore,
                breakResolver = breakResolver,
                ioDispatcher = ioDispatcher
            )
            val selectionManager = TxtSelectionManager(
                blockIndex = blockIndex,
                revision = meta.contentRevision,
                breakResolver = breakResolver,
                blockStore = blockStore,
                ioDispatcher = ioDispatcher
            )
            return TxtSession(
                txtController = controller,
                breakIndex = breakIndex,
                outlineProvider = outlineProvider,
                searchProvider = searchProvider,
                textProvider = textProvider,
                selectionManager = selectionManager,
                annotationsProvider = annotationsProvider
            )
        }
    }
}
