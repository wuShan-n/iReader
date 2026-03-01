package com.ireader.engines.epub.internal.provider

import android.webkit.MimeTypeMap
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.ResourceProvider
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class EpubResourceProvider(
    private val container: EpubContainer,
    private val ioDispatcher: CoroutineDispatcher
) : ResourceProvider {

    override suspend fun openResource(path: String): ReaderResult<InputStream> = withContext(ioDispatcher) {
        try {
            val cleanPath = path.trimStart('/')
            val target = File(container.rootDir, cleanPath).canonicalFile
            val root = container.rootDir.canonicalFile
            if (!target.path.startsWith(root.path) || !target.exists() || !target.isFile) {
                return@withContext ReaderResult.Err(
                    ReaderError.NotFound("Resource not found: $path")
                )
            }
            ReaderResult.Ok(target.inputStream())
        } catch (t: Throwable) {
            ReaderResult.Err(ReaderError.Io(cause = t))
        }
    }

    override suspend fun getMimeType(path: String): ReaderResult<String?> = withContext(ioDispatcher) {
        val cleanPath = path.trimStart('/')
        val fromOpf = container.opf.mediaTypeByPath[cleanPath]
        if (!fromOpf.isNullOrBlank()) {
            return@withContext ReaderResult.Ok(fromOpf)
        }

        val ext = cleanPath.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ReaderResult.Ok(mime)
    }
}
