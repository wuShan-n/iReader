package com.ireader.engines.epub.internal.open

import android.content.Context
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.DocumentId
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.allAreHtml
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal class EpubOpener(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val appContext = context.applicationContext

    private val readium = ReadiumRuntime(appContext)

    suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(ioDispatcher) {
        var asset: Asset? = null
        try {
            val stableId = stableId(source)
            val docId = DocumentId("epub:$stableId")
            val baseDir = File(appContext.cacheDir, "epub/$stableId").apply { mkdirs() }
            val localFile = File(baseDir, localFileName(source))

            if (!localFile.exists() || (source.sizeBytes != null && localFile.length() != source.sizeBytes)) {
                copyToLocal(source, localFile)
            }

            val hints = FormatHints(
                mediaType = source.mimeType?.let { MediaType(it) }
            )
            asset = readium.assetRetriever.retrieve(localFile, hints)
                .fold(
                    onSuccess = { it },
                    onFailure = { return@withContext ReaderResult.Err(it.toReaderError()) }
                )

            val publication = readium.publicationOpener.open(
                asset = asset,
                credentials = options.password,
                allowUserInteraction = false
            ).fold(
                onSuccess = { it },
                onFailure = {
                    runCatching { asset.close() }
                    return@withContext ReaderResult.Err(it.toReaderError())
                }
            )

            if (!publication.conformsTo(Publication.Profile.EPUB) && !publication.readingOrder.allAreHtml) {
                runCatching { publication.close() }
                runCatching { asset.close() }
                return@withContext ReaderResult.Err(
                    ReaderError.UnsupportedFormat(detected = "EPUB")
                )
            }

            ReaderResult.Ok(
                EpubDocument(
                    id = docId,
                    publication = publication,
                    asset = asset,
                    openOptions = options,
                    ioDispatcher = ioDispatcher
                )
            )
        } catch (t: Throwable) {
            runCatching { asset?.close() }
            ReaderResult.Err(t.toReaderError())
        }
    }

    private suspend fun copyToLocal(source: DocumentSource, outFile: File) {
        outFile.parentFile?.mkdirs()
        source.openInputStream().use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun localFileName(source: DocumentSource): String {
        val raw = source.displayName?.takeIf { it.isNotBlank() } ?: "book.epub"
        val safe = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (safe.endsWith(".epub", ignoreCase = true)) safe else "$safe.epub"
    }

    private fun stableId(source: DocumentSource): String {
        val seed = buildString {
            append(source.uri)
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(source.sizeBytes ?: -1)
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(32) {
            for (i in 0 until 16) {
                val b = bytes[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }

    private fun Throwable.toReaderError(): ReaderError = when (this) {
        is ReaderError -> this
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> ReaderError.Internal(message = message, cause = this)
    }

    private fun AssetRetriever.RetrieveError.toReaderError(): ReaderError = when (this) {
        is AssetRetriever.RetrieveError.FormatNotSupported -> ReaderError.UnsupportedFormat()
        is AssetRetriever.RetrieveError.Reading -> ReaderError.Io(
            message = cause.message,
            cause = cause.toThrowable()
        )
    }

    private fun PublicationOpener.OpenError.toReaderError(): ReaderError = when (this) {
        is PublicationOpener.OpenError.FormatNotSupported -> ReaderError.UnsupportedFormat()
        is PublicationOpener.OpenError.Reading -> ReaderError.CorruptOrInvalid(
            message = cause.message,
            cause = cause.toThrowable()
        )
    }

    private fun ReadError.toThrowable(): Throwable = ErrorException(this)
}

private class ReadiumRuntime(context: Context) {
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null
        )
    )
}
