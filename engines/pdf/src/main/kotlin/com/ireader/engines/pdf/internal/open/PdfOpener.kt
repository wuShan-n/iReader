package com.ireader.engines.pdf.internal.open

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PdfOpener(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val cacheStore = PdfCacheFileStore(context)

    suspend fun open(source: DocumentSource): ReaderResult<OpenedPdfSource> = withContext(ioDispatcher) {
        runCatching {
            val directPfd = source.openFileDescriptor("r")
            if (directPfd != null) {
                return@runCatching OpenedPdfSource(
                    descriptor = directPfd,
                    tempFile = null
                )
            }

            val cacheFile = copyToCache(source)
            val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            OpenedPdfSource(
                descriptor = pfd,
                tempFile = cacheFile
            )
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { throwable ->
                ReaderResult.Err(
                    when (throwable) {
                        is SecurityException -> ReaderError.PermissionDenied(cause = throwable)
                        is IOException -> ReaderError.Io(cause = throwable)
                        else -> ReaderError.Internal(cause = throwable)
                    }
                )
            }
        )
    }

    private suspend fun copyToCache(source: DocumentSource): File {
        val target = cacheStore.fileFor(source.uri)
        val parent = target.parentFile ?: throw IOException("Invalid cache path: ${target.absolutePath}")
        val temp = File(parent, "${target.name}.tmp-${UUID.randomUUID()}")
        try {
            source.openInputStream().use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.flush()
                    output.fd.sync()
                }
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Failed to replace cached PDF: ${target.absolutePath}")
            }
            if (!temp.renameTo(target)) {
                throw IOException("Failed to finalize cached PDF: ${target.absolutePath}")
            }
            return target
        } catch (t: Throwable) {
            runCatching { temp.delete() }
            throw t
        }
    }
}

internal data class OpenedPdfSource(
    val descriptor: ParcelFileDescriptor,
    val tempFile: File?
)
