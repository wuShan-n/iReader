package com.ireader.engines.txt.internal.util

import com.ireader.engines.common.io.replaceFileAtomically
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AtomicFilesTest {

    @Test
    fun `replaceFileAtomically should fall back to copy when rename fails`() {
        val root = Files.createTempDirectory("atomic_files").toFile()
        val target = root.resolve("target.txt").apply { writeText("old") }
        val temp = root.resolve("temp.txt").apply { writeText("new") }

        replaceFileAtomically(
            tempFile = temp,
            targetFile = target,
            rename = { _, _ -> false }
        )

        assertEquals("new", target.readText())
        assertFalse(temp.exists())
        root.deleteRecursively()
    }
}
