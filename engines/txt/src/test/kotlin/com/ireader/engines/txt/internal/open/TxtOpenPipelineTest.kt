package com.ireader.engines.txt.internal.open

import android.net.Uri
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.testing.InMemoryDocumentSource
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `open should report annotation capability when factory is provided`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_annotation_capability").toFile()
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/annotation.txt"),
                payload = sampleText().toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(
                TxtEngineConfig(
                    cacheDir = cacheDir,
                    annotationProviderFactory = { documentId ->
                        NoopAnnotationProvider(documentId)
                    }
                )
            )

            val opened = engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk()
            try {
                assertTrue(opened.capabilities.annotations)
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
    fun `open should produce different cache entries when only tail content changes`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_cache_tail_change").toFile()
            val head = "A".repeat(80_000)
            val middle = "中".repeat(30_000)
            val tailA = "尾部A".repeat(4_000)
            val tailB = "尾部B".repeat(4_000)
            val payloadA = (head + middle + tailA).toByteArray(Charsets.UTF_8)
            val payloadB = (head + middle + tailB).toByteArray(Charsets.UTF_8)
            assertEquals(payloadA.size, payloadB.size)

            val sourceA = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/tail-change.txt"),
                payload = payloadA
            )
            val sourceB = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/tail-change.txt"),
                payload = payloadB
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            engine.open(sourceA, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            engine.open(sourceB, OpenOptions(textEncoding = "UTF-8")).requireOk().close()

            val metaFiles = readMetaFiles(cacheDir)
            assertEquals(2, metaFiles.size)
            val sampleHashes = metaFiles.map { JSONObject(it.readText()).getString("sampleHash") }.toSet()
            assertEquals(2, sampleHashes.size)
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

    @Test
    fun `open should mark fixed-width wrapped text as hard wrap likely`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_hard_wrap_detect").toFile()
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/hard-wrap.txt"),
                payload = fixedWidthWrappedText().toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val meta = readSingleMeta(cacheDir)

            assertTrue(meta.getBoolean("hardWrapLikely"))
            assertEquals(6, meta.getInt("version"))
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `open should mark short stable wrapped text as hard wrap likely`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_short_wrap_detect").toFile()
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/short-wrap.txt"),
                payload = shortStableWrappedText().toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val meta = readSingleMeta(cacheDir)

            assertTrue(meta.getBoolean("hardWrapLikely"))
            assertEquals(6, meta.getInt("version"))
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `open should rebuild cache when cached schema version is outdated`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_cache_schema_upgrade").toFile()
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/schema-upgrade.txt"),
                payload = sampleText().toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val metaFile = readSingleMetaFile(cacheDir)
            val firstMeta = JSONObject(metaFile.readText())
            val firstCreatedAt = firstMeta.getLong("createdAtEpochMs")

            val outdated = JSONObject(firstMeta.toString()).apply { put("version", 1) }
            metaFile.writeText(outdated.toString())

            Thread.sleep(20L)
            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val rebuiltMeta = readSingleMeta(cacheDir)

            assertEquals(6, rebuiltMeta.getInt("version"))
            assertTrue(rebuiltMeta.getLong("createdAtEpochMs") > firstCreatedAt)
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `open should rebuild cache when cached meta json is corrupted`() {
        runBlocking {
            val cacheDir = Files.createTempDirectory("txt_cache_corrupted_meta").toFile()
            val source = InMemoryDocumentSource(
                uri = Uri.parse("file:///books/corrupted-meta.txt"),
                payload = sampleText().toByteArray(Charsets.UTF_8)
            )
            val engine = TxtEngine(TxtEngineConfig(cacheDir = cacheDir))

            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val metaFile = readSingleMetaFile(cacheDir)
            val firstCreatedAt = JSONObject(metaFile.readText()).getLong("createdAtEpochMs")

            metaFile.writeText("{broken")

            Thread.sleep(20L)
            engine.open(source, OpenOptions(textEncoding = "UTF-8")).requireOk().close()
            val rebuiltMeta = readSingleMeta(cacheDir)

            assertEquals(6, rebuiltMeta.getInt("version"))
            assertTrue(rebuiltMeta.getLong("createdAtEpochMs") > firstCreatedAt)
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

    private fun fixedWidthWrappedText(): String {
        return buildString {
            repeat(180) { idx ->
                append("这是固定宽度换行样本第")
                append(idx + 1)
                append("行用于检测硬换行概率并保持行宽稳定")
                if (idx < 179) {
                    append('\n')
                }
            }
        }
    }

    private fun shortStableWrappedText(): String {
        return buildString {
            repeat(220) { idx ->
                append("道人提笔画符第")
                append((idx % 9) + 1)
                append("行续写")
                if (idx % 3 == 0) {
                    append("。")
                }
                if (idx < 219) {
                    append('\n')
                }
            }
        }
    }

    private fun readSingleMeta(cacheDir: File): JSONObject {
        return JSONObject(readSingleMetaFile(cacheDir).readText())
    }

    private fun readSingleMetaFile(cacheDir: File): File {
        val matches = readMetaFiles(cacheDir)
        assertEquals(1, matches.size)
        val file = matches.firstOrNull()
        assertNotNull(file)
        return file!!
    }

    private fun readMetaFiles(cacheDir: File): List<File> {
        return cacheDir
            .walkTopDown()
            .filter { it.isFile && it.name == "meta.json" }
            .toList()
    }

    private fun <T> ReaderResult<T>.requireOk(): T {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }
}

private class NoopAnnotationProvider(
    private val documentId: DocumentId
) : AnnotationProvider {
    private val state = MutableStateFlow<List<Annotation>>(emptyList())

    override fun observeAll(): Flow<List<Annotation>> = state

    override suspend fun listAll(): ReaderResult<List<Annotation>> = ReaderResult.Ok(state.value)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> = ReaderResult.Ok(state.value)

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> {
        val now = System.currentTimeMillis()
        val created = Annotation(
            id = AnnotationId("${documentId.value}:noop"),
            type = draft.type,
            anchor = draft.anchor,
            content = draft.content,
            style = draft.style,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            extra = draft.extra
        )
        state.value = state.value + created
        return ReaderResult.Ok(created)
    }

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> =
        ReaderResult.Ok(emptyList())
}
