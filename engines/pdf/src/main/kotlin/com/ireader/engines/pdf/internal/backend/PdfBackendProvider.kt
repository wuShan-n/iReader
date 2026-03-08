package com.ireader.engines.pdf.internal.backend

import com.ireader.engines.pdf.internal.open.OpenedPdf
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource

internal interface PdfBackendProvider {
    suspend fun open(source: DocumentSource, password: String?): ReaderResult<OpenedPdf>
}
