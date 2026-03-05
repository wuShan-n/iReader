@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "NestedBlockDepth",
    "ReturnCount",
    "TooGenericExceptionCaught"
)

package com.ireader.engines.txt.internal.open

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.common.hash.Hashing
import com.ireader.engines.common.id.DocumentIds
import com.ireader.engines.txt.internal.encoding.EncodingDetector
import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.DocumentId
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.sqrt
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class TxtOpener(
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val encodingDetector = EncodingDetector()
    private val schemaVersion = 5

    suspend fun open(source: DocumentSource, options: OpenOptions): ReaderResult<TxtOpenResult> {
        return withContext(ioDispatcher) {
            try {
                cacheDir.mkdirs()
                val sampleHash = computeSampleHash(source, 64 * 1024)
                val documentId = computeDocumentId(source, sampleHash)
                val files = buildFiles(documentId)
                files.bookDir.mkdirs()
                files.paginationDir.mkdirs()

                RandomAccessFile(files.lockFile, "rw").channel.use { lockChannel ->
                    lockChannel.lock().use {
                        when (val encodingResult = encodingDetector.detect(source, options.textEncoding)) {
                            is ReaderResult.Err -> encodingResult
                            is ReaderResult.Ok -> {
                                val detected = encodingResult.value.charset.name()
                                val cached = tryLoadCached(files, source, sampleHash, detected)
                                if (cached != null) {
                                    return@use ReaderResult.Ok(
                                        TxtOpenResult(
                                            documentId = documentId,
                                            files = files,
                                            meta = cached
                                        )
                                    )
                                }

                                val rebuilt = buildCache(
                                    files = files,
                                    source = source,
                                    sampleHash = sampleHash,
                                    charsetName = detected
                                )
                                ReaderResult.Ok(
                                    TxtOpenResult(
                                        documentId = documentId,
                                        files = files,
                                        meta = rebuilt
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
            }
        }
    }

    private fun buildFiles(documentId: DocumentId): TxtBookFiles {
        val bookDir = File(cacheDir, documentId.value)
        return TxtBookFiles(
            bookDir = bookDir,
            lockFile = File(bookDir, "build.lock"),
            contentU16 = File(bookDir, "content.u16"),
            metaJson = File(bookDir, "meta.json"),
            outlineJson = File(bookDir, "outline.json"),
            paginationDir = File(bookDir, "pagemap"),
            softBreakIdx = File(bookDir, "softbreak.idx"),
            softBreakLock = File(bookDir, "softbreak.lock"),
            bloomIdx = File(bookDir, "tri_bloom.idx"),
            bloomLock = File(bookDir, "tri_bloom.lock")
        )
    }

    private fun tryLoadCached(
        files: TxtBookFiles,
        source: DocumentSource,
        sampleHash: String,
        expectedCharset: String
    ): TxtMeta? {
        if (!files.contentU16.exists() || !files.metaJson.exists()) {
            return null
        }
        val json = JSONObject(files.metaJson.readText())
        val meta = TxtMeta.fromJson(json)
        if (meta.version != schemaVersion) {
            return null
        }
        if (meta.sourceUri != source.uri.toString()) {
            return null
        }
        val cachedSize = meta.sizeBytes ?: -1L
        val currentSize = source.sizeBytes ?: -1L
        if (cachedSize != currentSize) {
            return null
        }
        if (meta.sampleHash != sampleHash) {
            return null
        }
        if (!meta.originalCharset.equals(expectedCharset, ignoreCase = true)) {
            return null
        }
        if (meta.lengthChars != files.contentU16.length() / 2L) {
            return null
        }
        return meta
    }

    private suspend fun buildCache(
        files: TxtBookFiles,
        source: DocumentSource,
        sampleHash: String,
        charsetName: String
    ): TxtMeta {
        val charset = java.nio.charset.Charset.forName(charsetName)
        val temp = File(files.bookDir, "content.u16.tmp")
        prepareTempFile(temp)

        val content = writeUtf16Content(source = source, charset = charset, temp = temp)

        replaceFileAtomically(tempFile = temp, targetFile = files.contentU16)

        val meta = TxtMeta(
            version = schemaVersion,
            sourceUri = source.uri.toString(),
            displayName = source.displayName,
            sizeBytes = source.sizeBytes,
            sampleHash = sampleHash,
            originalCharset = charset.name(),
            lengthChars = content.lengthChars,
            hardWrapLikely = content.hardWrapLikely,
            createdAtEpochMs = System.currentTimeMillis()
        )
        files.metaJson.writeText(meta.toJson().toString())
        return meta
    }

    private suspend fun writeUtf16Content(
        source: DocumentSource,
        charset: java.nio.charset.Charset,
        temp: File
    ): CacheContent {
        val writer = Utf16LeFileWriter(temp)
        val stats = LineStatsCollector(sampleCapacity = 6000)
        var pendingCr = false
        var lineLength = 0
        var lastNonSpace: Char? = null
        var isFirstChar = true

        source.openInputStream().use { raw ->
            val input = BufferedInputStream(raw)
            val reader = input.reader(charset)
            val buffer = CharArray(16_384)

            while (true) {
                coroutineContext.ensureActive()
                val read = reader.read(buffer)
                if (read < 0) {
                    break
                }
                var i = 0
                while (i < read) {
                    var c = buffer[i]

                    if (isFirstChar && c == '\uFEFF') {
                        isFirstChar = false
                        i++
                        continue
                    }
                    isFirstChar = false

                    if (pendingCr) {
                        if (c == '\n') {
                            writeNewLine(writer, stats, lineLength, lastNonSpace)
                            lineLength = 0
                            lastNonSpace = null
                            pendingCr = false
                            i++
                            continue
                        } else {
                            writeNewLine(writer, stats, lineLength, lastNonSpace)
                            lineLength = 0
                            lastNonSpace = null
                            pendingCr = false
                        }
                    }

                    when (c) {
                        '\r' -> pendingCr = true
                        '\n' -> {
                            writeNewLine(writer, stats, lineLength, lastNonSpace)
                            lineLength = 0
                            lastNonSpace = null
                        }

                        else -> {
                            c = normalizeChar(c)
                            writer.writeChar(c)
                            lineLength++
                            if (!c.isWhitespace()) {
                                lastNonSpace = c
                            }
                        }
                    }
                    i++
                }
            }
        }

        if (pendingCr) {
            writeNewLine(writer, stats, lineLength, lastNonSpace)
            lineLength = 0
            lastNonSpace = null
        }
        stats.onLineFinishedIfHasData(lineLength, lastNonSpace)

        return try {
            CacheContent(
                lengthChars = writer.totalCharsWritten,
                hardWrapLikely = stats.snapshot().hardWrapLikely
            )
        } finally {
            writer.close()
        }
    }

    private fun writeNewLine(
        writer: Utf16LeFileWriter,
        stats: LineStatsCollector,
        lineLength: Int,
        lastNonSpace: Char?
    ) {
        writer.writeChar('\n')
        stats.onLineFinished(lineLength, lastNonSpace)
    }

    private fun normalizeChar(value: Char): Char {
        if (value == '\t') {
            return ' '
        }
        if (value < ' ' && value != '\n') {
            return ' '
        }
        if (value == '\u0000') {
            return ' '
        }
        return value
    }

    private suspend fun computeSampleHash(source: DocumentSource, limitBytes: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        source.openInputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var remaining = limitBytes
            while (remaining > 0) {
                coroutineContext.ensureActive()
                val read = input.read(buffer, 0, buffer.size.coerceAtMost(remaining))
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }
        return Hashing.toHexLower(digest.digest())
    }

    private fun computeDocumentId(source: DocumentSource, sampleHash: String): DocumentId {
        val raw = buildString {
            append(source.uri.toString())
            append('|')
            append(source.sizeBytes ?: -1L)
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(sampleHash)
        }
        return DocumentIds.fromSha256(raw = raw, length = 40)
    }

    private data class LineStatsSnapshot(
        val hardWrapLikely: Boolean
    )

    private data class CacheContent(
        val lengthChars: Long,
        val hardWrapLikely: Boolean
    )

    private class LineStatsCollector(
        private val sampleCapacity: Int
    ) {
        private val lineLengths = ArrayList<Int>(sampleCapacity)
        private var totalLines: Long = 0L
        private var blankLines: Long = 0L
        private var sentenceEndLines: Long = 0L

        fun onLineFinished(length: Int, lastNonSpace: Char?) {
            totalLines++
            if (lineLengths.size < sampleCapacity) {
                lineLengths.add(length)
            }
            if (length == 0) {
                blankLines++
            }
            if (lastNonSpace != null && STRONG_END_PUNCTUATION.contains(lastNonSpace)) {
                sentenceEndLines++
            }
        }

        fun onLineFinishedIfHasData(length: Int, lastNonSpace: Char?) {
            if (length > 0 || lastNonSpace != null) {
                onLineFinished(length, lastNonSpace)
            }
        }

        fun snapshot(): LineStatsSnapshot {
            if (lineLengths.size < MIN_SAMPLE_LINES) {
                return LineStatsSnapshot(hardWrapLikely = false)
            }

            val sorted = lineLengths.sorted()
            val median = sorted[sorted.size / 2]
            val mean = lineLengths.average()
            val variance = lineLengths.fold(0.0) { acc, value ->
                val delta = value - mean
                acc + delta * delta
            } / lineLengths.size.toDouble()
            val std = sqrt(variance)
            val coefficientOfVariation = if (mean <= 0.0) Double.POSITIVE_INFINITY else std / mean

            val blankRatio = if (totalLines == 0L) 0.0 else blankLines.toDouble() / totalLines.toDouble()
            val endPunctRatio = if (totalLines == 0L) 0.0 else sentenceEndLines.toDouble() / totalLines.toDouble()

            val likelyByClassic = median in HARD_WRAP_MEDIAN_MIN..HARD_WRAP_MEDIAN_MAX &&
                std <= HARD_WRAP_STD_MAX &&
                blankRatio <= HARD_WRAP_BLANK_RATIO_MAX &&
                endPunctRatio <= HARD_WRAP_END_PUNCT_RATIO_MAX &&
                coefficientOfVariation <= HARD_WRAP_COEFFICIENT_OF_VARIATION_MAX

            val likelyByShortStableWrap = median in SHORT_WRAP_MEDIAN_MIN..SHORT_WRAP_MEDIAN_MAX &&
                std <= SHORT_WRAP_STD_MAX &&
                blankRatio <= SHORT_WRAP_BLANK_RATIO_MAX &&
                endPunctRatio <= SHORT_WRAP_END_PUNCT_RATIO_MAX &&
                coefficientOfVariation <= SHORT_WRAP_COEFFICIENT_OF_VARIATION_MAX

            val likely = likelyByClassic || likelyByShortStableWrap

            return LineStatsSnapshot(hardWrapLikely = likely)
        }
    }

    private companion object {
        private val STRONG_END_PUNCTUATION = setOf('。', '！', '？', '.', '!', '?')
        private const val MIN_SAMPLE_LINES = 60
        private const val HARD_WRAP_MEDIAN_MIN = 18
        private const val HARD_WRAP_MEDIAN_MAX = 140
        private const val HARD_WRAP_STD_MAX = 30.0
        private const val HARD_WRAP_BLANK_RATIO_MAX = 0.22
        private const val HARD_WRAP_END_PUNCT_RATIO_MAX = 0.72
        private const val HARD_WRAP_COEFFICIENT_OF_VARIATION_MAX = 0.42

        private const val SHORT_WRAP_MEDIAN_MIN = 10
        private const val SHORT_WRAP_MEDIAN_MAX = 17
        private const val SHORT_WRAP_STD_MAX = 7.5
        private const val SHORT_WRAP_BLANK_RATIO_MAX = 0.12
        private const val SHORT_WRAP_END_PUNCT_RATIO_MAX = 0.88
        private const val SHORT_WRAP_COEFFICIENT_OF_VARIATION_MAX = 0.28
    }
}
