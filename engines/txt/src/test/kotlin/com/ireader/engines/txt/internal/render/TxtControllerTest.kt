package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.open.Utf16LeFileWriter
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.engines.txt.testing.TxtRuntimeFixture
import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.TextLayoutInput
import com.ireader.reader.api.render.TextLayoutMeasureResult
import com.ireader.reader.api.render.TextLayouter
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationType
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TxtControllerTest {

    @Test
    fun `render should fail before layout constraints are set`() = runBlocking {
        val fixture = createFixture(text = sampleText(paragraphs = 10))
        try {
            val result = fixture.controller.render(RenderPolicy.Default)
            assertTrue(result is ReaderResult.Err)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `render should return error when layouter throws unexpectedly`() = runBlocking {
        val fixture = createFixture(
            text = sampleText(paragraphs = 20),
            textLayouterFactory = ThrowingTextLayouterFactory
        )
        try {
            fixture.controller.setLayoutConstraints(defaultConstraints())
            val result = fixture.controller.render(RenderPolicy.Default)
            assertTrue(result is ReaderResult.Err)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `render cache should reset after config change`() = runBlocking {
        val fixture = createFixture(text = sampleText(paragraphs = 60))
        try {
            fixture.controller.setLayoutConstraints(defaultConstraints())

            val first = fixture.controller.render(RenderPolicy.Default).requireOk()
            val second = fixture.controller.render(RenderPolicy.Default).requireOk()
            assertFalse(first.metrics?.cacheHit ?: true)
            assertEquals(first.id.value, second.id.value)

            val current = fixture.controller.state.value.config as RenderConfig.ReflowText
            fixture.controller.setConfig(current.copy(fontSizeSp = current.fontSizeSp + 2f))

            val third = fixture.controller.render(RenderPolicy.Default).requireOk()
            assertFalse(third.metrics?.cacheHit ?: true)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `render with allowCache false should not populate page cache`() = runBlocking {
        val fixture = createFixture(text = sampleText(paragraphs = 80))
        try {
            fixture.controller.setLayoutConstraints(defaultConstraints())

            val noCache = fixture.controller.render(
                RenderPolicy(allowCache = false, prefetchNeighbors = 0)
            ).requireOk()
            val firstCachedAttempt = fixture.controller.render(
                RenderPolicy(allowCache = true, prefetchNeighbors = 0)
            ).requireOk()

            assertFalse(noCache.metrics?.cacheHit ?: true)
            assertFalse(firstCachedAttempt.metrics?.cacheHit ?: true)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `render should succeed without persisted break map`() = runBlocking {
        val fixture = createFixture(
            text = sampleText(paragraphs = 40),
            usePersistedBreakIndex = false
        )
        try {
            fixture.controller.setLayoutConstraints(defaultConstraints())

            val page = fixture.controller.render(RenderPolicy.Default).requireOk()

            assertTrue((page.content as RenderContent.Text).text.isNotEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `next then prev should return to original page`() = runBlocking {
        val fixture = createFixture(text = sampleText(paragraphs = 140))
        try {
            fixture.controller.setLayoutConstraints(defaultConstraints())
            val first = fixture.controller.render(RenderPolicy.Default).requireOk()
            val next = fixture.controller.next(RenderPolicy.Default).requireOk()
            val prev = fixture.controller.prev(RenderPolicy.Default).requireOk()

            assertTrue(first.id.value != next.id.value)
            assertEquals(first.id.value, prev.id.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `next should move chapter title to page start`() = runBlocking {
        val fixture = createFixture(text = chapterBoundaryText())
        try {
            fixture.controller.setLayoutConstraints(defaultConstraints().copy(viewportHeightPx = 620))
            val first = fixture.controller.render(RenderPolicy.Default).requireOk()
            val second = fixture.controller.next(RenderPolicy.Default).requireOk()

            val firstText = (first.content as RenderContent.Text).text.toString()
            val secondText = (second.content as RenderContent.Text).text.toString()

            assertTrue(first.id.value != second.id.value)
            assertFalse(firstText.contains("第12章 新章节"))
            assertTrue(secondText.trimStart().startsWith("第12章 新章节"))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `goTo should reject unsupported locator`() = runBlocking {
        val fixture = createFixture(text = sampleText(paragraphs = 20))
        try {
            val result = fixture.controller.goTo(
                locator = Locator(scheme = "unsupported.scheme", value = "123"),
                policy = RenderPolicy.Default
            )
            assertTrue(result is ReaderResult.Err)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `render should skip decoration queries for empty annotations and refresh after revision`() = runBlocking {
        val provider = CountingAnnotationProvider()
        val fixture = createFixture(
            text = sampleText(paragraphs = 60),
            annotationProvider = provider
        )
        try {
            fixture.controller.setLayoutConstraints(defaultConstraints())
            fixture.controller.render(RenderPolicy.Default).requireOk()
            assertEquals(0, provider.decorationQueryCount.get())

            provider.emitOne(locator = fixture.runtime.locatorFor(0L), suffix = "1")
            delay(80L)
            fixture.controller.render(RenderPolicy.Default).requireOk()
            assertEquals(1, provider.decorationQueryCount.get())

            fixture.controller.render(RenderPolicy.Default).requireOk()
            assertEquals(1, provider.decorationQueryCount.get())

            provider.emitOne(locator = fixture.runtime.locatorFor(0L), suffix = "2")
            delay(80L)
            fixture.controller.render(RenderPolicy.Default).requireOk()
            assertEquals(2, provider.decorationQueryCount.get())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `applyBreakPatch should rerender current page with projected text and persist patch`() = runBlocking {
        val fixture = createFixture(text = "甲\n乙\n丙\n")
        try {
            fixture.controller.setLayoutConstraints(defaultConstraints())

            val patched = fixture.controller.applyBreakPatch(
                locator = fixture.runtime.locatorFor(0L),
                direction = TextBreakPatchDirection.NEXT,
                state = TextBreakPatchState.SOFT_SPACE
            ).requireOk()

            val patchedText = (patched.content as RenderContent.Text).text.toString()
            assertTrue(patchedText.startsWith("甲 乙"))
            assertTrue(fixture.runtime.files.breakPatch.exists())
        } finally {
            fixture.close()
        }
    }

    private fun sampleText(paragraphs: Int): String {
        return buildString {
            repeat(paragraphs) { index ->
                append("第${index + 1}段 这是用于分页测试的文本，包含一些重复内容以产生多页效果。")
                append("This sentence is repeated to ensure we span multiple pages in tests. ")
                append('\n')
            }
        }
    }

    private fun chapterBoundaryText(): String {
        return buildString {
            append("上一章正文推进剧情用于分页测试A。\n")
            append("上一章正文推进剧情用于分页测试B。\n")
            append("第12章 新章节\n")
            repeat(24) { index ->
                append("新章节正文第${index + 1}段继续描述故事发展并用于翻页验证。\n")
            }
        }
    }

    private fun defaultConstraints(): LayoutConstraints {
        return LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 3f,
            fontScale = 1f
        )
    }

    private suspend fun createFixture(
        text: String,
        annotationProvider: AnnotationProvider? = null,
        textLayouterFactory: TextLayouterFactory = TestTextLayouterFactory,
        usePersistedBreakIndex: Boolean = true
    ): ControllerFixture {
        val dir = Files.createTempDirectory("txt_controller_test").toFile()
        val runtime = buildTxtRuntimeFixture(
            text = text,
            sampleHash = "sample",
            ioDispatcher = Dispatchers.IO,
            rootDir = dir
        )
        val projectionEngine = if (usePersistedBreakIndex) {
            runtime.projectionEngine
        } else {
            runtime.files.breakMap.delete()
            TextProjectionEngine(
                store = runtime.store,
                files = runtime.files,
                meta = runtime.meta,
                breakIndex = null
            )
        }
        val blockStore = if (usePersistedBreakIndex) {
            runtime.blockStore
        } else {
            BlockStore(
                store = runtime.store,
                blockIndex = runtime.blockIndex,
                revision = runtime.meta.contentRevision,
                projectionEngine = projectionEngine
            )
        }
        val controller = TxtController(
            documentKey = "doc-test",
            store = runtime.store,
            meta = runtime.meta,
            blockIndex = runtime.blockIndex,
            projectionEngine = projectionEngine,
            blockStore = blockStore,
            initialLocator = null,
            initialOffset = 0L,
            initialConfig = RenderConfig.ReflowText(),
            maxPageCache = 8,
            persistPagination = false,
            files = runtime.files,
            annotationProvider = annotationProvider,
            ioDispatcher = Dispatchers.IO,
            paginationDispatcher = Dispatchers.Default.limitedParallelism(1),
            defaultDispatcher = Dispatchers.Default
        )
        controller.setTextLayouterFactory(textLayouterFactory)
        return ControllerFixture(
            controller = controller,
            runtime = runtime,
            transientProjectionEngine = if (usePersistedBreakIndex) null else projectionEngine
        )
    }

    private fun ReaderResult<RenderPage>.requireOk(): RenderPage {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }

    private class ControllerFixture(
        val controller: TxtController,
        val runtime: TxtRuntimeFixture,
        private val transientProjectionEngine: TextProjectionEngine? = null
    ) {
        fun close() {
            controller.close()
            transientProjectionEngine?.close()
            runtime.close()
        }
    }

    private class CountingAnnotationProvider : AnnotationProvider {
        private val state = MutableStateFlow<List<Annotation>>(emptyList())
        val decorationQueryCount = AtomicInteger(0)

        override fun observeAll(): Flow<List<Annotation>> = state

        override suspend fun listAll(): ReaderResult<List<Annotation>> = ReaderResult.Ok(state.value)

        override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> = ReaderResult.Ok(state.value)

        override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> {
            val created = Annotation(
                id = AnnotationId("created-${System.nanoTime()}"),
                type = draft.type,
                anchor = draft.anchor,
                content = draft.content,
                style = draft.style,
                createdAtEpochMs = System.currentTimeMillis(),
                extra = draft.extra
            )
            state.value = state.value + created
            return ReaderResult.Ok(created)
        }

        override suspend fun update(annotation: Annotation): ReaderResult<Unit> = ReaderResult.Ok(Unit)

        override suspend fun delete(id: AnnotationId): ReaderResult<Unit> = ReaderResult.Ok(Unit)

        override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
            decorationQueryCount.incrementAndGet()
            return ReaderResult.Ok(emptyList())
        }

        fun emitOne(locator: Locator, suffix: String) {
            state.value = listOf(
                Annotation(
                    id = AnnotationId("anno-$suffix"),
                    type = AnnotationType.HIGHLIGHT,
                    anchor = AnnotationAnchor.ReflowRange(LocatorRange(start = locator, end = locator)),
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    private companion object {
        private val TestTextLayouterFactory = object : TextLayouterFactory {
            override val environmentKey: String = "txt-controller-test"

            override fun create(cacheSize: Int): TextLayouter {
                return object : TextLayouter {
                    override fun measure(
                        text: CharSequence,
                        input: TextLayoutInput
                    ): TextLayoutMeasureResult {
                        if (text.isEmpty() || input.widthPx <= 0 || input.heightPx <= 0) {
                            return TextLayoutMeasureResult(
                                endChar = 0,
                                lineCount = 0,
                                lastVisibleLine = -1
                            )
                        }

                        val charsPerLine = (input.widthPx / 28).coerceAtLeast(8)
                        val lineHeightPx = 54
                        val maxLines = (input.heightPx / lineHeightPx).coerceAtLeast(1)

                        var line = 0
                        var column = 0
                        var endChar = 0
                        for (index in text.indices) {
                            if (line >= maxLines) {
                                break
                            }
                            val ch = text[index]
                            if (ch == '\n') {
                                endChar = index + 1
                                line++
                                column = 0
                                continue
                            }
                            if (column >= charsPerLine) {
                                line++
                                column = 0
                                if (line >= maxLines) {
                                    break
                                }
                            }
                            column++
                            endChar = index + 1
                        }

                        val visibleLineCount = when {
                            endChar <= 0 -> 0
                            line >= maxLines -> maxLines
                            column > 0 -> line + 1
                            else -> line.coerceAtLeast(1)
                        }
                        return TextLayoutMeasureResult(
                            endChar = endChar,
                            lineCount = visibleLineCount,
                            lastVisibleLine = visibleLineCount - 1
                        )
                    }
                }
            }
        }

        private val ThrowingTextLayouterFactory = object : TextLayouterFactory {
            override val environmentKey: String = "txt-controller-throwing"

            override fun create(cacheSize: Int): TextLayouter {
                return object : TextLayouter {
                    override fun measure(
                        text: CharSequence,
                        input: TextLayoutInput
                    ): TextLayoutMeasureResult {
                        error("synthetic layouter failure")
                    }
                }
            }
        }
    }
}
