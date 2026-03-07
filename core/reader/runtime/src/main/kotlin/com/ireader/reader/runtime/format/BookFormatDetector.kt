package com.ireader.reader.runtime.format

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.model.BookFormat

interface BookFormatDetector {
    suspend fun detect(
        source: DocumentSource,
        hint: BookFormat? = null
    ): ReaderResult<BookFormat>
}


