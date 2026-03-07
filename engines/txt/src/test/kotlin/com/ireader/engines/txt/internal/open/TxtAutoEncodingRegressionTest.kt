package com.ireader.engines.txt.internal.open

import android.net.Uri
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.testing.InMemoryDocumentSource
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TxtAutoEncodingRegressionTest {

    @Test
    fun `auto detect should not assume utf8 when first window is ascii only`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_auto_gbk_ascii_header").toFile()
            val gbk = Charset.forName("GBK")
            val text = "A".repeat(300_000) + "中文测试段落".repeat(2000)
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/auto-gbk.txt"),
                payload = text.toByteArray(gbk)
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            val opened = engine.open(source, OpenOptions()).requireOk()
            try {
                val metadata = opened.metadata().requireOk()
                val detected = metadata.extra["charset"]
                assertEquals("GB18030", detected)

                val decoded = readSingleCacheContent(cacheDir)
                assertTrue(decoded.contains("中文测试段落"))
            } finally {
                opened.close()
                cacheDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `auto detect should still pick utf8 for utf8 content`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_auto_utf8").toFile()
            val text = "A".repeat(300_000) + "中文UTF8段落".repeat(2000)
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/auto-utf8.txt"),
                payload = text.toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            val opened = engine.open(source, OpenOptions()).requireOk()
            try {
                val metadata = opened.metadata().requireOk()
                val detected = metadata.extra["charset"]
                assertEquals("UTF-8", detected)

                val decoded = readSingleCacheContent(cacheDir)
                assertTrue(decoded.contains("中文UTF8段落"))
            } finally {
                opened.close()
                cacheDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `auto detect should not be confused by utf8 window boundaries`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_auto_utf8_boundaries").toFile()
            val text = "中".repeat(120_000)
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/auto-utf8-boundaries.txt"),
                payload = text.toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            val opened = engine.open(source, OpenOptions()).requireOk()
            try {
                val metadata = opened.metadata().requireOk()
                val detected = metadata.extra["charset"]
                assertEquals("UTF-8", detected)

                val decoded = readSingleCacheContent(cacheDir)
                assertTrue(decoded.startsWith("中中中"))
            } finally {
                opened.close()
                cacheDir.deleteRecursively()
            }
        }
    }

    private fun readSingleCacheContent(cacheDir: File): String {
        val matches = cacheDir
            .walkTopDown()
            .filter { it.isFile && it.name == "content.u16" }
            .toList()
        assertEquals(1, matches.size)
        val file = matches.firstOrNull()
        assertNotNull(file)
        return String(file!!.readBytes(), Charsets.UTF_16LE)
    }

    private fun <T> ReaderResult<T>.requireOk(): T {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }
}
