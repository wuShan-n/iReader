package com.ireader.engines.epub

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.source.DocumentSource

class EpubEngine : ReaderEngine {
    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.EPUB)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return ReaderResult.Err(ReaderError.Internal("EPUB engine is not implemented yet"))
    }
}
