package com.ireader.engines.pdf

import android.content.Context
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.common.android.id.SourceDocumentIds
import com.ireader.engines.pdf.internal.backend.BackendFactory
import com.ireader.engines.pdf.internal.backend.PdfBackendProvider
import com.ireader.engines.pdf.internal.open.PdfDocument
import com.ireader.engines.pdf.internal.open.PdfOpener
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import kotlinx.coroutines.withContext

class PdfEngine internal constructor(
    private val config: PdfEngineConfig,
    private val backendProvider: PdfBackendProvider
) : ReaderEngine {

    constructor(
        context: Context,
        config: PdfEngineConfig = PdfEngineConfig()
    ) : this(
        config = config,
        backendProvider = BackendFactory(
            opener = PdfOpener(
                context = context.applicationContext,
                ioDispatcher = config.ioDispatcher
            ),
            config = config
        )
    )

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.PDF)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(config.ioDispatcher) {
        val opened = when (val result = backendProvider.open(source, options.password)) {
            is ReaderResult.Err -> return@withContext result
            is ReaderResult.Ok -> result.value
        }
        val created = runCatching {
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
            onFailure = {
                ReaderResult.Err(
                    it.toReaderError(invalidPasswordKeywords = setOf("password", "encrypted"))
                )
            }
        )
        if (created is ReaderResult.Err) {
            runCatching { opened.cleanup.close() }
        }
        created
    }

    private fun buildDocumentId(source: DocumentSource): DocumentId {
        return SourceDocumentIds.fromSourceSha256(
            prefix = "pdf",
            source = source,
            length = 40
        )
    }
}
