package com.ireader.reader.runtime.format

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.model.BookFormat
import com.ireader.reader.runtime.error.toReaderError
import java.io.BufferedInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Suppress("MagicNumber", "NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught")
class DefaultBookFormatDetector(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BookFormatDetector {

    override suspend fun detect(
        source: DocumentSource,
        hint: BookFormat?
    ): ReaderResult<BookFormat> = withContext(ioDispatcher) {
        try {
            // 1) hint
            if (hint != null) return@withContext ReaderResult.Ok(hint)

            // 2) mime
            detectFromMime(source.mimeType)?.let { return@withContext ReaderResult.Ok(it) }

            // 3) extension
            detectFromName(source.displayName)?.let { return@withContext ReaderResult.Ok(it) }

            // 4) magic sniff
            sniffFromContent(source)
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    private fun detectFromMime(mimeType: String?): BookFormat? {
        val mime = mimeType?.lowercase(Locale.US)?.trim() ?: return null
        return when {
            mime == "application/pdf" -> BookFormat.PDF
            mime == "application/epub+zip" -> BookFormat.EPUB
            mime == "text/plain" -> BookFormat.TXT
            // 有些文件管理器会给 text/*，可按需要放宽
            mime.startsWith("text/") -> BookFormat.TXT
            else -> null
        }
    }

    private fun detectFromName(displayName: String?): BookFormat? {
        val name = displayName?.lowercase(Locale.US)?.trim() ?: return null
        return when {
            name.endsWith(".pdf") -> BookFormat.PDF
            name.endsWith(".epub") -> BookFormat.EPUB
            name.endsWith(".txt") -> BookFormat.TXT
            else -> null
        }
    }

    private suspend fun sniffFromContent(source: DocumentSource): ReaderResult<BookFormat> {
        val header = readHeader(source, bytes = 8)

        if (looksLikePdf(header)) return ReaderResult.Ok(BookFormat.PDF)

        if (looksLikeZip(header)) {
            val epub = looksLikeEpubZip(source)
            return if (epub) {
                ReaderResult.Ok(BookFormat.EPUB)
            } else {
                ReaderResult.Err(ReaderError.UnsupportedFormat(detected = "zip"))
            }
        }

        // 兜底策略：本地阅读器很多“未知类型”其实就是 txt（尤其是 mime 缺失时）
        return ReaderResult.Ok(BookFormat.TXT)
    }

    private suspend fun readHeader(source: DocumentSource, bytes: Int): ByteArray {
        return source.openInputStream().use { input ->
            val buf = ByteArray(bytes)
            val read = input.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }
    }

    private fun looksLikePdf(header: ByteArray): Boolean {
        // "%PDF"
        return header.size >= 4 &&
            header[0] == 0x25.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x44.toByte() &&
            header[3] == 0x46.toByte()
    }

    private fun looksLikeZip(header: ByteArray): Boolean {
        // "PK.."
        return header.size >= 2 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte()
    }

    private suspend fun looksLikeEpubZip(source: DocumentSource): Boolean {
        // 重新开流：zip 探测需要从头读
        source.openInputStream().use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                var hasContainerXml = false

                while (true) {
                    coroutineContext.ensureActive()

                    val entry = zis.nextEntry ?: break
                    val name = entry.name

                    if (name == "mimetype") {
                        // epub 标准：mimetype 文件内容为 "application/epub+zip"
                        val content = readSmallAscii(zis, limitBytes = 64).trim()
                        if (content == "application/epub+zip") return true
                    }

                    if (name.equals("META-INF/container.xml", ignoreCase = true)) {
                        hasContainerXml = true
                        // 有 container.xml 基本可以视为 epub
                        return true
                    }
                }

                return hasContainerXml
            }
        }
    }

    private fun readSmallAscii(zis: ZipInputStream, limitBytes: Int): String {
        val buf = ByteArray(limitBytes)
        val read = zis.read(buf)
        return if (read <= 0) "" else String(buf, 0, read, Charsets.US_ASCII)
    }
}


