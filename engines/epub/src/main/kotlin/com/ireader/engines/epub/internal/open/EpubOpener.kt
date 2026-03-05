package com.ireader.engines.epub.internal.open

import android.content.Context
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.common.android.id.SourceDocumentIds
import com.ireader.engines.epub.internal.readium.ReadiumEpubToolkit
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.asset.Asset

internal class EpubOpener(
    context: Context,
    private val annotationStore: AnnotationStore? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val toolkit: ReadiumEpubToolkit = ReadiumEpubToolkit(context.applicationContext)
) {

    @OptIn(ExperimentalReadiumApi::class)
    suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<EpubDocument> = withContext(ioDispatcher) {
        var asset: Asset? = null
        var publication: Publication? = null

        try {
            val url = source.uri.toAbsoluteUrl()
                ?: return@withContext ReaderResult.Err(
                    ReaderError.CorruptOrInvalid("Cannot convert source uri to AbsoluteUrl: ${source.uri}")
                )

            val mediaType = source.mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaType(it) }
                ?: MediaType.EPUB

            asset = toolkit.assetRetriever
                .retrieve(url, mediaType)
                .getOrElse { retrieveError ->
                    return@withContext ReaderResult.Err(
                        ReaderError.Io("Failed to retrieve EPUB asset: ${retrieveError.message}")
                    )
                }

            publication = toolkit.publicationOpener
                .open(
                    asset = asset,
                    credentials = options.password,
                    allowUserInteraction = true
                )
                .getOrElse { openError ->
                    runCatching { asset?.close() }
                    asset = null
                    return@withContext ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Failed to open EPUB publication: ${openError.message}")
                    )
                }

            if (publication.isRestricted) {
                runCatching { publication.close() }
                runCatching { asset.close() }
                publication = null
                asset = null
                return@withContext ReaderResult.Err(
                    ReaderError.DrmRestricted("EPUB publication is restricted")
                )
            }

            val docId: DocumentId = SourceDocumentIds.fromSourceSha256(
                prefix = "epub",
                source = source,
                length = 40
            )

            val fixedLayout = publication.isFixedLayoutPublication()

            val capabilities = DocumentCapabilities(
                reflowable = !fixedLayout,
                fixedLayout = fixedLayout,
                outline = publication.tableOfContents.isNotEmpty(),
                search = publication.isSearchable,
                textExtraction = true,
                annotations = annotationStore != null,
                selection = true,
                links = true
            )

            ReaderResult.Ok(
                EpubDocument(
                    id = docId,
                    format = BookFormat.EPUB,
                    capabilities = capabilities,
                    openOptions = options,
                    publication = publication,
                    asset = asset,
                    annotationStore = annotationStore
                )
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t

            runCatching { publication?.close() }
            runCatching { asset?.close() }

            ReaderResult.Err(t.toReaderError(preserveInternalMessage = false))
        }
    }
}
