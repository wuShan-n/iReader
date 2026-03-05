package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.txt.testing.createBookFiles
import com.ireader.engines.txt.testing.writeUtf16Text
import com.ireader.reader.api.error.ReaderResult
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TxtOutlineProviderTest {

    @Test
    fun `getOutline should recover from corrupted cached json`() = runBlocking {
        val root = Files.createTempDirectory("txt_outline_cache_corrupt").toFile()
        val files = createBookFiles(root)
        val text = buildString {
            append("第1章 起始\n")
            append("这里是正文。\n")
            append("第2章 发展\n")
            append("这里是后续正文。\n")
        }
        writeUtf16Text(files.contentU16, text)
        val store = Utf16TextStore(files.contentU16)
        try {
            val provider = TxtOutlineProvider(
                files = files,
                meta = createMeta(store.lengthChars, text.length.toLong(), sampleHash = "outline-cache"),
                store = store,
                ioDispatcher = Dispatchers.IO,
                persistOutline = true
            )

            val first = provider.getOutline().requireOk()
            assertTrue(first.isNotEmpty())

            files.outlineJson.writeText("{broken")

            val second = provider.getOutline().requireOk()
            assertTrue(second.isNotEmpty())
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `getOutline should detect chapter title across chunk boundary`() = runBlocking {
        val root = Files.createTempDirectory("txt_outline_chunk_boundary").toFile()
        val files = createBookFiles(root)
        val text = "A".repeat(63_997) + "\n第123章 跨块标题\n正文继续"
        writeUtf16Text(files.contentU16, text)
        val store = Utf16TextStore(files.contentU16)
        try {
            val provider = TxtOutlineProvider(
                files = files,
                meta = createMeta(store.lengthChars, text.length.toLong(), sampleHash = "outline-boundary"),
                store = store,
                ioDispatcher = Dispatchers.IO,
                persistOutline = false
            )

            val outline = provider.getOutline().requireOk()
            assertTrue(outline.any { it.title.contains("第123章 跨块标题") })
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }

    private fun createMeta(lengthChars: Long, sizeBytes: Long, sampleHash: String): TxtMeta {
        return TxtMeta(
            version = 6,
            sourceUri = "file:///outline-test.txt",
            displayName = "outline-test.txt",
            sizeBytes = sizeBytes,
            sampleHash = sampleHash,
            originalCharset = "UTF-8",
            lengthChars = lengthChars,
            hardWrapLikely = false,
            createdAtEpochMs = 0L
        )
    }

    private fun <T> ReaderResult<T>.requireOk(): T {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }
}
