package com.ireader.engines.txt.testing

import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.locator.TextAnchorAffinity
import com.ireader.engines.txt.internal.locator.TxtAnchorLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.runtime.BreakResolver
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.model.Locator
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineDispatcher

internal data class TxtRuntimeFixture(
    val rootDir: File,
    val files: TxtBookFiles,
    val store: Utf16TextStore,
    val meta: TxtMeta,
    val blockIndex: TxtBlockIndex,
    val breakIndex: SoftBreakIndex,
    val breakResolver: BreakResolver,
    val blockStore: BlockStore,
    val sourceText: String
) {
    fun locatorFor(offset: Long, affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD): Locator {
        return TxtAnchorLocatorCodec.locatorForOffset(
            offset = offset,
            blockIndex = blockIndex,
            revision = meta.contentRevision,
            affinity = affinity
        )
    }

    fun parseOffset(locator: Locator): Long? {
        return TxtAnchorLocatorCodec.parseOffset(
            locator = locator,
            blockIndex = blockIndex,
            expectedRevision = meta.contentRevision,
            maxOffset = blockIndex.lengthCodeUnits
        )
    }

    fun close() {
        runCatching { breakIndex.close() }
        runCatching { store.close() }
        rootDir.deleteRecursively()
    }
}

internal suspend fun buildTxtRuntimeFixture(
    text: String,
    sampleHash: String,
    ioDispatcher: CoroutineDispatcher,
    rootDir: File = Files.createTempDirectory("txt_runtime_fixture").toFile()
): TxtRuntimeFixture {
    val files = createBookFiles(rootDir)
    writeUtf16Text(files.textStore, text)
    val store = Utf16TextStore(files.textStore)
    val meta = TxtMeta(
        version = 7,
        sourceUri = "file:///$sampleHash.txt",
        displayName = "$sampleHash.txt",
        sizeBytes = text.length.toLong(),
        sampleHash = sampleHash,
        originalCharset = "UTF-8",
        lengthChars = store.lengthChars,
        hardWrapLikely = false,
        createdAtEpochMs = 0L
    )
    SoftBreakIndexBuilder.buildIfNeeded(
        files = files,
        meta = meta,
        ioDispatcher = ioDispatcher,
        profile = SoftBreakTuningProfile.BALANCED
    )
    TxtBlockIndex.buildIfNeeded(
        file = files.blockIdx,
        store = store,
        meta = meta,
        ioDispatcher = ioDispatcher
    )
    val blockIndex = requireNotNull(TxtBlockIndex.openIfValid(files.blockIdx, meta)) {
        "Missing block index"
    }
    val breakIndex = requireNotNull(
        SoftBreakIndex.openIfValid(
            file = files.breakMap,
            meta = meta,
            profile = SoftBreakTuningProfile.BALANCED,
            rulesVersion = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED).rulesVersion
        )
    ) {
        "Missing break map"
    }
    val breakResolver = BreakResolver(
        store = store,
        files = files,
        meta = meta,
        breakIndex = breakIndex
    )
    val blockStore = BlockStore(
        store = store,
        blockIndex = blockIndex,
        revision = meta.contentRevision,
        breakResolver = breakResolver
    )
    return TxtRuntimeFixture(
        rootDir = rootDir,
        files = files,
        store = store,
        meta = meta,
        blockIndex = blockIndex,
        breakIndex = breakIndex,
        breakResolver = breakResolver,
        blockStore = blockStore,
        sourceText = text
    )
}
