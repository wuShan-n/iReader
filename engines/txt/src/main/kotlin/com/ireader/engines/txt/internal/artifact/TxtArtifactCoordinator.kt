package com.ireader.engines.txt.internal.artifact

import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.txt.internal.locator.TxtProjectionVersion
import com.ireader.engines.txt.internal.open.TxtArtifactManifest
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

internal class TxtArtifactCoordinator(
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    private val projectionEngine: TextProjectionEngine,
    private val outlineProvider: TxtOutlineProvider,
    private val searchProvider: TxtSearchProviderPro,
    private val ioDispatcher: CoroutineDispatcher
) {
    fun warmupProviders() {
        searchProvider.warmup()
    }

    fun close() {
        runCatching { outlineProvider.close() }
        runCatching { searchProvider.close() }
    }

    suspend fun ensureBackgroundArtifactsReady() {
        val projectionVersion = TxtProjectionVersion.current(files, meta)
        var manifest = TxtArtifactManifest.readIfValid(
            file = files.manifestJson,
            meta = meta,
            expectedProjectionVersion = projectionVersion
        ) ?: TxtArtifactManifest.initial(meta, projectionVersion)
        manifest = ensureBlockIndexReady(manifest)
        ensureBreakMapReady(manifest)
    }

    fun refreshProjectionArtifacts() {
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
        warmupProviders()
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

    private companion object {
        private const val TXT_BLOCK_INDEX_VERSION = 1
        private const val SOFT_BREAK_MAP_VERSION = 7
    }
}
