package com.ireader.engines.txt.contract

import android.net.Uri
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.testing.InMemoryDocumentSource
import com.ireader.engines.txt.testing.TxtRuntimeFixture
import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationType
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.runtime.DefaultReaderRuntime
import com.ireader.reader.runtime.ReaderHandle
import com.ireader.reader.testkit.TestTextLayouterFactory
import com.ireader.reader.testkit.contract.ReaderContractHarness
import com.ireader.reader.testkit.contract.ReaderHandleContractSuite
import com.ireader.reader.testkit.requireOk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TxtReaderHandleContractTest : ReaderHandleContractSuite() {
    override fun createHarness(): ReaderContractHarness = TxtContractHarness()
}

private class TxtContractHarness : ReaderContractHarness {
    private val rootDir: File = Files.createTempDirectory("txt_reader_contract").toFile()
    private val locatorFixtureRoot = File(rootDir, "locator-fixture")
    private val engineCacheDir = File(rootDir, "engine-cache").apply { mkdirs() }
    private val sampleText = buildSampleText()
    private val annotationProvider = InMemoryAnnotationProvider()
    private val fixture: TxtRuntimeFixture = kotlinx.coroutines.runBlocking {
        buildTxtRuntimeFixture(
            text = sampleText,
            sampleHash = "txt-reader-contract",
            ioDispatcher = Dispatchers.IO,
            rootDir = locatorFixtureRoot
        )
    }
    private val runtime = DefaultReaderRuntime(
        engineRegistry = TxtOnlyRegistry(
            TxtEngine(
                TxtEngineConfig(
                    cacheDir = engineCacheDir,
                    persistPagination = false,
                    persistOutline = false,
                    annotationProviderFactory = { annotationProvider }
                )
            )
        )
    )
    private val source = InMemoryDocumentSource(
        uri = Uri.parse("content://contract/book.txt"),
        payload = sampleText.toByteArray(Charsets.UTF_8),
        displayName = "contract.txt"
    )

    override val defaultLayout: LayoutConstraints = LayoutConstraints(
        viewportWidthPx = 1080,
        viewportHeightPx = 1920,
        density = 3f,
        fontScale = 1f
    )
    override val defaultLayouterFactory = TestTextLayouterFactory("txt-contract")
    override val searchQuery: String = "needle"
    override val expectedSearchExcerpt: String = "needle"
    override val expectedOutlineTitle: String = "第12章"
    override val selectionStartOffset: Long = 0L
    override val selectionEndOffset: Long = 64L

    override suspend fun openSession(): ReaderHandle {
        return runtime.openSession(
            source = source,
            options = com.ireader.reader.api.open.OpenOptions(hintFormat = BookFormat.TXT),
            initialConfig = RenderConfig.ReflowText()
        ).requireOk()
    }

    override fun locatorAt(offset: Long): Locator = fixture.locatorFor(offset)

    override fun close() {
        fixture.close()
        rootDir.deleteRecursively()
    }
}

private class TxtOnlyRegistry(
    private val engine: ReaderEngine
) : EngineRegistry {
    override fun engineFor(format: BookFormat): ReaderEngine? {
        return if (format == BookFormat.TXT) engine else null
    }
}

private class InMemoryAnnotationProvider : AnnotationProvider {
    private val annotations = MutableStateFlow<List<Annotation>>(emptyList())

    override fun observeAll(): Flow<List<Annotation>> = annotations

    override suspend fun listAll(): ReaderResult<List<Annotation>> = ReaderResult.Ok(annotations.value)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> {
        return ReaderResult.Ok(annotations.value)
    }

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> {
        val annotation = Annotation(
            id = AnnotationId("ann-${annotations.value.size + 1}"),
            type = draft.type,
            anchor = draft.anchor,
            content = draft.content,
            createdAtEpochMs = annotations.value.size.toLong() + 1L,
            updatedAtEpochMs = annotations.value.size.toLong() + 1L,
            extra = draft.extra
        )
        annotations.value = annotations.value + annotation
        return ReaderResult.Ok(annotation)
    }

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> {
        annotations.value = annotations.value.map { current ->
            if (current.id == annotation.id) annotation else current
        }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> {
        annotations.value = annotations.value.filterNot { it.id == id }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        return ReaderResult.Ok(emptyList())
    }
}

private fun buildSampleText(): String {
    return buildString {
        append("第1章 合同测试起始\n")
        repeat(24) { index ->
            append("第${index + 1}段 这是用于 reader contract 的分页文本，包含 needle 关键字和足够多的内容用于翻页验证。")
            append("This line keeps the page long enough for runtime-bound navigation checks.\n")
        }
        append("第12章 合同测试章节\n")
        repeat(48) { index ->
            append("后续正文第${index + 1}段继续包含 needle 关键字，用于搜索、目录、选区和批注合同测试。\n")
        }
    }
}
