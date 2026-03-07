package com.ireader.engines.txt.internal.runtime

import com.ireader.engines.txt.internal.softbreak.BreakMapState
import com.ireader.engines.txt.testing.createBookFiles
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class BreakPatchStoreTest {

    @Test
    fun `write and read should round-trip patches`() {
        val root = Files.createTempDirectory("break_patch_store_round_trip").toFile()
        try {
            val store = BreakPatchStore(
                files = createBookFiles(root),
                sampleHash = "sample-hash"
            )

            store.write(
                mapOf(
                    12L to BreakMapState.SOFT_SPACE,
                    25L to BreakMapState.HARD_PARAGRAPH
                )
            )

            assertEquals(
                mapOf(
                    12L to BreakMapState.SOFT_SPACE,
                    25L to BreakMapState.HARD_PARAGRAPH
                ),
                store.read()
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `read should ignore malformed or mismatched payloads`() {
        val root = Files.createTempDirectory("break_patch_store_invalid").toFile()
        try {
            val files = createBookFiles(root)
            val store = BreakPatchStore(
                files = files,
                sampleHash = "expected-hash"
            )

            files.breakPatch.writeText("{broken")
            assertEquals(emptyMap<Long, BreakMapState>(), store.read())

            files.breakPatch.writeText(
                """
                {
                  "version": 1,
                  "sampleHash": "other-hash",
                  "patches": {
                    "3": "SOFT_JOIN"
                  }
                }
                """.trimIndent()
            )
            assertEquals(emptyMap<Long, BreakMapState>(), store.read())
        } finally {
            root.deleteRecursively()
        }
    }
}
