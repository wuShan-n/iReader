@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "ReturnCount",
    "TooGenericExceptionCaught"
)

package com.ireader.engines.txt.internal.encoding

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.math.min
import kotlinx.coroutines.CancellationException

internal data class EncodingResult(
    val charset: Charset,
    val confidence: Double,
    val reason: String
)

internal class EncodingDetector {

    suspend fun detect(
        source: DocumentSource,
        explicitEncoding: String?
    ): ReaderResult<EncodingResult> {
        return try {
            if (!explicitEncoding.isNullOrBlank()) {
                val explicit = Charset.forName(explicitEncoding.trim())
                ReaderResult.Ok(
                    EncodingResult(
                        charset = explicit,
                        confidence = 1.0,
                        reason = "explicit"
                    )
                )
            } else {
                val bomResult = sniffBom(source)
                if (bomResult != null) {
                    ReaderResult.Ok(bomResult)
                } else {
                    val samples = readSamples(source, sampleBytes = 128 * 1024)
                    val utf8 = samples.map(::checkUtf8)
                    if (utf8.all { it.valid }) {
                        val hasAnyNonAscii = utf8.any { it.hasNonAscii }
                        val confidence = if (hasAnyNonAscii) 0.9 else 0.7
                        ReaderResult.Ok(
                            EncodingResult(
                                charset = Charsets.UTF_8,
                                confidence = confidence,
                                reason = if (hasAnyNonAscii) {
                                    "utf8_valid_multi_window"
                                } else {
                                    "utf8_ascii_multi_window"
                                }
                            )
                        )
                    } else {
                        val best = EncodingScorer.pickBest(samples)
                        ReaderResult.Ok(
                            EncodingResult(
                                charset = best,
                                confidence = 0.65,
                                reason = "scored_multi_window"
                            )
                        )
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            ReaderResult.Err(e.toReaderError())
        }
    }

    private suspend fun sniffBom(source: DocumentSource): EncodingResult? {
        source.openInputStream().use { raw ->
            val input = BufferedInputStream(raw)
            input.mark(4)
            val b0 = input.read()
            val b1 = input.read()
            val b2 = input.read()
            val b3 = input.read()
            input.reset()

            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                return EncodingResult(Charsets.UTF_8, 1.0, "bom_utf8")
            }
            if (b0 == 0xFF && b1 == 0xFE && b2 == 0x00 && b3 == 0x00) {
                return EncodingResult(Charset.forName("UTF-32LE"), 1.0, "bom_utf32le")
            }
            if (b0 == 0x00 && b1 == 0x00 && b2 == 0xFE && b3 == 0xFF) {
                return EncodingResult(Charset.forName("UTF-32BE"), 1.0, "bom_utf32be")
            }
            if (b0 == 0xFF && b1 == 0xFE) {
                return EncodingResult(Charset.forName("UTF-16LE"), 1.0, "bom_utf16le")
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return EncodingResult(Charset.forName("UTF-16BE"), 1.0, "bom_utf16be")
            }
            return null
        }
    }

    private data class Utf8Check(val valid: Boolean, val hasNonAscii: Boolean)

    private fun checkUtf8(bytes: ByteArray): Utf8Check {
        val hasNonAscii = bytes.any { b -> (b.toInt() and 0xFF) >= 0x80 }
        val startIndex = trimUtf8LeadingContinuationBytes(bytes)
        if (startIndex >= bytes.size) {
            return Utf8Check(valid = true, hasNonAscii = hasNonAscii)
        }
        return Utf8Check(
            valid = validateUtf8(bytes = bytes, startIndex = startIndex),
            hasNonAscii = hasNonAscii
        )
    }

    private fun trimUtf8LeadingContinuationBytes(bytes: ByteArray): Int {
        var start = 0
        val maxTrim = min(3, bytes.size)
        while (start < maxTrim && isUtf8Continuation(bytes[start])) {
            start += 1
        }
        return start
    }

    private fun isUtf8Continuation(byte: Byte): Boolean {
        return (byte.toInt() and 0xC0) == 0x80
    }

    private fun validateUtf8(bytes: ByteArray, startIndex: Int): Boolean {
        var i = startIndex
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            when {
                b0 < 0x80 -> {
                    i++
                }

                b0 in 0xC2..0xDF -> {
                    if (i + 1 >= bytes.size) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (!isUtf8ContinuationByte(b1)) return false
                    i += 2
                }

                b0 == 0xE0 -> {
                    if (i + 2 >= bytes.size) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    if (b1 !in 0xA0..0xBF || !isUtf8ContinuationByte(b2)) return false
                    i += 3
                }

                b0 in 0xE1..0xEC || b0 in 0xEE..0xEF -> {
                    if (i + 2 >= bytes.size) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    if (!isUtf8ContinuationByte(b1) || !isUtf8ContinuationByte(b2)) return false
                    i += 3
                }

                b0 == 0xED -> {
                    if (i + 2 >= bytes.size) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    if (b1 !in 0x80..0x9F || !isUtf8ContinuationByte(b2)) return false
                    i += 3
                }

                b0 == 0xF0 -> {
                    if (i + 3 >= bytes.size) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF
                    if (b1 !in 0x90..0xBF || !isUtf8ContinuationByte(b2) || !isUtf8ContinuationByte(b3)) {
                        return false
                    }
                    i += 4
                }

                b0 in 0xF1..0xF3 -> {
                    if (i + 3 >= bytes.size) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF
                    if (!isUtf8ContinuationByte(b1) || !isUtf8ContinuationByte(b2) || !isUtf8ContinuationByte(b3)) {
                        return false
                    }
                    i += 4
                }

                b0 == 0xF4 -> {
                    if (i + 3 >= bytes.size) return true
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF
                    if (b1 !in 0x80..0x8F || !isUtf8ContinuationByte(b2) || !isUtf8ContinuationByte(b3)) {
                        return false
                    }
                    i += 4
                }

                else -> return false
            }
        }
        return true
    }

    private fun isUtf8ContinuationByte(value: Int): Boolean {
        return (value and 0xC0) == 0x80
    }

    private suspend fun readSamples(source: DocumentSource, sampleBytes: Int): List<ByteArray> {
        val resolvedSize = resolveSizeBytes(source)
        if (resolvedSize == null) {
            val head = readHead(source, maxBytes = sampleBytes * 3)
            if (head.size <= sampleBytes) {
                return listOf(head)
            }
            return listOf(
                head.copyOfRange(0, sampleBytes),
                head.copyOfRange(head.size - sampleBytes, head.size)
            )
        }

        if (resolvedSize <= 0L) {
            return listOf(readSampleAt(source, offset = 0L, length = sampleBytes))
        }

        val size = resolvedSize
        if (size <= sampleBytes.toLong()) {
            return listOf(readSampleAt(source, offset = 0L, length = sampleBytes))
        }

        val maxOffset = (size - sampleBytes.toLong()).coerceAtLeast(0L)
        val offsets = LinkedHashSet<Long>()
        offsets += 0L
        offsets += maxOffset
        if (size > (2L * sampleBytes.toLong())) {
            val midRaw = (size / 2L) - (sampleBytes.toLong() / 2L)
            offsets += midRaw.coerceIn(0L, maxOffset)
        }

        return offsets
            .toList()
            .sorted()
            .map { offset -> readSampleAt(source, offset = offset, length = sampleBytes) }
    }

    private suspend fun resolveSizeBytes(source: DocumentSource): Long? {
        source.sizeBytes?.takeIf { it > 0 }?.let { return it }

        val pfd = source.openFileDescriptor("r") ?: return null
        return try {
            val size = pfd.statSize
            size.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { pfd.close() }
        }
    }

    private suspend fun readHead(source: DocumentSource, maxBytes: Int): ByteArray {
        return source.openInputStream().use { input ->
            val buffer = ByteArray(maxBytes)
            var total = 0
            while (total < maxBytes) {
                val read = input.read(buffer, total, maxBytes - total)
                if (read <= 0) {
                    break
                }
                total += read
            }
            if (total == buffer.size) buffer else buffer.copyOf(total)
        }
    }

    private suspend fun readSampleAt(source: DocumentSource, offset: Long, length: Int): ByteArray {
        val fdBytes = runCatching { readSampleAtViaFd(source, offset = offset, length = length) }.getOrNull()
        if (fdBytes != null) {
            return fdBytes
        }

        return source.openInputStream().use { input ->
            skipFully(input, offset)
            val buffer = ByteArray(length)
            var total = 0
            while (total < length) {
                val read = input.read(buffer, total, length - total)
                if (read <= 0) {
                    break
                }
                total += read
            }
            if (total == buffer.size) buffer else buffer.copyOf(total)
        }
    }

    private suspend fun readSampleAtViaFd(source: DocumentSource, offset: Long, length: Int): ByteArray? {
        val pfd = source.openFileDescriptor("r") ?: return null
        return try {
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val channel = fis.channel
                channel.position(offset.coerceAtLeast(0L))

                val buffer = ByteArray(length)
                val byteBuffer = ByteBuffer.wrap(buffer)
                while (byteBuffer.hasRemaining()) {
                    val read = channel.read(byteBuffer)
                    if (read <= 0) {
                        break
                    }
                }
                val total = byteBuffer.position()
                if (total == buffer.size) buffer else buffer.copyOf(total)
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip.coerceAtLeast(0L)
        val scratch = ByteArray(8 * 1024)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }

            val toRead = min(scratch.size.toLong(), remaining).toInt()
            val read = input.read(scratch, 0, toRead)
            if (read <= 0) {
                break
            }
            remaining -= read.toLong()
        }
    }
}
