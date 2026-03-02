package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.TxtEngineConfig
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtOutlineCacheTest {

    @Test
    fun loadFromDisk_parses_v2_format_only() {
        val dir = createTempDirectory(prefix = "txt-outline-v2-").toFile()
        try {
            val cache = TxtOutlineCache(
                config = TxtEngineConfig(
                    cacheDir = dir,
                    persistOutline = true,
                    outlineAsTree = false
                ),
                docNamespace = "doc-v2",
                charsetName = "UTF-8"
            )
            val file = outlineFile(dir, "doc-v2", "UTF-8")
            file.parentFile?.mkdirs()
            file.writeText(
                "1\t10\tChapter 1\n2\t20\tSection 1\n",
                Charsets.UTF_8
            )

            val nodes = cache.loadFromDisk().orEmpty()

            assertEquals(2, nodes.size)
            assertEquals("Chapter 1", nodes[0].title)
            assertEquals("10", nodes[0].locator.value)
            assertEquals("Section 1", nodes[1].title)
            assertEquals("20", nodes[1].locator.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun loadFromDisk_ignores_legacy_flat_format_lines() {
        val dir = createTempDirectory(prefix = "txt-outline-legacy-").toFile()
        try {
            val cache = TxtOutlineCache(
                config = TxtEngineConfig(
                    cacheDir = dir,
                    persistOutline = true,
                    outlineAsTree = false
                ),
                docNamespace = "doc-legacy",
                charsetName = "UTF-8"
            )
            val file = outlineFile(dir, "doc-legacy", "UTF-8")
            file.parentFile?.mkdirs()
            file.writeText(
                "10\tOld Flat Title\n",
                Charsets.UTF_8
            )

            val nodes = cache.loadFromDisk().orEmpty()

            assertTrue(nodes.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun outlineFile(baseDir: File, docNamespace: String, charset: String): File {
        val folder = File(baseDir, "reader-txt-v2/outline")
        return File(folder, "${docNamespace.hashCode()}_${charset.hashCode()}.txt")
    }
}

