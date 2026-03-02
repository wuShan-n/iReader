package com.ireader.engines.txt

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.internal.open.TxtDocument
import com.ireader.engines.txt.internal.open.TxtOpener
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat

class TxtEngine(
    private val config: TxtEngineConfig
) : ReaderEngine {

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.TXT)

    private val opener = TxtOpener(
        cacheDir = config.cacheDir,
        ioDispatcher = config.ioDispatcher
    )

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return when (val result = opener.open(source, options)) {
            is ReaderResult.Err -> result
            is ReaderResult.Ok -> {
                ReaderResult.Ok(
                    TxtDocument(
                        id = result.value.documentId,
                        source = source,
                        files = result.value.files,
                        meta = result.value.meta,
                        openOptions = options,
                        persistPagination = config.persistPagination,
                        persistOutline = config.persistOutline,
                        maxPageCache = config.maxPageCache,
                        annotationProviderFactory = config.annotationProviderFactory,
                        ioDispatcher = config.ioDispatcher,
                        defaultDispatcher = config.defaultDispatcher
                    )
                )
            }
        }
    }
}
