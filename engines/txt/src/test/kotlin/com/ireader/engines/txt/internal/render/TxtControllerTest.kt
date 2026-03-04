package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.open.Utf16LeFileWriter
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.Locator
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    private suspend fun createFixture(text: String): ControllerFixture {
        val dir = Files.createTempDirectory("txt_controller_test").toFile()
        val files = createBookFiles(dir)
        Utf16LeFileWriter(files.contentU16).use { writer ->
            text.forEach(writer::writeChar)
        }
        val store = Utf16TextStore(files.contentU16)
        val meta = TxtMeta(
            version = 1,
            sourceUri = "file://test.txt",
            displayName = "test.txt",
            sizeBytes = text.length.toLong(),
            sampleHash = "sample",
            originalCharset = "UTF-8",
            lengthChars = store.lengthChars,
            hardWrapLikely = false,
            createdAtEpochMs = 0L
        )
        SoftBreakIndexBuilder.buildIfNeeded(
            files = files,
            meta = meta,
            ioDispatcher = Dispatchers.IO,
            profile = SoftBreakTuningProfile.BALANCED
        )
        val controller = TxtController(
            documentKey = "doc-test",
            store = store,
            meta = meta,
            initialLocator = null,
            initialOffset = 0L,
            initialConfig = RenderConfig.ReflowText(),
            maxPageCache = 8,
            persistPagination = false,
            files = files,
            annotationProvider = null,
            ioDispatcher = Dispatchers.IO,
            defaultDispatcher = Dispatchers.Default
        )
        return ControllerFixture(
            controller = controller,
            store = store,
            rootDir = dir
        )
    }

    private fun createBookFiles(root: File): TxtBookFiles {
        val paginationDir = File(root, "pagination").apply { mkdirs() }
        return TxtBookFiles(
            bookDir = root,
            lockFile = File(root, "book.lock"),
            contentU16 = File(root, "content.u16"),
            metaJson = File(root, "meta.json"),
            outlineJson = File(root, "outline.json"),
            paginationDir = paginationDir,
            softBreakIdx = File(root, "softbreak.idx"),
            softBreakLock = File(root, "softbreak.lock"),
            bloomIdx = File(root, "bloom.idx"),
            bloomLock = File(root, "bloom.lock")
        )
    }

    private fun ReaderResult<RenderPage>.requireOk(): RenderPage {
        return (this as? ReaderResult.Ok)?.value
            ?: error("Expected ReaderResult.Ok but was $this")
    }

    private class ControllerFixture(
        val controller: TxtController,
        private val store: Utf16TextStore,
        private val rootDir: File
    ) {
        fun close() {
            controller.close()
            store.close()
            rootDir.deleteRecursively()
        }
    }
}
