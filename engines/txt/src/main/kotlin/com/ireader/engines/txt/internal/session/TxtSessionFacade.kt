package com.ireader.engines.txt.internal.session

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.txt.internal.artifact.TxtArtifactCoordinator
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.provider.TxtSelectionManager
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.engine.TextBreakPatchSupport
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class TxtSessionFacade(
    private val txtController: com.ireader.engines.txt.internal.render.TxtController,
    private val projectionEngine: TextProjectionEngine,
    private val artifactCoordinator: TxtArtifactCoordinator,
    private val ioDispatcher: CoroutineDispatcher,
    annotationsProvider: AnnotationProvider?,
    outlineProvider: TxtOutlineProvider,
    searchProvider: TxtSearchProviderPro,
    textProvider: TxtTextProvider,
    selectionManager: TxtSelectionManager
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
    private val sessionScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        sessionScope.launch {
            txtController.events
                .filterIsInstance<ReaderEvent.Rendered>()
                .first()
            artifactCoordinator.warmupProviders()
            delay(BACKGROUND_ARTIFACT_DELAY_MS)
            artifactCoordinator.ensureBackgroundArtifactsReady()
        }
    }

    override fun closeExtras() {
        sessionScope.cancel()
        artifactCoordinator.close()
        runCatching { projectionEngine.close() }
    }

    override suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): com.ireader.reader.api.error.ReaderResult<RenderPage> {
        val result = txtController.applyBreakPatch(locator, direction, state)
        if (result is com.ireader.reader.api.error.ReaderResult.Ok) {
            artifactCoordinator.refreshProjectionArtifacts()
        }
        return result
    }

    override suspend fun clearBreakPatches(): com.ireader.reader.api.error.ReaderResult<RenderPage> {
        val result = txtController.clearBreakPatches()
        if (result is com.ireader.reader.api.error.ReaderResult.Ok) {
            artifactCoordinator.refreshProjectionArtifacts()
        }
        return result
    }

    companion object {
        private const val BACKGROUND_ARTIFACT_DELAY_MS = 150L

        fun create(
            controller: com.ireader.engines.txt.internal.render.TxtController,
            files: TxtBookFiles,
            meta: TxtMeta,
            blockIndex: TxtBlockIndex,
            projectionEngine: TextProjectionEngine,
            blockStore: BlockStore,
            ioDispatcher: CoroutineDispatcher,
            persistOutline: Boolean,
            annotationsProvider: AnnotationProvider?
        ): TxtSessionFacade {
            val outlineProvider = TxtOutlineProvider(
                files = files,
                meta = meta,
                blockIndex = blockIndex,
                projectionEngine = projectionEngine,
                blockStore = blockStore,
                ioDispatcher = ioDispatcher,
                persistOutline = persistOutline
            )
            val searchProvider = TxtSearchProviderPro(
                files = files,
                meta = meta,
                blockIndex = blockIndex,
                projectionEngine = projectionEngine,
                blockStore = blockStore,
                ioDispatcher = ioDispatcher
            )
            val textProvider = TxtTextProvider(
                blockIndex = blockIndex,
                contentFingerprint = meta.contentFingerprint,
                blockStore = blockStore,
                projectionEngine = projectionEngine,
                ioDispatcher = ioDispatcher
            )
            val selectionManager = TxtSelectionManager(
                blockIndex = blockIndex,
                contentFingerprint = meta.contentFingerprint,
                projectionEngine = projectionEngine,
                blockStore = blockStore,
                ioDispatcher = ioDispatcher
            )
            val artifactCoordinator = TxtArtifactCoordinator(
                files = files,
                meta = meta,
                projectionEngine = projectionEngine,
                outlineProvider = outlineProvider,
                searchProvider = searchProvider,
                ioDispatcher = ioDispatcher
            )
            return TxtSessionFacade(
                txtController = controller,
                projectionEngine = projectionEngine,
                artifactCoordinator = artifactCoordinator,
                ioDispatcher = ioDispatcher,
                annotationsProvider = annotationsProvider,
                outlineProvider = outlineProvider,
                searchProvider = searchProvider,
                textProvider = textProvider,
                selectionManager = selectionManager
            )
        }
    }
}
