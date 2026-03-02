package com.ireader.reader.runtime

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.core.files.source.DocumentSource

interface ReaderRuntime {

    suspend fun openDocument(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<ReaderDocument>

    suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions = OpenOptions(),
        initialLocator: Locator? = null,
        initialConfig: RenderConfig? = null
    ): ReaderResult<ReaderSessionHandle>
}


