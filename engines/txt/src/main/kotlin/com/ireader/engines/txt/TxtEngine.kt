package com.ireader.engines.txt

import com.ireader.engines.txt.internal.open.TxtDocument
import com.ireader.engines.txt.internal.open.TxtOpenResult
import com.ireader.engines.txt.internal.open.TxtOpener
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat

class TxtEngine(
    private val config: TxtEngineConfig
) : ReaderEngine {

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.TXT)

    private val opener by lazy(LazyThreadSafetyMode.NONE) {
        TxtOpener(
            cacheDir = config.cacheDir,
            ioDispatcher = config.ioDispatcher
        )
    }

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return when (val result = opener.openMinimal(source, options)) {
            is ReaderResult.Err -> result
            is ReaderResult.Ok -> {
                ReaderResult.Ok(
                    createDocument(
                        openResult = result.value,
                        source = source,
                        options = options
                    )
                )
            }
        }
    }

    private fun createDocument(
        openResult: TxtOpenResult,
        source: DocumentSource,
        options: OpenOptions
    ): ReaderDocument {
        return TxtDocument(
            id = openResult.documentId,
            source = source,
            files = openResult.files,
            meta = openResult.meta,
            openOptions = options,
            persistPagination = config.persistPagination,
            persistOutline = config.persistOutline,
            maxPageCache = config.maxPageCache,
            annotationProviderFactory = config.annotationProviderFactory,
            ioDispatcher = config.ioDispatcher,
            paginationDispatcher = config.paginationDispatcher,
            defaultDispatcher = config.defaultDispatcher
        )
    }
}
