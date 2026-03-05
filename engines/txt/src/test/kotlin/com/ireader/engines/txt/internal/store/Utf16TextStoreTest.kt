package com.ireader.engines.txt.internal.store

import com.ireader.engines.txt.testing.writeUtf16Text
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class Utf16TextStoreTest {

    @Test
    fun `readChars should align start when offset points to low surrogate`() {
        val root = Files.createTempDirectory("u16_store_align_start").toFile()
        val target = root.resolve("content.u16")
        writeUtf16Text(target, "A\uD83D\uDE00B")

        Utf16TextStore(target).use { store ->
            val chars = store.readChars(start = 2L, count = 2)
            assertEquals("\uD83D\uDE00", String(chars))
        }
        root.deleteRecursively()
    }

    @Test
    fun `readChars should trim count to avoid splitting surrogate pair`() {
        val root = Files.createTempDirectory("u16_store_align_count").toFile()
        val target = root.resolve("content.u16")
        writeUtf16Text(target, "A\uD83D\uDE00B")

        Utf16TextStore(target).use { store ->
            val chars = store.readChars(start = 0L, count = 2)
            assertEquals("A", String(chars))
        }
        root.deleteRecursively()
    }
}
