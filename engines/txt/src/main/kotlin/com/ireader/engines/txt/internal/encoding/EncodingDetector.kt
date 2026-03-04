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
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
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
                } else if (looksLikeUtf8(source, sampleBytes = 256 * 1024)) {
                    ReaderResult.Ok(
                        EncodingResult(
                            charset = Charsets.UTF_8,
                            confidence = 0.85,
                            reason = "utf8_valid_sample"
                        )
                    )
                } else {
                    val best = EncodingScorer.pickBest(source)
                    ReaderResult.Ok(
                        EncodingResult(
                            charset = best,
                            confidence = 0.65,
                            reason = "scored_candidates"
                        )
                    )
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

    private suspend fun looksLikeUtf8(source: DocumentSource, sampleBytes: Int): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            source.openInputStream().use { input ->
                val bytes = ByteArray(sampleBytes)
                val read = input.read(bytes)
                if (read <= 0) {
                    return false
                }
                decoder.decode(ByteBuffer.wrap(bytes, 0, read))
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
