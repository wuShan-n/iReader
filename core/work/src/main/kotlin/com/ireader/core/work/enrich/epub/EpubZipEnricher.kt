package com.ireader.core.work.enrich.epub

import android.graphics.Bitmap
import android.util.Xml
import com.ireader.core.work.enrich.BitmapDecode
import com.ireader.core.work.enrich.BitmapIO
import com.ireader.reader.model.DocumentMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

object EpubZipEnricher {

    fun tryExtractCoverToPng(
        file: File,
        coverPathInZip: String,
        outFile: File,
        reqWidth: Int,
        reqHeight: Int
    ): Boolean {
        return runCatching {
            ZipFile(file).use { zip ->
                val normalizedPath = normalizeZipPath(coverPathInZip)
                val coverEntry = zip.getEntry(normalizedPath) ?: return false
                val bytes = zip.getInputStream(coverEntry).use { stream ->
                    stream.readAllBytesCapped(limitBytes = 12 * 1024 * 1024)
                } ?: return false

                val decoded = BitmapDecode.decodeSampled(bytes, reqWidth, reqHeight) ?: return false
                val outputBitmap = if (decoded.width == reqWidth && decoded.height == reqHeight) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, reqWidth, reqHeight, true)
                }
                BitmapIO.savePng(outFile, outputBitmap)
                true
            }
        }.getOrDefault(false)
    }

    private fun normalizeZipPath(path: String): String {
        val parts = path
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }

        val stack = ArrayDeque<String>(parts.size)
        for (part in parts) {
            when (part) {
                "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun InputStream.readAllBytesCapped(limitBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) {
                break
            }
            total += read
            if (total > limitBytes) {
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

}
