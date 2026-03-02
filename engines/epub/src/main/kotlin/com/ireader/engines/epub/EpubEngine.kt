package com.ireader.engines.epub

import android.content.Context
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.epub.internal.open.EpubOpener
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.BookFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpubEngine(
    context: Context,
    private val annotationStore: AnnotationStore? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReaderEngine {

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.EPUB)

    private val opener = EpubOpener(
        context = context,
        annotationStore = annotationStore
    )

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(ioDispatcher) {
        if (source.uri.toString().isBlank()) {
            return@withContext ReaderResult.Err(
                ReaderError.NotFound("EPUB source uri is empty")
            )
        }

        opener.open(source, options)
    }
}
