package com.ireader.engines.pdf.internal.backend

import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.pdfium.PdfiumBackend
import com.ireader.engines.pdf.internal.backend.platform.PlatformPdfBackend
import com.ireader.engines.pdf.internal.open.OpenedPdf
import com.ireader.engines.pdf.internal.open.PdfOpener
import com.ireader.engines.pdf.internal.util.closeQuietly
import com.ireader.reader.api.error.ReaderResult
import java.io.Closeable

internal class BackendFactory(
    private val opener: PdfOpener,
    private val config: PdfEngineConfig
) {
    suspend fun open(source: DocumentSource, password: String?): ReaderResult<OpenedPdf> {
        val opened = when (val result = opener.open(source)) {
            is ReaderResult.Err -> return result
            is ReaderResult.Ok -> result.value
        }

        val primaryPfd = opened.descriptor
        val tempFile = opened.tempFile

        try {
            val backend = when {
                config.forcePdfiumBackend -> openPdfium(primaryPfd, password)
                config.forcePlatformBackend -> openPlatform(primaryPfd)
                config.preferPlatformBackend -> {
                    val platform = runCatching { openPlatform(primaryPfd) }.getOrNull()
                    if (platform != null && platform.capabilities.supportsFullReaderFeatures()) {
                        platform
                    } else {
                        platform.closeQuietly()
                        openPdfium(primaryPfd, password)
                    }
                }
                else -> openPdfium(primaryPfd, password)
            }

            val cleanup = Closeable {
                backend.closeQuietly()
                tempFile?.let { file -> runCatching { file.delete() } }
            }
            return ReaderResult.Ok(OpenedPdf(backend = backend, cleanup = cleanup))
        } finally {
            primaryPfd.closeQuietly()
        }
    }

    private suspend fun openPdfium(
        sourceDescriptor: ParcelFileDescriptor,
        password: String?
    ): PdfBackend {
        val pfd = ParcelFileDescriptor.dup(sourceDescriptor.fileDescriptor)
        return PdfiumBackend.open(
            descriptor = pfd,
            password = password,
            ioDispatcher = config.ioDispatcher
        )
    }

    private fun openPlatform(sourceDescriptor: ParcelFileDescriptor): PdfBackend {
        val pfd = ParcelFileDescriptor.dup(sourceDescriptor.fileDescriptor)
        return PlatformPdfBackend(
            descriptor = pfd,
            ioDispatcher = config.ioDispatcher
        )
    }
}

