package com.ireader.engines.txt.internal.pagination

import com.ireader.engines.common.pagination.PageMap
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
}
