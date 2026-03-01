package com.ireader.engines.epub

import android.content.Context
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.engines.epub.internal.open.EpubOpener
import com.ireader.reader.model.BookFormat
import com.ireader.reader.source.DocumentSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class EpubEngine(
    context: Context,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReaderEngine {
    private val opener = EpubOpener(
        context = context.applicationContext,
        ioDispatcher = ioDispatcher
    )

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.EPUB)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return opener.open(source, options)
    }
}
