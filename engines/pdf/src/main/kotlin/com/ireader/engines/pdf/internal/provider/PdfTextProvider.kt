package com.ireader.engines.pdf.internal.provider

import com.ireader.engines.pdf.internal.pdfium.PdfiumDocument
import com.ireader.engines.pdf.internal.util.toReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

internal class PdfTextProvider(
    private val pdfium: PdfiumDocument
) : TextProvider {
    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        return try {
            val locator = range.start
            if (locator.scheme != LocatorSchemes.PDF_PAGE) return ReaderResult.Ok("")
            val pageIndex = locator.value.toIntOrNull() ?: 0
            ReaderResult.Ok(pdfium.pageText(pageIndex))
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        return try {
            if (locator.scheme != LocatorSchemes.PDF_PAGE) return ReaderResult.Ok("")
            val pageIndex = locator.value.toIntOrNull() ?: 0
            ReaderResult.Ok(pdfium.pageText(pageIndex).take(maxChars.coerceAtLeast(0)))
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }
}
