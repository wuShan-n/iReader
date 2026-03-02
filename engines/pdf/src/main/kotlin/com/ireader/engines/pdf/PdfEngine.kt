package com.ireader.engines.pdf

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource

class PdfEngine : ReaderEngine {
    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.PDF)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return ReaderResult.Err(ReaderError.Internal("PDF engine is not implemented yet"))
    }
}
