package com.ireader.engines.pdf

import android.content.Context
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.pdf.internal.backend.BackendFactory
import com.ireader.engines.pdf.internal.open.PdfDocument
import com.ireader.engines.pdf.internal.open.PdfOpener
import com.ireader.engines.pdf.internal.util.sha1Hex
import com.ireader.engines.pdf.internal.util.toReaderError
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import kotlinx.coroutines.withContext

class PdfEngine(
    context: Context,
    private val config: PdfEngineConfig = PdfEngineConfig()
) : ReaderEngine {

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.PDF)

    private val opener = PdfOpener(
        context = context.applicationContext,
        ioDispatcher = config.ioDispatcher
    )
    private val backendFactory = BackendFactory(
        opener = opener,
        config = config
    )

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(config.ioDispatcher) {
        val opened = when (val result = backendFactory.open(source, options.password)) {
            is ReaderResult.Err -> return@withContext result
            is ReaderResult.Ok -> result.value
        }
        runCatching {
            val pageCount = opened.backend.pageCount()
            val documentId = buildDocumentId(source)

            ReaderResult.Ok(
                PdfDocument(
                    id = documentId,
                    source = source,
                    openedPdf = opened,
                    pageCount = pageCount,
                    engineConfig = config,
                    openOptions = options
                ) as ReaderDocument
            )
        }.fold(
            onSuccess = { it },
            onFailure = { ReaderResult.Err(it.toReaderError()) }
        )
    }

    private fun buildDocumentId(source: DocumentSource): DocumentId {
        val raw = buildString {
            append(source.uri.toString())
            append('|')
            append(source.displayName ?: "")
            append('|')
            append(source.sizeBytes ?: -1L)
            append('|')
            append(source.mimeType ?: "")
        }
        return DocumentId("pdf:${sha1Hex(raw)}")
    }
}
