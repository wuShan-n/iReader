package com.ireader.engines.pdf.internal.open

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.open.OpenOptions
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal data class OpenedPdf(
    val pfd: ParcelFileDescriptor,
    val tempFile: File? = null
)

internal class PdfOpener(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun open(source: DocumentSource, options: OpenOptions): OpenedPdf = withContext(ioDispatcher) {
        source.openFileDescriptor(mode = "r")?.let { direct ->
            return@withContext OpenedPdf(pfd = direct, tempFile = null)
        }

        val cacheDir = File(context.cacheDir, "reader_pdf").apply { mkdirs() }
        val tempFile = File(cacheDir, "tmp_${UUID.randomUUID()}.pdf")
        source.openInputStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }

        val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        OpenedPdf(pfd = pfd, tempFile = tempFile)
    }
}
