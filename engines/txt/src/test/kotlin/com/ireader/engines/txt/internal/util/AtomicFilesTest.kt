package com.ireader.engines.txt.internal.util

import com.ireader.engines.common.io.replaceFileAtomically
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
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

    @Test
    fun `replaceFileAtomically should restore old target when replace fails`() {
        val root = Files.createTempDirectory("atomic_files_restore").toFile()
        val target = root.resolve("target.txt").apply { writeText("old") }
        val temp = root.resolve("temp.txt").apply { writeText("new") }
        temp.delete()

        try {
            replaceFileAtomically(
                tempFile = temp,
                targetFile = target,
                rename = { _, _ -> false }
            )
            fail("Expected IOException")
        } catch (_: IOException) {
            // expected
        }

        assertEquals("old", target.readText())
        assertFalse(root.resolve("target.txt.bak").exists())
        root.deleteRecursively()
    }
}
