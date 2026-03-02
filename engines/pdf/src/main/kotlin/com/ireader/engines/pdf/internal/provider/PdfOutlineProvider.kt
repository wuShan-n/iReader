package com.ireader.engines.pdf.internal.provider

import com.ireader.engines.pdf.internal.pdfium.PdfiumDocument
import com.ireader.engines.pdf.internal.util.toReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode

internal class PdfOutlineProvider(
    private val pdfium: PdfiumDocument
) : OutlineProvider {
    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        return try {
            ReaderResult.Ok(pdfium.outline())
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }
}
