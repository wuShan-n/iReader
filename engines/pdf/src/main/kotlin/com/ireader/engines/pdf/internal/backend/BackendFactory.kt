package com.ireader.engines.pdf.internal.backend

import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.pdfium.PdfiumBackend
import com.ireader.engines.pdf.internal.backend.platform.PlatformPdfBackend
import com.ireader.engines.pdf.internal.open.OpenedPdf
import com.ireader.engines.pdf.internal.open.PdfOpener
import com.ireader.engines.common.io.closeQuietly
import com.ireader.reader.api.error.ReaderResult
import java.io.Closeable

internal class BackendFactory(
    private val opener: PdfOpener,
    private val config: PdfEngineConfig
) : PdfBackendProvider {
    override suspend fun open(source: DocumentSource, password: String?): ReaderResult<OpenedPdf> {
        val opened = when (val result = opener.open(source)) {
            is ReaderResult.Err -> return result
            is ReaderResult.Ok -> result.value
        }

        val primaryPfd = opened.descriptor
        val tempFile = opened.tempFile

        return try {
            val selected = when {
                config.forcePdfiumBackend -> SelectedBackend(
                    backend = openPdfium(primaryPfd, password),
                    degraded = false
                )

                config.forcePlatformBackend -> SelectedBackend(
                    backend = openPlatform(primaryPfd),
                    degraded = true
                )

                else -> openWithPdfiumFallback(primaryPfd, password)
            }

            val cleanup = Closeable {
                selected.backend.closeQuietly()
                tempFile?.let { file -> runCatching { file.delete() } }
            }
            ReaderResult.Ok(
                OpenedPdf(
                    backend = selected.backend,
                    cleanup = cleanup,
                    degradedBackend = selected.degraded
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(
                t.toReaderError(invalidPasswordKeywords = setOf("password", "encrypted"))
            )
        } finally {
            primaryPfd.closeQuietly()
        }
    }

    private suspend fun openWithPdfiumFallback(
        sourceDescriptor: ParcelFileDescriptor,
        password: String?
    ): SelectedBackend {
        val pdfiumAttempt = runCatching { openPdfium(sourceDescriptor, password) }
        if (pdfiumAttempt.isSuccess) {
            return SelectedBackend(
                backend = pdfiumAttempt.getOrThrow(),
                degraded = false
            )
        }

        val platformAttempt = runCatching { openPlatform(sourceDescriptor) }
        if (platformAttempt.isSuccess) {
            return SelectedBackend(
                backend = platformAttempt.getOrThrow(),
                degraded = true
            )
        }

        val pdfiumError = pdfiumAttempt.exceptionOrNull()
        val platformError = platformAttempt.exceptionOrNull()
        if (pdfiumError != null && platformError != null) {
            pdfiumError.addSuppressed(platformError)
        }
        throw pdfiumError ?: platformError ?: IllegalStateException("Unknown PDF backend open error")
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

    private data class SelectedBackend(
        val backend: PdfBackend,
        val degraded: Boolean
    )
}
