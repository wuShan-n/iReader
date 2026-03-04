package com.ireader.engines.pdf.internal.render

import android.graphics.Bitmap
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.backend.PdfBackendCapabilities
import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfControllerTest {

    @Test
    fun `render should fail before layout constraints are set`() = runTest {
        val controller = createController()

        val result = controller.render(RenderPolicy.Default)

        assertTrue(result is ReaderResult.Err)
    }

    @Test
    fun `next and prev keep navigation bounds`() = runTest {
        val controller = createController(pageCount = 3)
        controller.setLayoutConstraints(defaultConstraints())

        controller.render().requireOk()
        controller.next().requireOk()
        controller.next().requireOk()
        controller.next().requireOk()
        assertTrue(!controller.state.value.nav.canGoNext)

        controller.prev().requireOk()
        controller.prev().requireOk()
        controller.prev().requireOk()
        assertTrue(!controller.state.value.nav.canGoPrev)
    }

    @Test
    fun `render should emit page changed and rendered events`() = runTest {
        val controller = createController(pageCount = 4)
        controller.setLayoutConstraints(defaultConstraints())

        val events = mutableListOf<ReaderEvent>()
        val job = launch {
            controller.events.take(2).toList(events)
        }
        runCurrent()

        val page = controller.render().requireOk()
        job.join()

        assertEquals(2, events.size)
        assertTrue(events[0] is ReaderEvent.PageChanged)
        assertTrue(events[1] is ReaderEvent.Rendered)

        val tiles = page.content as RenderContent.Tiles
        assertEquals(512, tiles.baseTileSizePx)
    }

    @Test
    fun `goTo should reject unsupported locator`() = runTest {
        val controller = createController(pageCount = 2)
        controller.setLayoutConstraints(defaultConstraints())

        val result = controller.goTo(
            locator = Locator(scheme = LocatorSchemes.TXT_OFFSET, value = "1"),
            policy = RenderPolicy.Default
        )

        assertTrue(result is ReaderResult.Err)
    }

    private fun createController(pageCount: Int = 5): PdfController {
        return PdfController(
            backend = FakeBackend(pageCount = pageCount),
            pageCount = pageCount,
            initialPageIndex = 0,
            initialConfig = RenderConfig.FixedPage(),
            annotationProvider = null,
            engineConfig = PdfEngineConfig()
        )
    }

    private fun defaultConstraints(): LayoutConstraints {
        return LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 3f,
            fontScale = 1f
        )
    }

    private class FakeBackend(
        private val pageCount: Int
    ) : PdfBackend {
        override val capabilities: PdfBackendCapabilities = PdfBackendCapabilities(
            outline = true,
            links = true,
            textExtraction = true,
            search = true
        )

        override suspend fun pageCount(): Int = pageCount

        override suspend fun metadata(): DocumentMetadata = DocumentMetadata()

        override suspend fun pageSize(pageIndex: Int): PdfPageSize = PdfPageSize(1000, 1500)

        override suspend fun renderRegion(
            pageIndex: Int,
            bitmap: Bitmap,
            regionLeftPx: Int,
            regionTopPx: Int,
            regionWidthPx: Int,
            regionHeightPx: Int,
            quality: RenderPolicy.Quality
        ) = Unit

        override suspend fun pageLinks(pageIndex: Int): List<DocumentLink> = emptyList()

        override suspend fun outline(): List<OutlineNode> = emptyList()

        override suspend fun pageText(pageIndex: Int): String = ""

        override fun close() = Unit
    }

    private fun ReaderResult<RenderPage>.requireOk(): RenderPage {
        return when (this) {
            is ReaderResult.Ok -> value
            is ReaderResult.Err -> error("Expected Ok but got Err: ${error.code}")
        }
    }
}
