package com.ireader.engines.txt.internal.open

import android.net.Uri
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.testing.InMemoryDocumentSource
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TxtOpenPipelineTest {

    @Test
    fun `open should expose metadata and create session`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_open_pipeline").toFile()
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/pipeline.txt"),
                payload = sampleText().toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(
                TxtEngineConfig(
                    cacheDir = cacheDir,
                    ioDispatcher = Dispatchers.IO,
                    defaultDispatcher = Dispatchers.Default
                )
            )

            val opened = engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk()
            try {
                assertFalse(opened.capabilities.annotations)
                val metadata = opened.metadata().requireOk()
                assertEquals("UTF-8", metadata.extra["charset"])

                val session = opened.createSession(initialLocator = null, initialConfig = RenderConfig.ReflowText()).requireOk()
                session.close()
            } finally {
                opened.close()
                cacheDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `open should reuse cached meta when source and encoding are unchanged`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_cache_reuse").toFile()
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/reuse.txt"),
                payload = sampleText().toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val firstMeta = readSingleMeta(cacheDir)
            val firstCreatedAt = firstMeta.getLong("createdAtEpochMs")

            Thread.sleep(20L)
            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val secondMeta = readSingleMeta(cacheDir)
            val secondCreatedAt = secondMeta.getLong("createdAtEpochMs")

            assertEquals(firstCreatedAt, secondCreatedAt)
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `open should rebuild cached content when explicit encoding changes`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_cache_rebuild").toFile()
            val payload = "hello\\nworld\\nrepeat\\n".repeat(40).toByteArray(Charsets.UTF_8)
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/rebuild.txt"),
                payload = payload
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val firstMeta = readSingleMeta(cacheDir)
            val firstCreatedAt = firstMeta.getLong("createdAtEpochMs")
            assertEquals("UTF-8", firstMeta.getString("originalCharset"))

            Thread.sleep(20L)
            engine.open(source, OpenOptions(textEncoding = "UTF-16LE")).requireOk().close()
            val secondMeta = readSingleMeta(cacheDir)
            val secondCreatedAt = secondMeta.getLong("createdAtEpochMs")

            assertTrue(secondCreatedAt > firstCreatedAt)
            assertEquals("UTF-16LE", secondMeta.getString("originalCharset"))
            cacheDir.deleteRecursively()
        }
    }

    private fun sampleText(): String {
        return buildString {
            repeat(160) { idx ->
                append("Chapter ${idx + 1} A simple paragraph used for opener tests.")
                append('\n')
            }
        }
    }

    private fun readSingleMeta(cacheDir: File): JSONObject {
        val matches = cacheDir
            .walkTopDown()
            .filter { it.isFile && it.name == "meta.json" }
            .toList()
        assertEquals(1, matches.size)
        val file = matches.firstOrNull()
        assertNotNull(file)
        return JSONObject(file!!.readText())
    }

    private fun <T> ReaderResult<T>.requireOk(): T {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }
}
