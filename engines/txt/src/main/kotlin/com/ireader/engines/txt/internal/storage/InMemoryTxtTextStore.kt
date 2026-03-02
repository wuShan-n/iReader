package com.ireader.engines.txt.internal.storage

import com.ireader.core.files.source.DocumentSource
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class InMemoryTxtTextStore(
    private val source: DocumentSource,
    override val charset: Charset,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxBytes: Long = 8L * 1024L * 1024L
) : TxtTextStore {

    @Volatile
    private var cached: String? = null

    override suspend fun totalChars(): Int = ensureLoaded().length

    override suspend fun readRange(startChar: Int, endCharExclusive: Int): String {
        val text = ensureLoaded()
        val start = startChar.coerceIn(0, text.length)
        val end = endCharExclusive.coerceIn(start, text.length)
        return text.substring(start, end)
    }

    override suspend fun readChars(startChar: Int, maxChars: Int): String {
        val text = ensureLoaded()
        val start = startChar.coerceIn(0, text.length)
        val safeMax = maxChars.coerceAtLeast(0)
        val end = (start + safeMax).coerceAtMost(text.length)
        return text.substring(start, end)
    }

    override suspend fun readAround(charOffset: Int, maxChars: Int): String {
        val text = ensureLoaded()
        if (text.isEmpty()) return ""
        val safe = maxChars.coerceAtLeast(1)
        val half = (safe / 2).coerceAtLeast(1)
        val center = charOffset.coerceIn(0, text.length)
        val start = (center - half).coerceAtLeast(0)
        val end = (start + safe).coerceAtMost(text.length)
        return text.substring(start, end)
    }

    override fun close() {
        cached = null
    }

    private suspend fun ensureLoaded(): String = withContext(ioDispatcher) {
        cached?.let { return@withContext it }

        source.sizeBytes?.let { size ->
            if (size > maxBytes) {
                throw IllegalStateException("TXT too large for in-memory store: $size bytes")
            }
        }

        val bytes = source.openInputStream().use { input -> input.readBytes() }
        if (bytes.size.toLong() > maxBytes) {
            throw IllegalStateException("TXT too large for in-memory store: ${bytes.size} bytes")
        }

        val text = decodeRemovingBom(bytes, charset)
        cached = text
        text
    }

    private fun decodeRemovingBom(bytes: ByteArray, charset: Charset): String {
        if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return bytes.copyOfRange(3, bytes.size).toString(charset)
        }

        if (
            bytes.size >= 2 &&
            (
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                    (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
                )
        ) {
            return bytes.copyOfRange(2, bytes.size).toString(charset)
        }

        return bytes.toString(charset)
    }
}
