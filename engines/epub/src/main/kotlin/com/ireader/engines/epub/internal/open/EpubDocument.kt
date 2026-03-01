package com.ireader.engines.epub.internal.open

import com.ireader.engines.epub.internal.content.EpubStore
import com.ireader.engines.epub.internal.session.EpubSession
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import kotlinx.coroutines.CoroutineDispatcher

internal class EpubDocument(
    private val container: EpubContainer,
    override val openOptions: OpenOptions,
    private val ioDispatcher: CoroutineDispatcher
) : ReaderDocument {

    override val id: DocumentId = container.id

    override val format: BookFormat = BookFormat.EPUB

    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = true,
        fixedLayout = false,
        outline = true,
        search = true,
        textExtraction = true,
        annotations = true,
        links = true
    )

    override suspend fun metadata(): ReaderResult<DocumentMetadata> {
        val extra = buildMap {
            put("uriAuthority", container.authority)
            put("spineCount", container.spineCount.toString())
            putAll(container.opf.metadata.extra)
        }
        return ReaderResult.Ok(container.opf.metadata.copy(extra = extra))
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> {
        val config = (initialConfig as? RenderConfig.ReflowText) ?: RenderConfig.ReflowText()
        return EpubSession.create(
            container = container,
            initialLocator = initialLocator,
            initialConfig = config,
            ioDispatcher = ioDispatcher
        )
    }

    override fun close() {
        EpubStore.unregister(id.value)
    }
}
