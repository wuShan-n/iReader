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

        val hex = "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val value = b.toInt() and 0xFF
            out[i++] = hex[value ushr 4]
            out[i++] = hex[value and 0x0F]
        }
        return String(out)
    }
}
