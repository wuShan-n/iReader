package com.ireader.engines.txt.internal.pagination

import com.ireader.engines.common.android.reflow.SoftBreakProcessor
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.open.Utf16LeFileWriter
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoftBreakStateConsistencyTest {

    @Test
    fun `runtime classifier and index classifier should stay consistent on part heading`() = runBlocking {
        val raw = buildString {
            append("This wrapped prose line keeps similar width and intentionally skips punctuation at the end")
            append('\n')
            append("Part 1 The Return")
            append('\n')
            append("This wrapped prose line keeps similar width and intentionally skips punctuation in continuation")
        }
        val runtime = SoftBreakProcessor.process(
            rawText = raw,
            hardWrapLikely = true,
            paragraphSpacingPx = 0,
            paragraphIndentPx = 0,
            startsAtParagraphBoundary = true
        ).toString()
        val indexed = indexedNormalization(
            raw = raw,
            hardWrapLikely = true
        )

        val firstNewline = raw.indexOf('\n')
        assertEquals(runtime, indexed)
        assertEquals(' ', runtime[firstNewline])
        assertEquals(' ', indexed[firstNewline])
    }

    private suspend fun indexedNormalization(
        raw: String,
        hardWrapLikely: Boolean
    ): String {
        val dir = Files.createTempDirectory("txt_softbreak_state_consistency").toFile()
        val files = createBookFiles(dir)
        Utf16LeFileWriter(files.textStore).use { writer ->
            raw.forEach(writer::writeChar)
        }
        val profile = SoftBreakTuningProfile.BALANCED
        val meta = TxtMeta(
            version = 2,
            sourceUri = "file:///books/state-consistency.txt",
            displayName = "state-consistency.txt",
            sizeBytes = raw.length.toLong(),
            sampleHash = "sample-state-consistency",
            originalCharset = "UTF-8",
            lengthChars = raw.length.toLong(),
            hardWrapLikely = hardWrapLikely,
            createdAtEpochMs = 0L
        )
        try {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = Dispatchers.IO,
                profile = profile
            )
            val index = SoftBreakIndex.openIfValid(
                file = files.breakMap,
                meta = meta,
                profile = profile,
                rulesVersion = SoftBreakRuleConfig.forProfile(profile).rulesVersion
            ) ?: error("expected valid soft-break index")
            index.use { loaded ->
                val chars = raw.toCharArray()
                loaded.forEachNewlineInRange(0L, raw.length.toLong()) { offset, isSoft ->
                    if (!isSoft) {
                        return@forEachNewlineInRange
                    }
                    val local = offset.toInt()
                    if (local in chars.indices && chars[local] == '\n') {
                        chars[local] = ' '
                    }
                }
                return String(chars)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createBookFiles(root: File): TxtBookFiles {
        val paginationDir = File(root, "pagination").apply { mkdirs() }
        return TxtBookFiles(
            bookDir = root,
            lockFile = File(root, "book.lock"),
            textStore = File(root, "text.store"),
            metaJson = File(root, "meta.json"),
            outlineIdx = File(root, "outline.idx"),
            paginationDir = paginationDir,
            breakMap = File(root, "break.map"),
            breakLock = File(root, "break.lock"),
            searchIdx = File(root, "search.idx"),
            searchLock = File(root, "search.lock"),
            blockIdx = File(root, "block.idx"),
            breakPatch = File(root, "break.patch")
        )
    }
}
