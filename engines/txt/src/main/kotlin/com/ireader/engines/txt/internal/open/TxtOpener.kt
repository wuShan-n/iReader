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
import com.ireader.engines.common.android.id.SourceDocumentIds
import com.ireader.engines.common.hash.Hashing
import com.ireader.engines.txt.internal.encoding.EncodingDetector
import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.DocumentId
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min
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
    private val schemaVersion = 7

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
            textStore = File(bookDir, "text.store"),
            metaJson = File(bookDir, "meta.json"),
            outlineIdx = File(bookDir, "outline.idx"),
            paginationDir = File(bookDir, "pagemap"),
            breakMap = File(bookDir, "break.map"),
            breakLock = File(bookDir, "break.lock"),
            searchIdx = File(bookDir, "search.idx"),
            searchLock = File(bookDir, "search.lock"),
            blockIdx = File(bookDir, "block.idx"),
            breakPatch = File(bookDir, "break.patch")
        )
    }

    private fun tryLoadCached(
        files: TxtBookFiles,
        source: DocumentSource,
        sampleHash: String,
        expectedCharset: String
    ): TxtMeta? {
        if (!files.textStore.exists() || !files.metaJson.exists()) {
            return null
        }

        val meta = runCatching {
            val json = JSONObject(files.metaJson.readText())
            TxtMeta.fromJson(json)
        }.getOrElse {
            return null
        }

        if (meta.version != schemaVersion) {
            return null
        }
        if (meta.sourceUri != source.uri.toString()) {
            return null
        }

        val contentBytes = files.textStore.length()
        if (contentBytes <= 0L || contentBytes % 2L != 0L) {
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

        val expectedCodeUnits = contentBytes / 2L
        if (meta.lengthCodeUnits != expectedCodeUnits) {
            return null
        }
        if (!files.blockIdx.exists() || !files.breakMap.exists()) {
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
        val temp = File(files.bookDir, "text.store.tmp")
        prepareTempFile(temp)

        val content = writeUtf16Content(source = source, charset = charset, temp = temp)

        replaceFileAtomically(tempFile = temp, targetFile = files.textStore)

        val meta = TxtMeta(
            version = schemaVersion,
            sourceUri = source.uri.toString(),
            displayName = source.displayName,
            sizeBytes = source.sizeBytes,
            sampleHash = sampleHash,
            originalCharset = charset.name(),
            lengthChars = content.lengthChars,
            lengthCodeUnits = content.lengthChars,
            hardWrapLikely = content.hardWrapLikely,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val metaTemp = File(files.bookDir, "meta.json.tmp")
        prepareTempFile(metaTemp)
        metaTemp.writeText(meta.toJson().toString())
        replaceFileAtomically(tempFile = metaTemp, targetFile = files.metaJson)

        Utf16TextStore(files.textStore).use { store ->
            TxtBlockIndex.buildIfNeeded(
                file = files.blockIdx,
                store = store,
                meta = meta,
                ioDispatcher = ioDispatcher
            )
        }
        SoftBreakIndexBuilder.buildIfNeeded(
            files = files,
            meta = meta,
            ioDispatcher = ioDispatcher,
            profile = SoftBreakTuningProfile.BALANCED
        )
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
        val size = source.sizeBytes?.takeIf { it > 0L }
        val windowBytes = limitBytes.coerceAtLeast(1)
        val totalHeadBytes = (windowBytes * 3).coerceAtLeast(windowBytes)

        if (size != null && size > totalHeadBytes.toLong()) {
            val maxOffset = (size - windowBytes.toLong()).coerceAtLeast(0L)
            val midOffset = ((size / 2L) - (windowBytes.toLong() / 2L)).coerceIn(0L, maxOffset)
            val offsets = linkedSetOf(0L, midOffset, maxOffset)
            for (offset in offsets) {
                coroutineContext.ensureActive()
                val sample = readSampleWindow(source = source, offset = offset, maxBytes = windowBytes)
                if (sample.isNotEmpty()) {
                    digest.update(sample)
                }
            }
            return Hashing.toHexLower(digest.digest())
        }

        val headBytes = readSampleWindow(source = source, offset = 0L, maxBytes = totalHeadBytes)
        if (headBytes.isNotEmpty()) {
            digest.update(headBytes)
        }
        return Hashing.toHexLower(digest.digest())
    }

    private suspend fun readSampleWindow(source: DocumentSource, offset: Long, maxBytes: Int): ByteArray {
        val fdBytes = runCatching {
            readSampleWindowViaFd(source = source, offset = offset, maxBytes = maxBytes)
        }.getOrNull()
        if (fdBytes != null) {
            return fdBytes
        }

        return source.openInputStream().use { input ->
            skipFully(input = input, bytesToSkip = offset)
            readAtMost(input = input, maxBytes = maxBytes)
        }
    }

    private suspend fun readSampleWindowViaFd(source: DocumentSource, offset: Long, maxBytes: Int): ByteArray? {
        val pfd = source.openFileDescriptor("r") ?: return null
        return try {
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val channel = fis.channel
                channel.position(offset.coerceAtLeast(0L))
                val buffer = ByteArray(maxBytes)
                val byteBuffer = ByteBuffer.wrap(buffer)
                while (byteBuffer.hasRemaining()) {
                    coroutineContext.ensureActive()
                    val read = channel.read(byteBuffer)
                    if (read <= 0) {
                        break
                    }
                }
                val total = byteBuffer.position()
                if (total == buffer.size) {
                    buffer
                } else {
                    buffer.copyOf(total)
                }
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    private suspend fun readAtMost(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes.coerceAtLeast(1))
        var total = 0
        while (total < buffer.size) {
            coroutineContext.ensureActive()
            val read = input.read(buffer, total, buffer.size - total)
            if (read <= 0) {
                break
            }
            total += read
        }
        return if (total == buffer.size) buffer else buffer.copyOf(total)
    }

    private suspend fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip.coerceAtLeast(0L)
        val scratch = ByteArray(8 * 1024)
        while (remaining > 0L) {
            coroutineContext.ensureActive()
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

    private fun computeDocumentId(source: DocumentSource, sampleHash: String): DocumentId {
        return SourceDocumentIds.fromSourceSha256(
            source = source,
            length = 40,
            extraParts = listOf(sampleHash)
        )
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
