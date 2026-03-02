package com.ireader.engines.epub.internal.readium

import android.webkit.MimeTypeMap
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.ResourceProvider
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url

internal class ReadiumResourceProvider(
    private val publication: Publication
) : ResourceProvider {

    override suspend fun openResource(path: String): ReaderResult<InputStream> {
        val resource = resolveResource(path)
            ?: return ReaderResult.Err(ReaderError.NotFound("Resource not found: $path"))

        return try {
            when (val read = resource.read()) {
                is Try.Success -> ReaderResult.Ok(ByteArrayInputStream(read.value))
                is Try.Failure -> ReaderResult.Err(
                    ReaderError.Io(
                        message = read.value.message,
                        cause = ErrorException(read.value)
                    )
                )
            }
        } finally {
            runCatching { resource.close() }
        }
    }

    override suspend fun getMimeType(path: String): ReaderResult<String?> {
        val clean = normalizePath(path)
        if (clean.isBlank()) {
            return ReaderResult.Ok(null)
        }

        val fromPublication = resolveLink(clean)
            ?.mediaType
            ?.toString()
            ?.takeIf { it.isNotBlank() }
        if (fromPublication != null) {
            return ReaderResult.Ok(fromPublication)
        }

        val ext = clean.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "xhtml", "html", "htm" -> "application/xhtml+xml"
                "css" -> "text/css"
                "svg" -> "image/svg+xml"
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                else -> null
            }

        return ReaderResult.Ok(mime)
    }

    private fun resolveResource(path: String) =
        resolveLink(path)?.let(publication::get) ?: run {
            val clean = normalizePath(path)
            if (clean.isBlank()) {
                null
            } else {
                Url(clean)?.let(publication::get)
                    ?: Url("/$clean")?.let(publication::get)
            }
        }

    private fun resolveLink(path: String): Link? {
        val clean = normalizePath(path)
        if (clean.isBlank()) return null

        val byUrl = Url(clean)?.let(publication::linkWithHref)
            ?: Url("/$clean")?.let(publication::linkWithHref)
        if (byUrl != null) return byUrl

        return allPublicationLinks()
            .firstOrNull { normalizePath(it.href.toString()) == clean }
    }

    private fun allPublicationLinks(): List<Link> =
        publication.readingOrder + publication.resources + publication.links

    private fun normalizePath(path: String): String =
        path.trim()
            .removePrefix("href:")
            .substringBefore('?')
            .substringBefore('#')
            .trimStart('/')
}
