package com.ireader.engines.epub.internal.open

import com.ireader.engines.epub.internal.session.ReadiumEpubSession
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
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.util.asset.Asset

internal class EpubDocument(
    override val id: DocumentId,
    private val publication: Publication,
    private val asset: Asset,
    override val openOptions: OpenOptions,
    private val ioDispatcher: CoroutineDispatcher
) : ReaderDocument {

    override val format: BookFormat = BookFormat.EPUB

    @OptIn(ExperimentalReadiumApi::class)
    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = publication.metadata.presentation.layout != EpubLayout.FIXED,
        fixedLayout = publication.metadata.presentation.layout == EpubLayout.FIXED,
        outline = publication.tableOfContents.isNotEmpty(),
        search = publication.isSearchable,
        textExtraction = publication.content() != null,
        annotations = true,
        links = true
    )

    override suspend fun metadata(): ReaderResult<DocumentMetadata> {
        val meta = publication.metadata
        val firstAuthor = meta.authors.firstOrNull()?.name
        val language = meta.languages.firstOrNull()

        val extra = buildMap {
            put("layout", meta.presentation.layout?.value.orEmpty())
            put("description", meta.description.orEmpty())
            meta.numberOfPages?.let { put("numberOfPages", it.toString()) }
        }.filterValues { it.isNotBlank() }

        return ReaderResult.Ok(
            DocumentMetadata(
                title = meta.title,
                author = firstAuthor,
                language = language,
                identifier = meta.identifier,
                extra = extra
            )
        )
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> {
        val config = (initialConfig as? RenderConfig.ReflowText) ?: RenderConfig.ReflowText()
        return ReadiumEpubSession.create(
            publication = publication,
            initialLocator = initialLocator,
            initialConfig = config,
            ioDispatcher = ioDispatcher
        )
    }

    override fun close() {
        runCatching { publication.close() }
        runCatching { asset.close() }
    }
}
