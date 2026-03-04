@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "ReturnCount",
    "TooGenericExceptionCaught"
)

package com.ireader.engines.txt.internal.encoding

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.reader.api.error.ReaderResult
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
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
                    if (utf8.all { it.valid } && utf8.any { it.hasNonAscii }) {
                        ReaderResult.Ok(
                            EncodingResult(
                                charset = Charsets.UTF_8,
                                confidence = 0.9,
                                reason = "utf8_valid_multi_window"
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
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val trimmed = trimUtf8LeadingContinuationBytes(bytes)
        if (trimmed.isEmpty()) {
            return Utf8Check(valid = true, hasNonAscii = hasNonAscii)
        }

        return try {
            val input = ByteBuffer.wrap(trimmed)
            val output = CharBuffer.allocate(trimmed.size)
            while (true) {
                val result = decoder.decode(input, output, false)
                if (result.isError) {
                    return Utf8Check(valid = false, hasNonAscii = hasNonAscii)
                }
                if (result.isOverflow) {
                    continue
                }
                if (result.isUnderflow) {
                    break
                }
            }
            Utf8Check(valid = true, hasNonAscii = hasNonAscii)
        } catch (_: Exception) {
            Utf8Check(valid = false, hasNonAscii = hasNonAscii)
        }
    }

    private fun trimUtf8LeadingContinuationBytes(bytes: ByteArray): ByteArray {
        var start = 0
        val maxTrim = min(3, bytes.size)
        while (start < maxTrim && isUtf8Continuation(bytes[start])) {
            start += 1
        }
        return if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
    }

    private fun isUtf8Continuation(byte: Byte): Boolean {
        return (byte.toInt() and 0xC0) == 0x80
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
                var total = 0
                while (total < length) {
                    val read = channel.read(ByteBuffer.wrap(buffer, total, length - total))
                    if (read <= 0) {
                        break
                    }
                    total += read
                }
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
