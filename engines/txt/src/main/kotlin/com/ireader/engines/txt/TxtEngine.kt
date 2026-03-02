package com.ireader.engines.txt

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.engines.txt.internal.open.TxtCharsetDetector
import com.ireader.engines.txt.internal.open.TxtDocument
import com.ireader.engines.txt.internal.util.toReaderError
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TxtEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    config: TxtEngineConfig = TxtEngineConfig()
) : ReaderEngine {
    private val config: TxtEngineConfig = config.normalized()

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.TXT)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(ioDispatcher) {
        try {
            val charset = TxtCharsetDetector.detect(source, options.textEncoding, ioDispatcher)
            ReaderResult.Ok(
                TxtDocument(
                    source = source,
                    openOptions = options,
                    charset = charset,
                    ioDispatcher = ioDispatcher,
                    config = config
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }
}
