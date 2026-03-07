package com.ireader.reader.api.engine

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import java.io.Closeable

interface ReaderDocument : Closeable {
    val id: DocumentId
    val format: com.ireader.reader.model.BookFormat
    val capabilities: DocumentCapabilities
    val openOptions: OpenOptions

    suspend fun metadata(): ReaderResult<DocumentMetadata>

    /**
     * 会话：包含当前阅读位置、渲染设置、缓存等
     */
    suspend fun createSession(
        initialLocator: Locator? = null,
        initialConfig: RenderConfig = RenderConfig.Default
    ): ReaderResult<ReaderSession>
}


