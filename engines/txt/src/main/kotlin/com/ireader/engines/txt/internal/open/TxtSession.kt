package com.ireader.engines.txt.internal.open

import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.txt.internal.open.TxtArtifactManifest.Companion.initial
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.provider.TxtSelectionManager
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.runtime.BreakResolver
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class TxtSession(
    private val txtController: com.ireader.engines.txt.internal.render.TxtController,
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    private val breakResolver: BreakResolver,
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
            ensureDerivedArtifactsReady()
        }
    }

    override fun closeExtras() {
        sessionScope.cancel()
        runCatching { outlineProvider.close() }
        runCatching { searchProvider.close() }
        runCatching { breakResolver.close() }
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

    private suspend fun ensureDerivedArtifactsReady() {
        var manifest = TxtArtifactManifest.readIfValid(files.manifestJson, meta) ?: initial(meta)
        if (!manifest.blockIndexReady || TxtBlockIndex.openIfValid(files.blockIdx, meta) == null) {
            Utf16TextStore(files.textStore).use { store ->
                TxtBlockIndex.buildIfNeeded(
                    file = files.blockIdx,
                    lockFile = files.blockLock,
                    store = store,
                    meta = meta,
                    ioDispatcher = ioDispatcher
                )
            }
            manifest = if (TxtBlockIndex.openIfValid(files.blockIdx, meta) != null) {
                manifest.markBlockIndexReady(TXT_BLOCK_INDEX_VERSION)
            } else {
                manifest.copy(blockIndexVersion = null, blockIndexReady = false)
            }
            writeArtifactManifest(manifest)
        }
        if (!breakResolver.hasIndexedBreaks()) {
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
                breakResolver.attachIndex(builtIndex)
                manifest = manifest.markBreakMapReady(SOFT_BREAK_MAP_VERSION)
            } else {
                manifest = manifest.copy(breakMapVersion = null, breakMapReady = false)
            }
            writeArtifactManifest(manifest)
        } else if (!manifest.breakMapReady) {
            writeArtifactManifest(manifest.markBreakMapReady(SOFT_BREAK_MAP_VERSION))
        }
        searchProvider.warmup()
        outlineProvider.warmup()
    }

    private fun writeArtifactManifest(manifest: TxtArtifactManifest) {
        val temp = File(files.bookDir, "manifest.json.tmp")
        prepareTempFile(temp)
        temp.writeText(manifest.toJson().toString())
        replaceFileAtomically(tempFile = temp, targetFile = files.manifestJson)
    }

    companion object {
        private const val TXT_BLOCK_INDEX_VERSION = 1
        private const val SOFT_BREAK_MAP_VERSION = 7

        fun create(
            controller: com.ireader.engines.txt.internal.render.TxtController,
            files: TxtBookFiles,
            meta: TxtMeta,
            blockIndex: TxtBlockIndex,
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
                files = files,
                meta = meta,
                breakResolver = breakResolver,
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
