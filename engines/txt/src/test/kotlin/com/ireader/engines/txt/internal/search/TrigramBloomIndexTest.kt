package com.ireader.engines.txt.internal.search

import com.ireader.engines.txt.internal.open.TxtMeta
import java.nio.file.Files
import org.junit.Assert.assertNull
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
            createdAtEpochMs = 0L
        )

        val opened = TrigramBloomIndex.openIfValid(indexFile, meta)
        assertNull(opened)
        root.deleteRecursively()
    }
}
