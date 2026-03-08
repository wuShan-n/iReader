package com.ireader.engines.txt.internal.open

import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.txt.internal.locator.TxtProjectionVersion
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.provider.TxtSelectionManager
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.engine.TextBreakPatchSupport
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.model.SessionId
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class TxtSession(
    private val txtController: com.ireader.engines.txt.internal.render.TxtController,
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    private val projectionEngine: TextProjectionEngine,
    private val outlineProvider: TxtOutlineProvider,
    private val searchProvider: TxtSearchProviderPro,
    private val textProvider: TxtTextProvider,
    private val selectionManager: TxtSelectionManager,
    private val ioDispatcher: CoroutineDispatcher,
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
    private val sessionScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        sessionScope.launch {
            txtController.events
                .filterIsInstance<ReaderEvent.Rendered>()
                .first()
            outlineProvider.warmup()
            searchProvider.warmup()
            delay(BACKGROUND_ARTIFACT_DELAY_MS)
            ensureBackgroundArtifactsReady()
        }
    }

    override fun closeExtras() {
        sessionScope.cancel()
        runCatching { outlineProvider.close() }
        runCatching { searchProvider.close() }
        runCatching { projectionEngine.close() }
    }

    override suspend fun applyBreakPatch(
        locator: com.ireader.reader.model.Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): com.ireader.reader.api.error.ReaderResult<RenderPage> {
        val result = txtController.applyBreakPatch(locator, direction, state)
        if (result is com.ireader.reader.api.error.ReaderResult.Ok) {
            refreshProjectionArtifacts()
        }
        return result
    }

    override suspend fun clearBreakPatches(): com.ireader.reader.api.error.ReaderResult<RenderPage> {
        val result = txtController.clearBreakPatches()
        if (result is com.ireader.reader.api.error.ReaderResult.Ok) {
            refreshProjectionArtifacts()
        }
        return result
    }

    private suspend fun ensureBackgroundArtifactsReady() {
        val projectionVersion = TxtProjectionVersion.current(files, meta)
        var manifest = TxtArtifactManifest.readIfValid(
            file = files.manifestJson,
            meta = meta,
            expectedProjectionVersion = projectionVersion
        ) ?: TxtArtifactManifest.initial(meta, projectionVersion)
        manifest = ensureBlockIndexReady(manifest)
        ensureBreakMapReady(manifest)
    }

    private suspend fun ensureBlockIndexReady(manifest: TxtArtifactManifest): TxtArtifactManifest {
        if (manifest.blockIndexReady && TxtBlockIndex.openIfValid(files.blockIdx, meta) != null) {
            return manifest
        }

        Utf16TextStore(files.textStore).use { store ->
            TxtBlockIndex.buildIfNeeded(
                file = files.blockIdx,
                lockFile = files.blockLock,
                store = store,
                meta = meta,
                ioDispatcher = ioDispatcher
            )
        }

        val nextManifest = if (TxtBlockIndex.openIfValid(files.blockIdx, meta) != null) {
            manifest.markBlockIndexReady(TXT_BLOCK_INDEX_VERSION)
        } else {
            manifest.copy(blockIndexVersion = null, blockIndexReady = false)
        }
        writeArtifactManifest(nextManifest)
        return nextManifest
    }

    private suspend fun ensureBreakMapReady(manifest: TxtArtifactManifest) {
        val nextManifest = if (!projectionEngine.hasIndexedBreaks()) {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = ioDispatcher,
                profile = SoftBreakTuningProfile.BALANCED
            )
            val builtIndex = SoftBreakIndex.openIfValid(
                file = files.breakMap,
                meta = meta,
                profile = SoftBreakTuningProfile.BALANCED,
                rulesVersion = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED).rulesVersion
            )
            if (builtIndex != null) {
                projectionEngine.attachIndex(builtIndex)
                manifest.markBreakMapReady(SOFT_BREAK_MAP_VERSION)
            } else {
                manifest.copy(breakMapVersion = null, breakMapReady = false)
            }
        } else if (!manifest.breakMapReady) {
            manifest.markBreakMapReady(SOFT_BREAK_MAP_VERSION)
        } else {
            manifest
        }

        if (nextManifest != manifest) {
            writeArtifactManifest(nextManifest)
        }
    }

    private fun writeArtifactManifest(manifest: TxtArtifactManifest) {
        val temp = File(files.bookDir, "manifest.json.tmp")
        prepareTempFile(temp)
        temp.writeText(manifest.toJson().toString())
        replaceFileAtomically(tempFile = temp, targetFile = files.manifestJson)
    }

    private fun refreshProjectionArtifacts() {
        outlineProvider.invalidate()
        searchProvider.invalidate()
        val hasBlockIndex = TxtBlockIndex.openIfValid(files.blockIdx, meta) != null
        val projectionVersion = TxtProjectionVersion.current(files, meta)
        writeArtifactManifest(
            TxtArtifactManifest(
                version = TxtArtifactManifest.VERSION,
                sampleHash = meta.sampleHash,
                contentRevision = meta.contentRevision,
                projectionVersion = projectionVersion,
                blockIndexVersion = if (hasBlockIndex) TXT_BLOCK_INDEX_VERSION else null,
                breakMapVersion = if (projectionEngine.hasIndexedBreaks()) SOFT_BREAK_MAP_VERSION else null,
                blockIndexReady = hasBlockIndex,
                breakMapReady = projectionEngine.hasIndexedBreaks()
            )
        )
        outlineProvider.warmup()
        searchProvider.warmup()
    }

    companion object {
        private const val TXT_BLOCK_INDEX_VERSION = 1
        private const val SOFT_BREAK_MAP_VERSION = 7
        private const val BACKGROUND_ARTIFACT_DELAY_MS = 1_200L

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
        ): TxtSession {
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
            return TxtSession(
                txtController = controller,
                files = files,
                meta = meta,
                projectionEngine = projectionEngine,
                outlineProvider = outlineProvider,
                searchProvider = searchProvider,
                textProvider = textProvider,
                selectionManager = selectionManager,
                ioDispatcher = ioDispatcher,
                annotationsProvider = annotationsProvider
            )
        }
    }
}
