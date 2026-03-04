package com.ireader.engines.pdf.integration

import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.backend.PdfBackendCapabilities
import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.engines.pdf.internal.open.OpenedPdf
import com.ireader.engines.pdf.internal.open.PdfDocument
import com.ireader.feature.reader.domain.usecase.OpenReaderSession
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.ReaderSessionHandle
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfOpenReaderSessionIntegrationTest {

    @Test
    fun `open reader session should resolve fixed config and render pdf tiles`() = runTest {
        val fixedConfig = RenderConfig.FixedPage(zoom = 1.4f)
        val settings = FakeReaderSettingsStore(fixed = fixedConfig)
        val runtime = FakeRuntime(document = createPdfDocument())
        val useCase = OpenReaderSession(runtime = runtime, settings = settings)

        val result = useCase(
            source = FakeSource(),
            options = OpenOptions(),
            initialLocator = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "2")
        )

        assertTrue(result is ReaderResult.Ok)
        val handle = (result as ReaderResult.Ok).value
        assertEquals(fixedConfig, runtime.lastResolvedConfig)
        assertTrue(handle.document.capabilities.fixedLayout)

        val controller = handle.controller
        controller.setLayoutConstraints(
            LayoutConstraints(
                viewportWidthPx = 1080,
                viewportHeightPx = 1920,
                density = 3f,
                fontScale = 1f
            )
        )
        val page = controller.render(RenderPolicy.Default).requireOk()
        assertEquals(LocatorSchemes.PDF_PAGE, page.locator.scheme)
        assertEquals("2", page.locator.value)
        assertTrue(page.content is RenderContent.Tiles)
        handle.close()
    }

    private fun createPdfDocument(): PdfDocument {
        val backend = FakeBackend(pageCount = 5)
        return PdfDocument(
            id = DocumentId("pdf:integration"),
            source = FakeSource(),
            openedPdf = OpenedPdf(
                backend = backend,
                cleanup = Closeable { backend.close() },
                degradedBackend = false
            ),
            pageCount = 5,
            engineConfig = PdfEngineConfig(),
            openOptions = OpenOptions()
        )
    }

    private class FakeRuntime(
        private val document: PdfDocument
    ) : ReaderRuntime {
        var lastResolvedConfig: RenderConfig? = null

        override suspend fun openDocument(
            source: DocumentSource,
            options: OpenOptions
        ): ReaderResult<ReaderDocument> = ReaderResult.Ok(document)

        override suspend fun openSession(
            source: DocumentSource,
            options: OpenOptions,
            initialLocator: Locator?,
            initialConfig: RenderConfig?,
            resolveInitialConfig: (suspend (DocumentCapabilities) -> RenderConfig)?
        ): ReaderResult<ReaderSessionHandle> {
            val config = when {
                initialConfig != null -> initialConfig
                resolveInitialConfig != null -> resolveInitialConfig(document.capabilities)
                else -> RenderConfig.FixedPage()
            }
            lastResolvedConfig = config

            val session = when (val created = document.createSession(initialLocator, config)) {
                is ReaderResult.Err -> return ReaderResult.Err(created.error)
                is ReaderResult.Ok -> created.value
            }
            return ReaderResult.Ok(ReaderSessionHandle(document = document, session = session))
        }

        override suspend fun probe(
            source: DocumentSource,
            options: OpenOptions
        ): ReaderResult<BookProbeResult> {
            return ReaderResult.Err(ReaderError.Internal("Not used in this test"))
        }
    }

    private class FakeBackend(
        private val pageCount: Int
    ) : PdfBackend {
        private var closed = false

        override val capabilities: PdfBackendCapabilities = PdfBackendCapabilities(
            outline = true,
            links = true,
            textExtraction = true,
            search = true
        )

        override suspend fun pageCount(): Int = pageCount

        override suspend fun metadata(): DocumentMetadata {
            return DocumentMetadata(
                title = "PDF",
                extra = mapOf("backend" to "fake")
            )
        }

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

        override fun close() {
            closed = true
        }
    }

    private class FakeSource : DocumentSource {
        override val uri: Uri = Uri.parse("content://integration/book.pdf")
        override val displayName: String? = "book.pdf"
        override val mimeType: String? = "application/pdf"
        override val sizeBytes: Long? = 1L

        override suspend fun openInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = null
    }
}

private class FakeReaderSettingsStore(
    private val fixed: RenderConfig.FixedPage
) : ReaderSettingsStore {
    override val reflowConfig: Flow<RenderConfig.ReflowText> = flowOf(RenderConfig.ReflowText())
    override val fixedConfig: Flow<RenderConfig.FixedPage> = flowOf(fixed)
    override val displayPrefs: Flow<ReaderDisplayPrefs> = flowOf(ReaderDisplayPrefs())

    override suspend fun getReflowConfig(): RenderConfig.ReflowText = RenderConfig.ReflowText()

    override suspend fun getFixedConfig(): RenderConfig.FixedPage = fixed

    override suspend fun getDisplayPrefs(): ReaderDisplayPrefs = ReaderDisplayPrefs()

    override suspend fun setReflowConfig(config: RenderConfig.ReflowText) = Unit

    override suspend fun setFixedConfig(config: RenderConfig.FixedPage) = Unit

    override suspend fun setDisplayPrefs(prefs: ReaderDisplayPrefs) = Unit
}

private fun ReaderResult<com.ireader.reader.api.render.RenderPage>.requireOk():
    com.ireader.reader.api.render.RenderPage {
    return when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> error("Expected Ok but got Err: ${error.code}")
    }
}
