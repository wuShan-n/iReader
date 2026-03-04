package com.ireader.engines.pdf.internal.open

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class PdfCacheFileStore(
    context: Context
) {
    private val rootDir = File(context.cacheDir, "pdf-engine").apply { mkdirs() }

    fun fileFor(uri: Uri): File {
        val name = sha256Hex(uri.toString()).take(24)
        return File(rootDir, "$name.pdf")
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { value ->
                append("%02x".format(value))
            }
        }
    }
}
