package com.ireader.engines.pdf.internal.open

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.provider.EmptyPdfSelectionProvider
import com.ireader.engines.pdf.internal.provider.InMemoryPdfAnnotationProvider
import com.ireader.engines.pdf.internal.provider.PdfOutlineProvider
import com.ireader.engines.pdf.internal.provider.PdfSearchProvider
import com.ireader.engines.pdf.internal.provider.PdfTextProvider
import com.ireader.engines.pdf.internal.render.PdfController
import com.ireader.engines.pdf.internal.session.PdfSession
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.withContext

internal class PdfDocument(
    override val id: DocumentId,
    private val source: DocumentSource,
    private val openedPdf: OpenedPdf,
    private val pageCount: Int,
    private val engineConfig: PdfEngineConfig,
    override val openOptions: OpenOptions
) : ReaderDocument {

    override val format: BookFormat = BookFormat.PDF

    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = false,
        fixedLayout = true,
        outline = openedPdf.backend.capabilities.outline,
        search = openedPdf.backend.capabilities.search,
        textExtraction = openedPdf.backend.capabilities.textExtraction,
        annotations = true,
        links = openedPdf.backend.capabilities.links
    )

    private val closed = AtomicBoolean(false)

    override suspend fun metadata(): ReaderResult<DocumentMetadata> = withContext(engineConfig.ioDispatcher) {
        runCatching {
            val backendMetadata = openedPdf.backend.metadata()
            val defaultTitle = source.displayName?.substringBeforeLast('.')
            val extra = backendMetadata.extra + mapOf(
                "degradedBackend" to openedPdf.degradedBackend.toString()
            )
            backendMetadata.copy(
                title = backendMetadata.title ?: defaultTitle,
                extra = extra
            )
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(it.toReaderError()) }
        )
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> = withContext(engineConfig.ioDispatcher) {
        if (closed.get()) {
            return@withContext ReaderResult.Err(ReaderError.Internal("PDF document already closed"))
        }

        val fixedConfig = (initialConfig as? RenderConfig.FixedPage ?: RenderConfig.FixedPage()).sanitized()
        val initialPage = initialLocator?.toPdfPageIndexOrNull(pageCount) ?: 0

        val annotationProvider = engineConfig.annotationProviderFactory?.invoke(id)
            ?: InMemoryPdfAnnotationProvider(documentId = id)

        val textProvider = if (capabilities.textExtraction) {
            PdfTextProvider(
                backend = openedPdf.backend,
                pageCount = pageCount
            )
        } else {
            null
        }

        val controller = PdfController(
            backend = openedPdf.backend,
            pageCount = pageCount,
            initialPageIndex = initialPage,
            initialConfig = fixedConfig,
            annotationProvider = annotationProvider,
            engineConfig = engineConfig
        )

        ReaderResult.Ok(
            PdfSession(
                id = SessionId(UUID.randomUUID().toString()),
                controller = controller,
                outline = if (capabilities.outline) PdfOutlineProvider(openedPdf.backend) else null,
                search = if (capabilities.search && textProvider != null) {
                    PdfSearchProvider(pageCount = pageCount, textProvider = textProvider)
                } else {
                    null
                },
                text = textProvider,
                annotations = annotationProvider,
                selection = EmptyPdfSelectionProvider()
            )
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { openedPdf.cleanup.close() }
    }
}
