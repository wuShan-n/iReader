package com.ireader.engines.pdf

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.pdf.internal.open.PdfDocument
import com.ireader.engines.pdf.internal.open.PdfOpener
import com.ireader.engines.pdf.internal.pdfium.PdfiumDocument
import com.ireader.engines.pdf.internal.util.toReaderError
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfEngine(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReaderEngine {
    private val opener = PdfOpener(context.applicationContext, ioDispatcher)

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.PDF)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(ioDispatcher) {
        try {
            val opened = opener.open(source, options)
            val pdfium = runCatching {
                val dup = ParcelFileDescriptor.dup(opened.pfd.fileDescriptor)
                try {
                    PdfiumDocument.open(
                        pfd = dup,
                        dispatcher = ioDispatcher,
                        password = options.password
                    )
                } catch (t: Throwable) {
                    runCatching { dup.close() }
                    throw t
                }
            }.getOrNull()
            ReaderResult.Ok(
                PdfDocument(
                    source = source,
                    openOptions = options,
                    opened = opened,
                    ioDispatcher = ioDispatcher,
                    pdfium = pdfium
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }
}
