package com.ireader.reader.runtime

import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator

data class BookProbeResult(
    val format: BookFormat,
    val documentId: String?,
    val metadata: DocumentMetadata?,
    val coverBytes: ByteArray?,
    val capabilities: DocumentCapabilities?
)

interface ReaderRuntime {

    suspend fun openDocument(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<ReaderDocument>

    suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions = OpenOptions(),
        initialLocator: Locator? = null,
        initialConfig: RenderConfig? = null,
        resolveInitialConfig: (suspend (DocumentCapabilities) -> RenderConfig)? = null
    ): ReaderResult<ReaderSessionHandle>

    suspend fun probe(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<BookProbeResult>
}
