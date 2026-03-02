package com.ireader.engines.pdf.internal.session

import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.pdfium.PdfiumDocument
import com.ireader.engines.pdf.internal.provider.PdfHighlightStore
import com.ireader.engines.pdf.internal.provider.PdfOutlineProvider
import com.ireader.engines.pdf.internal.provider.PdfSearchProvider
import com.ireader.engines.pdf.internal.provider.PdfTextProvider
import com.ireader.engines.pdf.internal.render.PdfController
import com.ireader.engines.pdf.internal.util.toReaderError
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PdfSession private constructor(
    override val id: SessionId,
    override val controller: ReaderController,
    override val outline: OutlineProvider?,
    override val search: SearchProvider?,
    override val text: TextProvider?,
    override val annotations: AnnotationProvider?,
    override val resources: ResourceProvider?
) : ReaderSession {

    override fun close() {
        runCatching { controller.close() }
    }

    companion object {
        suspend fun create(
            documentId: DocumentId,
            backend: PdfBackend,
            startPageIndex: Int,
            initialConfig: RenderConfig.FixedPage,
            ioDispatcher: CoroutineDispatcher,
            pdfium: PdfiumDocument?
        ): ReaderResult<ReaderSession> = withContext(ioDispatcher) {
            try {
                val highlightStore = PdfHighlightStore()
                val controller = PdfController(
                    documentId = documentId,
                    backend = backend,
                    startPageIndex = startPageIndex,
                    initialConfig = initialConfig,
                    ioDispatcher = ioDispatcher,
                    linksResolver = { pageIndex, rotationDegrees ->
                        pdfium?.pageLinksNormalized(pageIndex, rotationDegrees).orEmpty()
                    },
                    highlightStore = highlightStore
                )

                val rotationProvider: () -> Int = {
                    val config = controller.state.value.config as? RenderConfig.FixedPage
                    config?.rotationDegrees ?: 0
                }

                val outlineProvider = pdfium?.let { PdfOutlineProvider(it) }
                val textProvider = pdfium?.takeIf { it.supportsText }?.let { PdfTextProvider(it) }
                val searchProvider = pdfium?.takeIf { it.supportsText }?.let {
                    PdfSearchProvider(
                        pdfium = it,
                        rotationDegreesProvider = rotationProvider,
                        highlightStore = highlightStore
                    )
                }

                ReaderResult.Ok(
                    PdfSession(
                        id = SessionId(UUID.randomUUID().toString()),
                        controller = controller,
                        outline = outlineProvider,
                        search = searchProvider,
                        text = textProvider,
                        annotations = null,
                        resources = null
                    )
                )
            } catch (t: Throwable) {
                ReaderResult.Err(t.toReaderError())
            }
        }
    }
}
