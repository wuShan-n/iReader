package com.ireader.engines.txt

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.internal.open.DefaultTxtDocumentFactory
import com.ireader.engines.txt.internal.open.DefaultTxtOpenerFactory
import com.ireader.engines.txt.internal.open.TxtDocumentFactory
import com.ireader.engines.txt.internal.open.TxtOpenResult
import com.ireader.engines.txt.internal.open.TxtOpenerFactory
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat

class TxtEngine internal constructor(
    private val config: TxtEngineConfig,
    private val openerFactory: TxtOpenerFactory,
    private val documentFactory: TxtDocumentFactory
) : ReaderEngine {

    constructor(config: TxtEngineConfig) : this(
        config = config,
        openerFactory = DefaultTxtOpenerFactory,
        documentFactory = DefaultTxtDocumentFactory()
    )

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.TXT)

    private val opener by lazy(LazyThreadSafetyMode.NONE) {
        openerFactory.create(
            cacheDir = config.cacheDir,
            ioDispatcher = config.ioDispatcher
        )
    }

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return when (val result = opener.open(source, options)) {
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
        return documentFactory.create(
            id = openResult.documentId,
            source = source,
            files = openResult.files,
            meta = openResult.meta,
            openOptions = options,
            config = config
        )
    }
}
