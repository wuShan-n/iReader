package com.ireader.engines.txt.internal.storage

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.internal.open.TxtTextNormalizer
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
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

        val text = source.openInputStream().use { rawInput ->
            val checkedInput = checkedStream(rawInput)
            decodeAndNormalize(checkedInput)
        }
        cached = text
        text
    }

    private fun checkedStream(input: InputStream): InputStream {
        return object : FilterInputStream(input) {
            private var totalBytes: Long = 0

            override fun read(): Int {
                val value = super.read()
                if (value >= 0) {
                    onBytesRead(1L)
                }
                return value
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val read = super.read(b, off, len)
                if (read > 0) {
                    onBytesRead(read.toLong())
                }
                return read
            }

            private fun onBytesRead(read: Long) {
                totalBytes += read
                if (totalBytes > maxBytes) {
                    throw IllegalStateException("TXT too large for in-memory store: $totalBytes bytes")
                }
            }
        }
    }

    private fun decodeAndNormalize(input: InputStream): String {
        val state = TxtTextNormalizer.StreamState()
        val out = StringBuilder(256 * 1024)
        val buffer = CharArray(16 * 1024)

        InputStreamReader(BufferedInputStream(input, 64 * 1024), charset).use { reader ->
            while (true) {
                val read = reader.read(buffer)
                if (read <= 0) break
                TxtTextNormalizer.appendNormalized(
                    input = java.nio.CharBuffer.wrap(buffer, 0, read),
                    state = state
                ) { normalized ->
                    out.append(normalized)
                }
            }
        }

        return out.toString()
    }
}
