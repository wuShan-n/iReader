package com.ireader.engines.pdf.internal.open

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
        source.openInputStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
        return target
    }
}

internal data class OpenedPdfSource(
    val descriptor: ParcelFileDescriptor,
    val tempFile: File?
)

