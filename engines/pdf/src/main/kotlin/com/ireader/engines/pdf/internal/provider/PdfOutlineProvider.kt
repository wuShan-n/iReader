package com.ireader.engines.pdf.internal.provider

import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.util.toReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode

internal class PdfOutlineProvider(
    private val backend: PdfBackend
) : OutlineProvider {
    @Volatile
    private var cached: List<OutlineNode>? = null

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        cached?.let { return ReaderResult.Ok(it) }
        return runCatching { backend.outline() }
            .fold(
                onSuccess = {
                    cached = it
                    ReaderResult.Ok(it)
                },
                onFailure = { ReaderResult.Err(it.toReaderError()) }
            )
    }
}

