package com.ireader.engines.pdf.internal.backend

import android.os.ParcelFileDescriptor
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.PdfBackendStrategy
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.pdfium.PdfiumBackend
import com.ireader.engines.pdf.internal.backend.platform.PlatformPdfBackend
import com.ireader.engines.pdf.internal.open.OpenedPdf
import com.ireader.engines.pdf.internal.open.PdfOpener
import com.ireader.engines.common.io.closeQuietly
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
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
            val selected = when (config.backendStrategy) {
                PdfBackendStrategy.PDFIUM_ONLY -> SelectedBackend(
                    backend = openPdfium(primaryPfd, password),
                    degraded = false
                )

                PdfBackendStrategy.PLATFORM_ONLY -> SelectedBackend(
                    backend = openPlatform(primaryPfd),
                    degraded = true
                )

                PdfBackendStrategy.AUTO -> openWithPdfiumFallback(primaryPfd, password)
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
            tempFile?.let { file -> runCatching { file.delete() } }
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
        val dup = ParcelFileDescriptor.dup(sourceDescriptor.fileDescriptor)
        return try {
            PdfiumBackend.open(
                descriptor = dup,
                password = password,
                ioDispatcher = config.ioDispatcher
            )
        } catch (t: Throwable) {
            dup.closeQuietly()
            throw t
        }
    }

    private fun openPlatform(sourceDescriptor: ParcelFileDescriptor): PdfBackend {
        val dup = ParcelFileDescriptor.dup(sourceDescriptor.fileDescriptor)
        return try {
            PlatformPdfBackend(
                descriptor = dup,
                ioDispatcher = config.ioDispatcher
            )
        } catch (t: Throwable) {
            dup.closeQuietly()
            throw t
        }
    }

    private data class SelectedBackend(
        val backend: PdfBackend,
        val degraded: Boolean
    )
}
