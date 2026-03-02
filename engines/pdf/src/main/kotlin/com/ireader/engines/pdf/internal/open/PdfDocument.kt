package com.ireader.engines.pdf.internal.open

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.pdf.internal.backend.PlatformPdfBackend
import com.ireader.engines.pdf.internal.pdfium.PdfiumDocument
import com.ireader.engines.pdf.internal.session.PdfSession
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.engines.pdf.internal.util.toReaderError
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PdfDocument(
    private val source: DocumentSource,
    override val openOptions: OpenOptions,
    private val opened: OpenedPdf,
    private val ioDispatcher: CoroutineDispatcher,
    private val pdfium: PdfiumDocument?
) : ReaderDocument {

    private val backend = PlatformPdfBackend(opened.pfd)

    override val id: DocumentId = DocumentId("pdf:${stableId()}")
    override val format: BookFormat = BookFormat.PDF

    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = false,
        fixedLayout = true,
        outline = pdfium != null,
        search = pdfium?.supportsText == true,
        textExtraction = pdfium?.supportsText == true,
        annotations = true,
        links = pdfium != null
    )

    override suspend fun metadata(): ReaderResult<DocumentMetadata> = withContext(ioDispatcher) {
        try {
            val title = source.displayName ?: source.uri.lastPathSegment ?: "Untitled"
            val extra = buildMap {
                source.mimeType?.let { put("mimeType", it) }
                source.sizeBytes?.let { put("sizeBytes", it.toString()) }
                put("pageCount", backend.pageCount.toString())
                put("uri", source.uri.toString())
                put("pdfium", (pdfium != null).toString())
                put("pdfiumText", (pdfium?.supportsText == true).toString())
            }
            ReaderResult.Ok(
                DocumentMetadata(
                    title = title,
                    identifier = id.value,
                    extra = extra
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> = withContext(ioDispatcher) {
        try {
            val config = (initialConfig as? RenderConfig.FixedPage) ?: RenderConfig.FixedPage()
            val maxIndex = (backend.pageCount - 1).coerceAtLeast(0)
            val pageIndex = initialLocator
                ?.toPdfPageIndexOrNull()
                ?.coerceIn(0, maxIndex)
                ?: 0

            PdfSession.create(
                documentId = id,
                backend = backend,
                startPageIndex = pageIndex,
                initialConfig = config,
                ioDispatcher = ioDispatcher,
                pdfium = pdfium
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    override fun close() {
        runCatching { pdfium?.close() }
        runCatching { backend.close() }
        runCatching { opened.pfd.close() }
        opened.tempFile?.let { file ->
            runCatching { file.delete() }
        }
    }

    private fun stableId(): String {
        val seed = buildString {
            append(source.uri.toString())
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(source.sizeBytes?.toString().orEmpty())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(32) {
            for (i in 0 until 16) {
                val b = digest[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }
}
