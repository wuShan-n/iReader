package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode

internal class EpubOutlineProvider(
    private val container: EpubContainer
) : OutlineProvider {
    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        return ReaderResult.Ok(container.outline)
    }
}
