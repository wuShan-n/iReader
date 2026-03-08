package com.ireader.engines.txt.internal.search

import com.ireader.engines.txt.internal.locator.TxtProjectionVersion
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.txt.testing.createBookFiles
import com.ireader.engines.txt.testing.writeUtf16Text
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrigramBloomIndexTest {

    @Test
    fun `openIfValid should return null when index file is corrupted`() {
        val root = Files.createTempDirectory("trigram_corrupt").toFile()
        val indexFile = root.resolve("tri_bloom.idx").apply { writeText("not-a-valid-index") }
        val meta = TxtMeta(
            version = 1,
            sourceUri = "file://book.txt",
            displayName = "book.txt",
            sizeBytes = 1024L,
            sampleHash = "hash",
            originalCharset = "UTF-8",
            lengthChars = 500L,
            hardWrapLikely = false,
            typicalLineLength = 72,
            createdAtEpochMs = 0L
        )

        val projectionVersion = TxtProjectionVersion.compute(meta, 0, meta.contentFingerprint)
        val opened = TrigramBloomIndex.openIfValid(indexFile, meta, projectionVersion)
        assertNull(opened)
        root.deleteRecursively()
    }

    @Test
    fun `mayContainAll with scratch should match legacy path`() = runBlocking {
        val root = Files.createTempDirectory("trigram_scratch").toFile()
        val files = createBookFiles(root)
        val text = buildString {
            repeat(35_000) { append("abcdefg hijklmn opqrst uvwxyz\n") }
            append("needle marker\n")
            repeat(35_000) { append("abcdefg hijklmn opqrst uvwxyz\n") }
        }
        writeUtf16Text(files.textStore, text)

        val store = Utf16TextStore(files.textStore)
        val meta = TxtMeta(
            version = 1,
            sourceUri = "file://book.txt",
            displayName = "book.txt",
            sizeBytes = text.length.toLong(),
            sampleHash = "hash",
            originalCharset = "UTF-8",
            lengthChars = store.lengthChars,
            hardWrapLikely = false,
            typicalLineLength = 72,
            createdAtEpochMs = 0L
        )

        try {
            val projectionVersion = TxtProjectionVersion.compute(meta, 0, meta.contentFingerprint)
            TrigramBloomIndex.buildIfNeeded(
                file = files.searchIdx,
                lockFile = files.searchLock,
                store = store,
                meta = meta,
                ioDispatcher = Dispatchers.IO
            )
            val opened = TrigramBloomIndex.openIfValid(files.searchIdx, meta, projectionVersion)
            checkNotNull(opened)

            val hashes = opened.buildQueryTrigramHashes("needle")
            val scratch = ByteArray(opened.bitsetBytes())

            RandomAccessFile(files.searchIdx, "r").use { raf ->
                var compared = 0
                for (blockIndex in 0 until opened.blocksCount()) {
                    val legacy = opened.mayContainAll(raf, blockIndex, hashes)
                    val withScratch = opened.mayContainAll(raf, blockIndex, hashes, scratch)
                    assertTrue("block=$blockIndex", legacy == withScratch)
                    compared++
                    if (compared >= 16) {
                        break
                    }
                }
            }
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }
}
