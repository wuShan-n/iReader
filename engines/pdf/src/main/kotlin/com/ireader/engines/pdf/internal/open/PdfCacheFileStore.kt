package com.ireader.engines.pdf.internal.open

import android.content.Context
import android.net.Uri
import java.io.File

internal class PdfCacheFileStore(
    context: Context
) {
    private val rootDir = File(context.cacheDir, "pdf-engine").apply { mkdirs() }

    fun fileFor(uri: Uri): File {
        val name = uri.toString().hashCode().toUInt().toString(16)
        return File(rootDir, "$name.pdf")
    }
}

