package com.ireader.engines.txt.internal.pagination

import com.ireader.engines.common.io.writeVarLong
import com.ireader.engines.common.pagination.PageMap
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMapTest {

    @Test
    fun saveAndLoad_roundTripShouldPreserveSortedOffsets() {
        val dir = Files.createTempDirectory("pagemap_test").toFile()
        val file = dir.resolve("map.bin")
        try {
            PageMap.save(file, listOf(400L, 0L, 800L, 800L, 1200L))
            val loaded = PageMap.load(file)
            assertEquals(listOf(0L, 400L, 800L, 1200L), loaded.toList())
        } finally {
            file.delete()
            dir.delete()
        }
    }

    @Test
    fun load_shouldFallbackToLegacyText() {
        val dir = Files.createTempDirectory("pagemap_legacy_test").toFile()
        val bin = dir.resolve("map.bin")
        val legacy = dir.resolve("map.txt")
        try {
            legacy.writeText("0\n100\n200\n")
            val loaded = PageMap.load(binaryFile = bin, legacyTextFile = legacy)
            assertTrue(loaded.containsAll(listOf(0L, 100L, 200L)))
        } finally {
            bin.delete()
            legacy.delete()
            dir.delete()
        }
    }

    @Test
    fun load_shouldFallbackToLegacyWhenBinaryIsTruncated() {
        val dir = Files.createTempDirectory("pagemap_truncated_test").toFile()
        val bin = dir.resolve("map.bin")
        val legacy = dir.resolve("map.txt")
        try {
            PageMap.save(bin, listOf(0L, 100L, 200L, 300L))
            legacy.writeText("0\n100\n200\n400\n")
            RandomAccessFile(bin, "rw").use { raf ->
                val shorter = (raf.length() - 2L).coerceAtLeast(0L)
                raf.setLength(shorter)
            }

            val loaded = PageMap.load(binaryFile = bin, legacyTextFile = legacy)
            assertEquals(listOf(0L, 100L, 200L, 400L), loaded.toList())
        } finally {
            bin.delete()
            legacy.delete()
            dir.delete()
        }
    }

    @Test
    fun load_shouldSupportLegacyBinaryV1() {
        val dir = Files.createTempDirectory("pagemap_v1_test").toFile()
        val bin = dir.resolve("map.bin")
        try {
            RandomAccessFile(bin, "rw").use { raf ->
                raf.setLength(0L)
                raf.writeInt(0x504D4150) // MAGIC
                raf.writeInt(1) // VERSION_V1
                raf.writeInt(3) // count
                raf.writeVarLong(0L)
                raf.writeVarLong(100L)
                raf.writeVarLong(200L)
            }

            val loaded = PageMap.load(binaryFile = bin)
            assertEquals(listOf(0L, 100L, 300L), loaded.toList())
        } finally {
            bin.delete()
            dir.delete()
        }
    }
}
