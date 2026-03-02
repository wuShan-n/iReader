package com.ireader.engines.epub.internal.open

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.epub.internal.session.EpubSession
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import java.util.UUID
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.Asset

internal class EpubDocument(
    override val id: DocumentId,
    override val format: BookFormat,
    override val capabilities: DocumentCapabilities,
    override val openOptions: OpenOptions,
    internal val publication: Publication,
    private val asset: Asset,
    private val annotationStore: AnnotationStore?
) : ReaderDocument {

    override suspend fun metadata(): ReaderResult<DocumentMetadata> {
        return try {
            val md = publication.metadata
            ReaderResult.Ok(
                DocumentMetadata(
                    title = md.title,
                    author = md.authors.firstOrNull()?.name,
                    language = md.languages.firstOrNull(),
                    identifier = md.identifier
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
        }
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> {
        return try {
            val sessionId = SessionId(UUID.randomUUID().toString())
            ReaderResult.Ok(
                EpubSession.create(
                    id = sessionId,
                    documentId = id,
                    publication = publication,
                    initialLocator = initialLocator,
                    initialConfig = initialConfig,
                    annotationStore = annotationStore
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
        }
    }

    override fun close() {
        runCatching { publication.close() }
        runCatching { asset.close() }
    }
}
