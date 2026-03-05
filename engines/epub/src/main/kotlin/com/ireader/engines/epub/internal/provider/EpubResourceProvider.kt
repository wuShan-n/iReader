package com.ireader.engines.epub.internal.provider

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.BlockingResourceProvider
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.asInputStream
import org.readium.r2.shared.util.fromLegacyHref

internal class EpubResourceProvider(
    private val publication: Publication,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlockingResourceProvider {

    override suspend fun openResource(path: String): ReaderResult<InputStream> =
        withContext(ioDispatcher) { openResourceBlocking(path) }

    override suspend fun getMimeType(path: String): ReaderResult<String?> =
        withContext(ioDispatcher) { getMimeTypeBlocking(path) }

    override fun openResourceBlocking(path: String): ReaderResult<InputStream> {
        return try {
            val href = parseHref(path)
                ?: return ReaderResult.Err(ReaderError.CorruptOrInvalid("Invalid EPUB href: $path"))

            val resource = publication.get(href)
                ?: return ReaderResult.Err(ReaderError.NotFound("Resource not found: $path"))

            ReaderResult.Ok(resource.asInputStream())
        } catch (t: Throwable) {
            ReaderResult.Err(ReaderError.Io(cause = t))
        }
    }

    override fun getMimeTypeBlocking(path: String): ReaderResult<String?> {
        return try {
            val href = parseHref(path) ?: return ReaderResult.Ok(null)
            ReaderResult.Ok(publication.linkWithHref(href)?.mediaType?.toString())
        } catch (t: Throwable) {
            ReaderResult.Err(ReaderError.Internal(cause = t))
        }
    }

    @OptIn(DelicateReadiumApi::class)
    private fun parseHref(path: String): Url? {
        val normalized = path.trim()
        if (normalized.isEmpty()) return null
        return Url(normalized) ?: Url.fromLegacyHref(normalized)
    }
}
