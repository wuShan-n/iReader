package com.ireader.reader.api.engine

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.api.open.OpenOptions
import com.ireader.core.files.source.DocumentSource

interface ReaderEngine {
    val supportedFormats: Set<BookFormat>

    /**
     * 打开文档：解析、解压、建立索引等
     * 失败返回 ReaderResult.Err，避免异常散落 UI 层。
     */
    suspend fun open(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<ReaderDocument>
}


