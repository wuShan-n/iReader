package com.ireader.engines.txt.internal.open

import com.ireader.core.files.source.DocumentSource
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal object TxtCharsetDetector {

    private const val PROBE_BYTES = 128 * 1024
    private const val MAX_REPLACEMENT_RATIO = 0.01
    private val fallbackCharsets = listOf("GB18030", "GBK", "ISO-8859-1")

    suspend fun detect(
        source: DocumentSource,
        overrideName: String?,
        ioDispatcher: CoroutineDispatcher
    ): Charset = withContext(ioDispatcher) {
        if (!overrideName.isNullOrBlank()) {
            return@withContext Charset.forName(overrideName)
        }

        val probe = readProbeBytes(source)
        val header = probe.copyOf(minOf(probe.size, 4))

        // UTF-8 BOM: EF BB BF
        if (
            header.size >= 3 &&
            header[0] == 0xEF.toByte() &&
            header[1] == 0xBB.toByte() &&
            header[2] == 0xBF.toByte()
        ) {
            return@withContext Charsets.UTF_8
        }

        // UTF-16 LE BOM: FF FE
        if (
            header.size >= 2 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xFE.toByte()
        ) {
            return@withContext Charsets.UTF_16LE
        }

        // UTF-16 BE BOM: FE FF
        if (
            header.size >= 2 &&
            header[0] == 0xFE.toByte() &&
            header[1] == 0xFF.toByte()
        ) {
            return@withContext Charsets.UTF_16BE
        }

        if (looksLikeUtf8(probe)) {
            return@withContext Charsets.UTF_8
        }

        selectBestFallback(probe) ?: Charsets.UTF_8
    }

    private suspend fun readProbeBytes(source: DocumentSource): ByteArray {
        return source.openInputStream().use { input ->
            val out = ByteArray(PROBE_BYTES)
            val read = input.read(out)
            if (read <= 0) ByteArray(0) else out.copyOf(read)
        }
    }

    private fun looksLikeUtf8(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded = runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()
            ?: return false
        val replacements = decoded.count { it == '\uFFFD' }
        if (replacements == 0) return true
        return replacements.toDouble() / decoded.length.coerceAtLeast(1).toDouble() <= MAX_REPLACEMENT_RATIO
    }

    private fun selectBestFallback(bytes: ByteArray): Charset? {
        if (bytes.isEmpty()) return Charsets.UTF_8
        var bestCharset: Charset? = null
        var bestScore = Int.MAX_VALUE
        for (name in fallbackCharsets) {
            val charset = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            val text = bytes.toString(charset)
            val replacement = text.count { it == '\uFFFD' }
            val controls = text.count { ch -> ch < ' ' && ch != '\n' && ch != '\r' && ch != '\t' }
            val score = replacement * 10 + controls
            if (score < bestScore) {
                bestScore = score
                bestCharset = charset
            }
        }
        return bestCharset
    }
}
