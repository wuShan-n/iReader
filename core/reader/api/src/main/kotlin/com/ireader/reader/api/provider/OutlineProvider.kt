package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.OutlineNode

interface OutlineProvider {
    suspend fun getOutline(): ReaderResult<List<OutlineNode>>
}


