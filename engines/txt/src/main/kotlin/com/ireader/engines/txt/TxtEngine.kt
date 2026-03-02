package com.ireader.engines.txt

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.engines.txt.internal.open.TxtCharsetDetector
import com.ireader.engines.txt.internal.open.TxtDocument
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TxtEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val config: TxtEngineConfig = TxtEngineConfig()
) : ReaderEngine {
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

internal fun Throwable.toReaderError(): ReaderError {
    return when (this) {
        is ReaderError -> this
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> ReaderError.Internal(message = message, cause = this)
    }
}
