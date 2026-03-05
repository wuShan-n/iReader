# txt 模块 .kt 代码（不含测试）

## engines/txt\src\main\kotlin\com\ireader\engines\txt\di\TxtEngineModule.kt

```kotlin
package com.ireader.engines.txt.di

import android.content.Context
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.internal.provider.StoredTxtAnnotationProvider
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.provider.AnnotationStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TxtEngineModule {

    @Provides
    @IntoSet
    @Singleton
    fun provideTxtEngine(
        @ApplicationContext context: Context,
        annotationStore: AnnotationStore
    ): ReaderEngine {
        return TxtEngine(
            config = TxtEngineConfig(
                cacheDir = context.cacheDir,
                persistPagination = true,
                persistOutline = false,
                annotationProviderFactory = { documentId ->
                    StoredTxtAnnotationProvider(
                        documentId = documentId,
                        store = annotationStore
                    )
                }
            )
        )
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\encoding\EncodingDetector.kt

```kotlin
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
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\encoding\EncodingScorer.kt

```kotlin
package com.ireader.engines.txt.internal.encoding

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object EncodingScorer {

    fun pickBest(samples: List<ByteArray>): Charset {
        val candidates = listOf(
            Charsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charset.forName("Big5"),
            Charset.forName("Shift_JIS"),
            Charset.forName("windows-1252")
        )

        val nonEmpty = samples.filter(ByteArray::isNotEmpty)
        if (nonEmpty.isEmpty()) {
            return Charsets.UTF_8
        }

        return candidates.maxByOrNull { candidate ->
            nonEmpty.sumOf { bytes -> score(candidate, bytes) }
        } ?: Charsets.UTF_8
    }

    private fun score(charset: Charset, bytes: ByteArray): Int {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()

        if (decoded.isEmpty()) {
            return Int.MIN_VALUE
        }

        var replacement = 0
        var controls = 0
        var printable = 0
        var han = 0
        var hiragana = 0
        var katakana = 0
        var hangul = 0
        var latin = 0

        for (c in decoded) {
            when {
                c == '\uFFFD' -> replacement++

                c == '\n' || c == '\r' || c == '\t' -> printable++

                c < ' ' -> controls++

                else -> {
                    printable++
                    when (Character.UnicodeScript.of(c.code)) {
                        Character.UnicodeScript.HAN -> han++
                        Character.UnicodeScript.HIRAGANA -> hiragana++
                        Character.UnicodeScript.KATAKANA -> katakana++
                        Character.UnicodeScript.HANGUL -> hangul++
                        Character.UnicodeScript.LATIN -> latin++
                        else -> Unit
                    }
                }
            }
        }

        val total = decoded.length.coerceAtLeast(1)

        val replacementPenalty = replacement * 100_000 / total
        val controlPenalty = controls * 20_000 / total
        val hanBonus = han * 50_000 / total
        val kanaBonus = (hiragana + katakana) * 10_000 / total
        val hangulBonus = hangul * 50_000 / total
        val latinBonus = latin * 8_000 / total
        val printableBonus = printable * 2_000 / total

        return 1_000 +
            printableBonus +
            hanBonus +
            kanaBonus +
            hangulBonus +
            latinBonus -
            replacementPenalty -
            controlPenalty
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\link\LinkDetector.kt

```kotlin
package com.ireader.engines.txt.internal.link

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.LinkTarget

internal object LinkDetector {

    private val urlRegex = Regex("""(?i)\b(https?://[^\s<>"']+|www\.[^\s<>"']+)""")
    private val emailRegex = Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""")

    private val trimTailChars = setOf('.', ',', '，', '。', '!', '?', '！', '？', ')', ']', '}', '>', '"', '\'')

    fun detect(
        text: CharSequence,
        pageStartOffset: Long,
        maxOffset: Long,
        max: Int = 20
    ): List<DocumentLink> {
        if (text.isEmpty()) {
            return emptyList()
        }
        val source = text.toString()
        val out = ArrayList<DocumentLink>(8)
        val dedupe = HashSet<String>()

        fun append(startInclusive: Int, endExclusive: Int, isEmail: Boolean) {
            val clampedStart = startInclusive.coerceIn(0, source.length)
            var clampedEnd = endExclusive.coerceIn(clampedStart, source.length)
            while (clampedEnd > clampedStart && trimTailChars.contains(source[clampedEnd - 1])) {
                clampedEnd--
            }
            if (clampedEnd <= clampedStart) {
                return
            }

            val value = source.substring(clampedStart, clampedEnd)
            val normalized = if (isEmail) {
                "mailto:$value"
            } else {
                normalizeUrl(value)
            }

            val dedupeKey = "$normalized|$clampedStart|$clampedEnd"
            if (!dedupe.add(dedupeKey)) {
                return
            }

            val globalStart = (pageStartOffset + clampedStart.toLong()).coerceAtMost(maxOffset)
            val globalEnd = (pageStartOffset + clampedEnd.toLong()).coerceAtMost(maxOffset)
            if (globalEnd <= globalStart) {
                return
            }

            out.add(
                DocumentLink(
                    target = LinkTarget.External(normalized),
                    title = value,
                    range = TxtBlockLocatorCodec.rangeForOffsets(
                        startOffset = globalStart,
                        endOffset = globalEnd,
                        maxOffset = maxOffset
                    ),
                    bounds = null
                )
            )
        }

        for (match in urlRegex.findAll(source)) {
            if (out.size >= max) {
                break
            }
            append(
                startInclusive = match.range.first,
                endExclusive = match.range.last + 1,
                isEmail = false
            )
        }
        for (match in emailRegex.findAll(source)) {
            if (out.size >= max) {
                break
            }
            append(
                startInclusive = match.range.first,
                endExclusive = match.range.last + 1,
                isEmail = true
            )
        }

        return out
    }

    private fun normalizeUrl(value: String): String {
        return if (value.startsWith("www.", ignoreCase = true)) {
            "http://$value"
        } else {
            value
        }
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\locator\TxtBlockLocatorCodec.kt

```kotlin
package com.ireader.engines.txt.internal.locator

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

/**
 * TXT locator format:
 * - preferred: scheme=txt.offset, value="<utf16Offset>"
 * - legacy: scheme=txt.block, value="<blockStartOffset>:<charOffsetInBlock>"
 */
internal object TxtBlockLocatorCodec {

    fun locatorForOffset(
        offset: Long,
        maxOffset: Long,
        extras: Map<String, String> = emptyMap()
    ): Locator {
        val safeMax = maxOffset.coerceAtLeast(0L)
        val safe = offset.coerceIn(0L, safeMax)
        return Locator(
            scheme = LocatorSchemes.TXT_OFFSET,
            value = safe.toString(),
            extras = extras
        )
    }

    fun rangeForOffsets(
        startOffset: Long,
        endOffset: Long,
        maxOffset: Long
    ): LocatorRange {
        return LocatorRange(
            start = locatorForOffset(startOffset, maxOffset),
            end = locatorForOffset(endOffset, maxOffset)
        )
    }

    fun parseOffset(locator: Locator): Long? {
        return when (locator.scheme) {
            LocatorSchemes.TXT_OFFSET -> parseOffsetValue(locator.value)
            LocatorSchemes.TXT_BLOCK -> parseLegacyBlockOffsetValue(locator.value)
            else -> null
        }
    }

    fun parseOffset(locator: Locator, maxOffset: Long): Long? {
        val parsed = parseOffset(locator) ?: return null
        return parsed.coerceIn(0L, maxOffset.coerceAtLeast(0L))
    }

    fun parseOffsetValue(value: String): Long? {
        val offset = value.toLongOrNull() ?: return null
        return offset.takeIf { it >= 0L }
    }

    fun parseLegacyBlockOffsetValue(value: String): Long? {
        val separator = value.indexOf(':')
        if (separator <= 0 || separator >= value.lastIndex) {
            return null
        }
        val blockStart = value.substring(0, separator).toLongOrNull() ?: return null
        val inBlock = value.substring(separator + 1).toLongOrNull() ?: return null
        if (blockStart < 0L || inBlock < 0L || inBlock >= BLOCK_CHARS) {
            return null
        }
        val sum = blockStart + inBlock
        if (sum < blockStart) {
            return null
        }
        return sum
    }

    private const val BLOCK_CHARS = 2048L
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\open\TxtBookFiles.kt

```kotlin
package com.ireader.engines.txt.internal.open

import java.io.File

internal data class TxtBookFiles(
    val bookDir: File,
    val lockFile: File,
    val contentU16: File,
    val metaJson: File,
    val outlineJson: File,
    val paginationDir: File,
    val softBreakIdx: File,
    val softBreakLock: File,
    val bloomIdx: File,
    val bloomLock: File
)
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\open\TxtDocument.kt

```kotlin
@file:Suppress("LongParameterList", "TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.open

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.render.TxtController
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtDocument(
    override val id: DocumentId,
    private val source: DocumentSource,
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    override val openOptions: OpenOptions,
    private val persistPagination: Boolean,
    private val persistOutline: Boolean,
    private val maxPageCache: Int,
    private val annotationProviderFactory: ((DocumentId) -> AnnotationProvider?)?,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher
) : ReaderDocument {

    override val format: BookFormat = BookFormat.TXT

    override val capabilities: DocumentCapabilities
        get() = DocumentCapabilities(
            reflowable = true,
            fixedLayout = false,
            outline = true,
            search = true,
            textExtraction = true,
            annotations = annotationProviderFactory != null,
            selection = true,
            links = true
        )

    private val store: Utf16TextStore by lazy {
        Utf16TextStore(files.contentU16)
    }

    override suspend fun metadata(): ReaderResult<DocumentMetadata> {
        return ReaderResult.Ok(
            DocumentMetadata(
                title = source.displayName?.substringBeforeLast('.'),
                extra = mapOf(
                    "charset" to meta.originalCharset,
                    "lengthChars" to meta.lengthChars.toString()
                )
            )
        )
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> {
        return withContext(defaultDispatcher) {
            try {
                val config = initialConfig as? RenderConfig.ReflowText
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("TXT engine requires RenderConfig.ReflowText")
                    )
                val effectiveConfig = config.sanitized()
                val initialOffset = when {
                    initialLocator == null -> 0L
                    else -> TxtBlockLocatorCodec.parseOffset(initialLocator, store.lengthChars) ?: 0L
                }.coerceIn(0L, store.lengthChars)
                val annotationProvider = annotationProviderFactory?.invoke(id)

                val controller = TxtController(
                    documentKey = id.value,
                    store = store,
                    meta = meta,
                    initialLocator = initialLocator,
                    initialOffset = initialOffset,
                    initialConfig = effectiveConfig,
                    maxPageCache = maxPageCache,
                    persistPagination = persistPagination,
                    files = files,
                    annotationProvider = annotationProvider,
                    ioDispatcher = ioDispatcher,
                    defaultDispatcher = defaultDispatcher
                )
                ReaderResult.Ok(
                    TxtSession(
                        controller = controller,
                        files = files,
                        meta = meta,
                        store = store,
                        ioDispatcher = ioDispatcher,
                        persistOutline = persistOutline,
                        annotationsProvider = annotationProvider
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
            }
        }
    }

    override fun close() {
        runCatching { store.close() }
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\open\TxtMeta.kt

```kotlin
package com.ireader.engines.txt.internal.open

import org.json.JSONObject

internal data class TxtMeta(
    val version: Int,
    val sourceUri: String,
    val displayName: String?,
    val sizeBytes: Long?,
    val sampleHash: String,
    val originalCharset: String,
    val lengthChars: Long,
    val hardWrapLikely: Boolean,
    val createdAtEpochMs: Long
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("sourceUri", sourceUri)
            put("displayName", displayName)
            if (sizeBytes != null) {
                put("sizeBytes", sizeBytes)
            }
            put("sampleHash", sampleHash)
            put("originalCharset", originalCharset)
            put("lengthChars", lengthChars)
            put("hardWrapLikely", hardWrapLikely)
            put("createdAtEpochMs", createdAtEpochMs)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TxtMeta {
            return TxtMeta(
                version = json.getInt("version"),
                sourceUri = json.getString("sourceUri"),
                displayName = json.optString("displayName").takeIf { it.isNotEmpty() },
                sizeBytes = if (json.has("sizeBytes")) json.getLong("sizeBytes") else null,
                sampleHash = json.getString("sampleHash"),
                originalCharset = json.getString("originalCharset"),
                lengthChars = json.getLong("lengthChars"),
                hardWrapLikely = json.optBoolean("hardWrapLikely", false),
                createdAtEpochMs = json.optLong("createdAtEpochMs", 0L)
            )
        }
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\open\TxtOpener.kt

```kotlin
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
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\open\TxtOpenResult.kt

```kotlin
package com.ireader.engines.txt.internal.open

import com.ireader.reader.model.DocumentId

internal data class TxtOpenResult(
    val documentId: DocumentId,
    val files: TxtBookFiles,
    val meta: TxtMeta
)

```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\open\TxtSession.kt

```kotlin
package com.ireader.engines.txt.internal.open

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.provider.TxtSelectionManager
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher

internal class TxtSession(
    controller: ReaderController,
    files: TxtBookFiles,
    meta: TxtMeta,
    store: Utf16TextStore,
    ioDispatcher: CoroutineDispatcher,
    persistOutline: Boolean,
    annotationsProvider: AnnotationProvider?,
    selectionManager: TxtSelectionManager = TxtSelectionManager(
        store = store,
        ioDispatcher = ioDispatcher
    )
) : BaseReaderSession(
    id = SessionId(UUID.randomUUID().toString()),
    controller = controller,
    outline = TxtOutlineProvider(
        files = files,
        meta = meta,
        store = store,
        ioDispatcher = ioDispatcher,
        persistOutline = persistOutline
    ),
    search = TxtSearchProviderPro(
        files = files,
        store = store,
        meta = meta,
        ioDispatcher = ioDispatcher
    ),
    text = TxtTextProvider(
        store = store,
        ioDispatcher = ioDispatcher
    ),
    annotations = annotationsProvider,
    selection = selectionManager,
    selectionController = selectionManager
)
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\open\Utf16LeFileWriter.kt

```kotlin
package com.ireader.engines.txt.internal.open

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

internal class Utf16LeFileWriter(
    file: File,
    charBufferSize: Int = 16_384
) : Closeable {

    private val output = FileOutputStream(file)
    private val channel: FileChannel = output.channel
    private val buffer: ByteBuffer = ByteBuffer
        .allocateDirect(charBufferSize * 2)
        .order(ByteOrder.LITTLE_ENDIAN)

    var totalCharsWritten: Long = 0L
        private set

    fun writeChar(value: Char) {
        if (buffer.remaining() < 2) {
            flushBuffer()
        }
        buffer.putChar(value)
        totalCharsWritten++
    }

    private fun flushBuffer() {
        buffer.flip()
        while (buffer.hasRemaining()) {
            channel.write(buffer)
        }
        buffer.clear()
    }

    override fun close() {
        flushBuffer()
        channel.close()
        output.close()
    }
}

```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\provider\ChapterDetector.kt

```kotlin
@file:Suppress("MagicNumber", "ReturnCount")

package com.ireader.engines.txt.internal.provider

internal class ChapterDetector {
    private val chinese = Regex("^第[零一二三四五六七八九十百千万0-9]{1,9}[章节回卷部篇].{0,30}$")
    private val english = Regex("^(Chapter|CHAPTER)\\s+\\d+.*$")
    private val prologue = Regex("^(Prologue|Epilogue|PROLOGUE|EPILOGUE)$")
    private val toc = Regex("^(目录|目\\s*录|contents)$", RegexOption.IGNORE_CASE)

    fun isChapterTitle(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank()) {
            return false
        }
        if (normalized.length > 48) {
            return false
        }
        return chinese.matches(normalized) || english.matches(normalized) || prologue.matches(normalized)
    }

    fun isChapterBoundaryTitle(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank()) {
            return false
        }
        return isChapterTitle(normalized) || toc.matches(normalized)
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\provider\KmpMatcher.kt

```kotlin
package com.ireader.engines.txt.internal.provider

internal class KmpMatcher(
    pattern: CharArray,
    caseSensitive: Boolean
) {
    private val normalizedPattern = if (caseSensitive) {
        pattern
    } else {
        CharArray(pattern.size) { index -> pattern[index].lowercaseChar() }
    }
    private val lps = buildLps(normalizedPattern)
    private val ignoreCase = !caseSensitive

    fun findAll(text: CharArray): List<Int> {
        if (normalizedPattern.isEmpty() || text.isEmpty()) {
            return emptyList()
        }
        val hits = ArrayList<Int>()
        var i = 0
        var j = 0
        while (i < text.size) {
            val textChar = normalize(text[i])
            val patternChar = normalizedPattern[j]
            if (textChar == patternChar) {
                i++
                j++
                if (j == normalizedPattern.size) {
                    hits.add(i - j)
                    j = lps[j - 1]
                }
            } else if (j != 0) {
                j = lps[j - 1]
            } else {
                i++
            }
        }
        return hits
    }

    private fun normalize(c: Char): Char {
        return if (ignoreCase) c.lowercaseChar() else c
    }

    private fun buildLps(pattern: CharArray): IntArray {
        val out = IntArray(pattern.size)
        var len = 0
        var i = 1
        while (i < pattern.size) {
            if (pattern[i] == pattern[len]) {
                len++
                out[i] = len
                i++
            } else if (len != 0) {
                len = out[len - 1]
            } else {
                out[i] = 0
                i++
            }
        }
        return out
    }
}

```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\provider\StoredTxtAnnotationProvider.kt

```kotlin
package com.ireader.engines.txt.internal.provider

import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import kotlinx.coroutines.flow.Flow

internal class StoredTxtAnnotationProvider(
    private val documentId: DocumentId,
    private val store: AnnotationStore
) : AnnotationProvider {

    override fun observeAll(): Flow<List<Annotation>> = store.observe(documentId)

    override suspend fun listAll(): ReaderResult<List<Annotation>> = store.list(documentId)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> =
        store.query(documentId, query)

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> =
        store.create(documentId, draft)

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> =
        store.update(documentId, annotation)

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> =
        store.delete(documentId, id)

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        val annotations = when (val result = store.query(documentId, query)) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> return ReaderResult.Err(result.error)
        }
        return ReaderResult.Ok(
            annotations.map { annotation ->
                when (val anchor = annotation.anchor) {
                    is AnnotationAnchor.ReflowRange -> Decoration.Reflow(
                        range = anchor.range,
                        style = annotation.style
                    )

                    is AnnotationAnchor.FixedRects -> Decoration.Fixed(
                        page = anchor.page,
                        rects = anchor.rects,
                        style = annotation.style
                    )
                }
            }
        )
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\provider\TxtOutlineProvider.kt

```kotlin
@file:Suppress("MagicNumber", "NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class TxtOutlineProvider(
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    private val store: Utf16TextStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val persistOutline: Boolean
) : OutlineProvider {

    private val detector = ChapterDetector()

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        return withContext(ioDispatcher) {
            try {
                if (persistOutline) {
                    val cached = loadFromCache()
                    if (cached != null) {
                        return@withContext ReaderResult.Ok(cached)
                    }
                }

                val detected = detectOutline()
                if (persistOutline) {
                    saveToCache(detected)
                }
                ReaderResult.Ok(detected)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
            }
        }
    }

    private fun loadFromCache(): List<OutlineNode>? {
        val file = files.outlineJson
        if (!file.exists()) {
            return null
        }
        val json = JSONObject(file.readText())
        if (json.optInt("version", -1) != 1) {
            return null
        }
        if (json.optString("sampleHash", "") != meta.sampleHash) {
            return null
        }
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<OutlineNode>(items.length())
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val title = item.optString("title")
            val offset = item.optLong("offset", -1L)
            if (title.isBlank() || offset < 0L) {
                continue
            }
            out.add(
                OutlineNode(
                    title = title,
                    locator = TxtBlockLocatorCodec.locatorForOffset(offset, store.lengthChars)
                )
            )
        }
        return out
    }

    private fun saveToCache(outline: List<OutlineNode>) {
        val items = JSONArray()
        for (node in outline) {
            val offset = TxtBlockLocatorCodec.parseOffset(node.locator, store.lengthChars) ?: continue
            items.put(
                JSONObject().apply {
                    put("title", node.title)
                    put("offset", offset)
                }
            )
        }
        val root = JSONObject().apply {
            put("version", 1)
            put("sampleHash", meta.sampleHash)
            put("items", items)
        }
        files.outlineJson.writeText(root.toString())
    }

    private suspend fun detectOutline(): List<OutlineNode> {
        val out = ArrayList<OutlineNode>(64)
        val seen = HashSet<String>()
        val chunkChars = 64_000
        var carry = ""
        var cursor = 0L

        while (cursor < store.lengthChars) {
            coroutineContext.ensureActive()
            val readCount = min(chunkChars.toLong(), store.lengthChars - cursor).toInt()
            val chunk = store.readString(cursor, readCount)
            if (chunk.isEmpty()) {
                break
            }
            val merged = carry + chunk
            var lineStart = 0
            while (true) {
                val newline = merged.indexOf('\n', lineStart)
                if (newline < 0) {
                    break
                }
                val line = merged.substring(lineStart, newline).trim()
                if (detector.isChapterTitle(line) && seen.add(line)) {
                    val offset = (cursor - carry.length + lineStart).coerceAtLeast(0)
                    out.add(
                        OutlineNode(
                            title = line,
                            locator = TxtBlockLocatorCodec.locatorForOffset(offset, store.lengthChars)
                        )
                    )
                    if (out.size >= MAX_OUTLINE_ITEMS) {
                        return out
                    }
                }
                lineStart = newline + 1
            }
            carry = merged.substring(lineStart)
            cursor += readCount.toLong()
        }
        return out
    }

    private companion object {
        private const val MAX_OUTLINE_ITEMS = 300
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\provider\TxtSearchProviderPro.kt

```kotlin
@file:Suppress("MagicNumber")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.search.TrigramBloomIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import java.io.RandomAccessFile
import kotlin.math.min
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TxtSearchProviderPro(
    private val files: TxtBookFiles,
    private val store: Utf16TextStore,
    private val meta: TxtMeta,
    private val ioDispatcher: CoroutineDispatcher
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = channelFlow {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return@channelFlow
        }

        val context = SearchContext(
            query = normalizedQuery,
            options = options,
            startOffset = options.startFrom
                ?.let { TxtBlockLocatorCodec.parseOffset(it, store.lengthChars) }
                ?: 0L
        )
        val bloom = TrigramBloomIndex.openIfValid(files.bloomIdx, meta)

        if (shouldBuildBloomAsync(bloom, context.query.length)) {
            scheduleBloomBuild()
        }

        if (bloom != null && context.query.length >= BLOOM_MIN_QUERY_LENGTH) {
            fastSearchWithBloom(
                bloom = bloom,
                context = context
            )
        } else {
            streamingScan(context)
        }
    }

    private fun shouldBuildBloomAsync(bloom: TrigramBloomIndex?, queryLength: Int): Boolean {
        return bloom == null &&
            queryLength >= BLOOM_MIN_QUERY_LENGTH &&
            meta.lengthChars >= BLOOM_MIN_BOOK_CHARS
    }

    private fun ProducerScope<SearchHit>.scheduleBloomBuild() {
        // Build index opportunistically; current search falls back to streaming scan.
        launch(ioDispatcher) {
            TrigramBloomIndex.buildIfNeeded(
                file = files.bloomIdx,
                lockFile = files.bloomLock,
                store = store,
                meta = meta,
                ioDispatcher = ioDispatcher
            )
        }
    }

    private suspend fun ProducerScope<SearchHit>.fastSearchWithBloom(
        bloom: TrigramBloomIndex,
        context: SearchContext
    ) = withContext(ioDispatcher) {
        val matcher = KmpMatcher(
            pattern = context.query.toCharArray(),
            caseSensitive = context.options.caseSensitive
        )
        val trigramHashes = bloom.buildQueryTrigramHashes(context.query.lowercase())
        val blocks = bloom.blocksCount()
        val startBlock = (context.startOffset / bloom.blockChars).toInt().coerceAtLeast(0)
        val state = BloomScanState(
            matcher = matcher,
            queryLength = context.query.length,
            maxHits = context.options.maxHits,
            startOffset = context.startOffset,
            wholeWord = context.options.wholeWord
        )

        RandomAccessFile(files.bloomIdx, "r").use { raf ->
            for (blockIndex in startBlock until blocks) {
                coroutineContext.ensureActive()
                if (state.reachedMaxHits()) {
                    break
                }
                if (bloom.mayContainAll(raf, blockIndex, trigramHashes)) {
                    scanBloomBlockWithNeighbors(
                        bloom = bloom,
                        blockIndex = blockIndex,
                        blocksCount = blocks,
                        state = state
                    )
                }
            }
        }
    }

    private suspend fun ProducerScope<SearchHit>.scanBloomBlockWithNeighbors(
        bloom: TrigramBloomIndex,
        blockIndex: Int,
        blocksCount: Int,
        state: BloomScanState
    ) {
        for (candidate in intArrayOf(blockIndex, blockIndex - 1, blockIndex + 1)) {
            if (state.reachedMaxHits()) {
                break
            }
            scanBloomCandidateBlock(
                bloom = bloom,
                blockIndex = candidate,
                blocksCount = blocksCount,
                state = state
            )
        }
    }

    @Suppress("NestedBlockDepth")
    private suspend fun ProducerScope<SearchHit>.scanBloomCandidateBlock(
        blockIndex: Int,
        blocksCount: Int,
        bloom: TrigramBloomIndex,
        state: BloomScanState
    ) {
        if (!state.shouldScanBlock(blockIndex, blocksCount)) {
            return
        }

        val range = bloom.blockRange(blockIndex)
        val scanStart = (range.start - (state.queryLength + BLOOM_SCAN_PADDING).toLong())
            .coerceAtLeast(state.startOffset)
        val scanEnd = (range.endExclusive + (state.queryLength + BLOOM_SCAN_PADDING).toLong())
            .coerceAtMost(store.lengthChars)
        val readLength = (scanEnd - scanStart).toInt().coerceAtLeast(0)
        if (readLength > 0) {
            val chunk = store.readChars(scanStart, readLength)
            var canSend = true
            for (hitIndex in state.matcher.findAll(chunk)) {
                coroutineContext.ensureActive()
                if (canSend && !state.reachedMaxHits()) {
                    val globalStart = scanStart + hitIndex.toLong()
                    val passesWordBoundary = !state.wholeWord ||
                        isWholeWord(chars = chunk, start = hitIndex, len = state.queryLength)
                    if (passesWordBoundary && state.canEmit(globalStart)) {
                        val globalEnd = globalStart + state.queryLength.toLong()
                        canSend = emitHit(
                            startOffset = globalStart,
                            endOffset = globalEnd,
                            queryLength = state.queryLength
                        )
                        if (canSend) {
                            state.markEmitted()
                        }
                    }
                }
                if (!canSend || state.reachedMaxHits()) {
                    break
                }
            }
        }
    }

    private suspend fun ProducerScope<SearchHit>.streamingScan(
        context: SearchContext
    ) = withContext(ioDispatcher) {
        val pattern = context.query.toCharArray()
        val matcher = KmpMatcher(pattern = pattern, caseSensitive = context.options.caseSensitive)
        val overlap = (pattern.size - 1).coerceAtLeast(0)
        val maxHits = context.options.maxHits

        var carry = CharArray(0)
        var cursor = context.startOffset
        var emitted = 0
        var keepScanning = true

        while (keepScanning && cursor < store.lengthChars && emitted < maxHits) {
            coroutineContext.ensureActive()
            val readCount = min(STREAM_CHUNK_SIZE.toLong(), store.lengthChars - cursor).toInt()
            val chunk = store.readChars(cursor, readCount)
            if (chunk.isEmpty()) {
                keepScanning = false
            } else {
                val merged = mergeChunks(carry, chunk)
                val mergedStart = cursor - carry.size.toLong()
                var canSend = true
                for (matchIndex in matcher.findAll(merged)) {
                    coroutineContext.ensureActive()
                    if (!canSend || emitted >= maxHits) {
                        break
                    }
                    val globalStart = mergedStart + matchIndex.toLong()
                    val candidate = StreamingCandidate(
                        matchIndex = matchIndex,
                        globalStart = globalStart,
                        carrySize = carry.size
                    )
                    val eligible = isEligibleStreamingHit(
                        candidate = candidate,
                        merged = merged,
                        context = context,
                        queryLength = pattern.size
                    )
                    if (eligible) {
                        val globalEnd = globalStart + pattern.size.toLong()
                        canSend = emitHit(
                            startOffset = globalStart,
                            endOffset = globalEnd,
                            queryLength = pattern.size
                        )
                        if (canSend) {
                            emitted++
                        }
                    }
                }
                keepScanning = canSend
                carry = keepTrailingOverlap(merged, overlap)
            }
            cursor += readCount.toLong()
        }
    }

    private suspend fun ProducerScope<SearchHit>.emitHit(
        startOffset: Long,
        endOffset: Long,
        queryLength: Int
    ): Boolean {
        val sent = trySend(
            SearchHit(
                range = TxtBlockLocatorCodec.rangeForOffsets(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    maxOffset = store.lengthChars
                ),
                excerpt = buildExcerpt(startOffset, queryLength),
                sectionTitle = null
            )
        )
        return sent.isSuccess
    }

    private fun isEligibleStreamingHit(
        candidate: StreamingCandidate,
        merged: CharArray,
        context: SearchContext,
        queryLength: Int
    ): Boolean {
        val startsInCarry = candidate.matchIndex + queryLength <= candidate.carrySize
        val startsBeforeRequestedOffset = candidate.globalStart < context.startOffset
        val failsWordBoundary = context.options.wholeWord &&
            !isWholeWord(chars = merged, start = candidate.matchIndex, len = queryLength)
        return !startsInCarry && !startsBeforeRequestedOffset && !failsWordBoundary
    }

    private fun buildExcerpt(matchStart: Long, patternLength: Int): String {
        val center = matchStart + patternLength / 2L
        return store.readAround(center, before = 64, after = 128)
            .replace('\n', ' ')
            .trim()
    }

    private data class SearchContext(
        val query: String,
        val options: SearchOptions,
        val startOffset: Long
    )

    private data class StreamingCandidate(
        val matchIndex: Int,
        val globalStart: Long,
        val carrySize: Int
    )

    private data class BloomScanState(
        val matcher: KmpMatcher,
        val queryLength: Int,
        val maxHits: Int,
        val startOffset: Long,
        val wholeWord: Boolean,
        private val emittedStarts: MutableSet<Long> = hashSetOf(),
        private val scannedBlocks: MutableSet<Int> = hashSetOf()
    ) {
        private var emittedCount: Int = 0

        fun reachedMaxHits(): Boolean = emittedCount >= maxHits

        fun shouldScanBlock(blockIndex: Int, blocksCount: Int): Boolean {
            if (blockIndex < 0 || blockIndex >= blocksCount || reachedMaxHits()) {
                return false
            }
            return scannedBlocks.add(blockIndex)
        }

        fun canEmit(globalStart: Long): Boolean {
            if (globalStart < startOffset || !emittedStarts.add(globalStart)) {
                return false
            }
            return true
        }

        fun markEmitted() {
            emittedCount++
        }
    }

    private companion object {
        private const val BLOOM_MIN_QUERY_LENGTH = 3
        private const val BLOOM_MIN_BOOK_CHARS = 1_000_000L
        private const val BLOOM_SCAN_PADDING = 8
        private const val STREAM_CHUNK_SIZE = 64_000
    }
}

private fun mergeChunks(carry: CharArray, chunk: CharArray): CharArray {
    val merged = CharArray(carry.size + chunk.size)
    if (carry.isNotEmpty()) {
        System.arraycopy(carry, 0, merged, 0, carry.size)
    }
    System.arraycopy(chunk, 0, merged, carry.size, chunk.size)
    return merged
}

private fun keepTrailingOverlap(merged: CharArray, overlap: Int): CharArray {
    if (overlap <= 0) {
        return CharArray(0)
    }
    val keep = min(overlap, merged.size)
    return merged.copyOfRange(merged.size - keep, merged.size)
}

private fun isWholeWord(chars: CharArray, start: Int, len: Int): Boolean {
    val before = if (start - 1 >= 0) chars[start - 1] else null
    val after = if (start + len < chars.size) chars[start + len] else null
    val beforeOk = before == null || !Character.isLetterOrDigit(before)
    val afterOk = after == null || !Character.isLetterOrDigit(after)
    return beforeOk && afterOk
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\provider\TxtSelectionManager.kt

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.LocatorRange
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TxtSelectionManager(
    private val store: Utf16TextStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxSelectedChars: Int = 4_096
) : SelectionProvider, SelectionController {

    private val mutex = Mutex()
    private var activeAnchorOffset: Long? = null
    private var current: SelectionProvider.Selection? = null

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return ReaderResult.Ok(mutex.withLock { current })
    }

    override suspend fun clearSelection(): ReaderResult<Unit> = clear()

    override suspend fun start(locator: Locator): ReaderResult<Unit> {
        val offset = parseOffset(locator)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid TXT locator: $locator"))
        return withContext(ioDispatcher) {
            runCatching {
                mutex.withLock {
                    activeAnchorOffset = offset
                    current = buildSelection(anchorOffset = offset, edgeOffset = offset)
                }
            }.fold(
                onSuccess = { ReaderResult.Ok(Unit) },
                onFailure = { ReaderResult.Err(it.toReaderError()) }
            )
        }
    }

    override suspend fun update(locator: Locator): ReaderResult<Unit> {
        val edgeOffset = parseOffset(locator)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid TXT locator: $locator"))
        return withContext(ioDispatcher) {
            runCatching {
                mutex.withLock {
                    val anchor = activeAnchorOffset ?: edgeOffset.also { activeAnchorOffset = it }
                    current = buildSelection(anchorOffset = anchor, edgeOffset = edgeOffset)
                }
            }.fold(
                onSuccess = { ReaderResult.Ok(Unit) },
                onFailure = { ReaderResult.Err(it.toReaderError()) }
            )
        }
    }

    override suspend fun finish(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun clear(): ReaderResult<Unit> {
        return mutex.withLock {
            activeAnchorOffset = null
            current = null
            ReaderResult.Ok(Unit)
        }
    }

    private fun parseOffset(locator: Locator): Long? {
        return TxtBlockLocatorCodec.parseOffset(locator, maxOffset = store.lengthChars)
    }

    private fun buildSelection(anchorOffset: Long, edgeOffset: Long): SelectionProvider.Selection {
        val startOffset = min(anchorOffset, edgeOffset).coerceIn(0L, store.lengthChars)
        val endOffset = max(anchorOffset, edgeOffset).coerceIn(0L, store.lengthChars)
        val startLocator = TxtBlockLocatorCodec.locatorForOffset(startOffset, store.lengthChars)
        val endLocator = TxtBlockLocatorCodec.locatorForOffset(endOffset, store.lengthChars)
        val textLength = (endOffset - startOffset).toInt().coerceAtLeast(0)
        val selectedText = if (textLength <= 0) {
            null
        } else {
            val cappedLength = textLength.coerceAtMost(maxSelectedChars)
            store.readString(startOffset, cappedLength).takeIf { it.isNotBlank() }
        }
        return SelectionProvider.Selection(
            locator = startLocator,
            start = startLocator,
            end = endLocator,
            selectedText = selectedText,
            extras = mapOf(
                LocatorExtraKeys.PROGRESSION to startLocator.extras[LocatorExtraKeys.PROGRESSION].orEmpty(),
                "selectionStartOffset" to startOffset.toString(),
                "selectionEndOffset" to endOffset.toString()
            ).filterValues { it.isNotBlank() }
        )
    }

    fun currentRangeOrNull(): LocatorRange? {
        val selection = current ?: return null
        val start = selection.start ?: return null
        val end = selection.end ?: return null
        return LocatorRange(start = start, end = end, extras = selection.extras)
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\provider\TxtTextProvider.kt

```kotlin
@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtTextProvider(
    private val store: Utf16TextStore,
    private val ioDispatcher: CoroutineDispatcher
) : TextProvider {

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        return withContext(ioDispatcher) {
            try {
                val start = parseOffset(range.start)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Invalid TXT range start: ${range.start}")
                    )
                val end = parseOffset(range.end)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Invalid TXT range end: ${range.end}")
                    )
                val minOffset = min(start, end).coerceIn(0L, store.lengthChars)
                val maxOffset = maxOf(start, end).coerceIn(0L, store.lengthChars)
                val length = (maxOffset - minOffset).toInt().coerceAtLeast(0)
                val capped = length.coerceAtMost(MAX_EXTRACT_CHARS)
                ReaderResult.Ok(store.readString(minOffset, capped))
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
            }
        }
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        return withContext(ioDispatcher) {
            try {
                val offset = parseOffset(locator)
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("Invalid TXT locator: $locator")
                    )
                val half = (maxChars / 2).coerceAtLeast(16)
                ReaderResult.Ok(store.readAround(offset, before = half, after = half))
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
            }
        }
    }

    private fun parseOffset(locator: Locator): Long? {
        return TxtBlockLocatorCodec.parseOffset(locator, store.lengthChars)
    }

    private companion object {
        private const val MAX_EXTRACT_CHARS = 200_000
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\render\TxtController.kt

```kotlin
@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions"
)

package com.ireader.engines.txt.internal.render

import android.util.Log
import com.ireader.engines.common.android.controller.BaseCoroutineReaderController
import com.ireader.engines.common.android.reflow.ReflowPageSlice
import com.ireader.engines.common.android.reflow.ReflowPageSliceCache
import com.ireader.engines.common.android.reflow.ReflowPaginationIndexStore
import com.ireader.engines.common.android.reflow.ReflowPaginator
import com.ireader.engines.common.android.reflow.SOFT_BREAK_PROFILE_EXTRA_KEY
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.common.cache.LruCache
import com.ireader.engines.txt.internal.link.LinkDetector
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.provider.ChapterDetector
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndexBuilder
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.error.getOrNull
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderMetrics
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.Progression
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

private fun initialRenderState(
    initialOffset: Long,
    maxOffset: Long,
    config: RenderConfig.ReflowText
): RenderState {
    val safeMax = maxOffset.coerceAtLeast(0L)
    val start = initialOffset.coerceIn(0L, safeMax)
    val percent = if (safeMax == 0L) {
        0.0
    } else {
        start.toDouble() / safeMax.toDouble()
    }.coerceIn(0.0, 1.0)

    return RenderState(
        locator = TxtBlockLocatorCodec.locatorForOffset(
            offset = start,
            maxOffset = safeMax,
            extras = mapOf(LocatorExtraKeys.PROGRESSION to String.format(Locale.US, "%.6f", percent))
        ),
        progression = Progression(
            percent = percent,
            label = "${(percent * 100.0).roundToInt()}%"
        ),
        nav = NavigationAvailability(
            canGoPrev = start > 0L,
            canGoNext = start < safeMax
        ),
        config = config
    )
}

internal class TxtController(
    private val documentKey: String,
    private val store: Utf16TextStore,
    private val meta: TxtMeta,
    private val initialLocator: Locator?,
    initialOffset: Long,
    initialConfig: RenderConfig.ReflowText,
    maxPageCache: Int,
    persistPagination: Boolean,
    private val files: TxtBookFiles,
    private val annotationProvider: AnnotationProvider?,
    private val ioDispatcher: CoroutineDispatcher,
    defaultDispatcher: CoroutineDispatcher
) : BaseCoroutineReaderController(
    initialState = initialRenderState(
        initialOffset = initialOffset,
        maxOffset = store.lengthChars,
        config = initialConfig
    ),
    dispatcher = defaultDispatcher
) {

    private val softBreakEnabled: Boolean = meta.hardWrapLikely
    private var softBreakProfile: SoftBreakTuningProfile = resolveSoftBreakProfile(initialConfig)
    private var softBreakIndex: SoftBreakIndex? = if (softBreakEnabled) {
        openSoftBreakIndex(softBreakProfile)
    } else {
        null
    }
    private val paginator = ReflowPaginator(
        source = TxtTextSource(store),
        hardWrapLikely = meta.hardWrapLikely,
        softBreakIndex = softBreakIndex,
        pageEndAdjuster = TxtPageEndAdjuster(ChapterDetector())
    )
    private val sliceCache = ReflowPageSliceCache(
        paginator = paginator,
        maxPageCache = maxPageCache,
        maxOffsetProvider = { store.lengthChars }
    )
    private val paginationIndex = ReflowPaginationIndexStore(
        enabled = persistPagination,
        documentKey = documentKey,
        paginationDir = files.paginationDir
    )
    private val pageExtrasCache = LruCache<PageExtrasCacheKey, PageExtras>((maxPageCache * 3).coerceAtLeast(8))

    private var pageCompletionJob: Job? = null
    private var annotationObserverJob: Job? = null
    private var annotationRevision: Long = 0L
    private var restoredLocatorAnchors = false

    private val initialStart = initialOffset.coerceIn(0L, store.lengthChars)
    private val navigation = TxtNavigationState(initialStart)

    private var constraints: LayoutConstraints? = null
    private var currentConfig: RenderConfig.ReflowText = initialConfig

    init {
        if (!softBreakEnabled) {
            logInfo(
                TAG,
                "soft-break disabled for non-hard-wrap document; using raw newline rendering"
            )
        } else if (softBreakIndex == null) {
            logInfo(
                TAG,
                "soft-break index unavailable at open; using runtime classifier until build completes " +
                    "profile=${softBreakProfile.storageValue} hardWrapLikely=${meta.hardWrapLikely}"
            )
            buildSoftBreakIndexAsync(softBreakProfile)
        } else {
            logInfo(
                TAG,
                "soft-break index loaded from cache profile=${softBreakProfile.storageValue} " +
                    "lengthChars=${store.lengthChars}"
            )
        }
        observeAnnotationChangesIfNeeded()
    }

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun unbindSurface(): ReaderResult<Unit> {
        return ReaderResult.Ok(Unit)
    }

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return mutex.withLock {
            this.constraints = constraints
            sliceCache.clear()
            pageExtrasCache.clear()
            reloadPaginationIndexIfNeededLocked()
            updateStateLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val reflow = config as? RenderConfig.ReflowText
            ?: return ReaderResult.Err(ReaderError.Internal("TXT requires ReflowText config"))
        val sanitized = reflow.sanitized()
        return mutex.withLock {
            currentConfig = sanitized
            stateMutable.value = stateMutable.value.copy(config = sanitized)
            val newProfile = resolveSoftBreakProfile(sanitized)
            val profileChanged = newProfile != softBreakProfile
            if (softBreakEnabled && profileChanged) {
                softBreakProfile = newProfile
                runCatching { softBreakIndex?.close() }
                softBreakIndex = openSoftBreakIndex(newProfile)
                logInfo(
                    TAG,
                    "soft-break profile switched to=${newProfile.storageValue} hasIndex=${softBreakIndex != null}"
                )
                paginator.setSoftBreakIndex(softBreakIndex)
                if (softBreakIndex == null) {
                    buildSoftBreakIndexAsync(newProfile)
                }
            }
            sliceCache.clear()
            pageExtrasCache.clear()
            reloadPaginationIndexIfNeededLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        val renderResult = mutex.withLock {
            renderLocked(policy)
        }
        if (renderResult is ReaderResult.Ok && policy.prefetchNeighbors > 0) {
            launchSafely("prefetch-neighbors") { prefetchNeighbors(policy.prefetchNeighbors) }
        }
        return renderResult
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val constraintsLocal = constraints
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
            val current = sliceCache.getOrBuild(
                start = navigation.currentStart,
                constraints = constraintsLocal,
                config = currentConfig,
                allowCache = true
            )
            if (current.endOffset >= store.lengthChars) {
                return@withLock buildPageResultLocked(
                    slice = current,
                    renderTimeMs = 0L,
                    cacheHit = true
                )
            }
            navigation.moveTo(current.endOffset, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            val constraintsLocal = constraints
                ?: return@withLock ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
            if (!navigation.canGoPrev()) {
                return@withLock renderLocked(policy)
            }
            val target = navigation.findPreviousStart(
                fromStart = navigation.currentStart,
                maxOffset = store.lengthChars,
                constraints = constraintsLocal
            ) { start, constraintsArg ->
                sliceCache.getOrBuild(
                    start = start,
                    constraints = constraintsArg,
                    config = currentConfig,
                    allowCache = true
                )
            }
            navigation.moveTo(target, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        val offset = TxtBlockLocatorCodec.parseOffset(locator, store.lengthChars)
            ?: return ReaderResult.Err(
                ReaderError.Internal("Unsupported TXT locator: ${locator.scheme}:${locator.value}")
            )
        return mutex.withLock {
            navigation.moveTo(offset, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        val clamped = percent.coerceIn(0.0, 1.0)
        return mutex.withLock {
            val target = paginationIndex.startForProgress(clamped)
                ?: (store.lengthChars * clamped).toLong()
            navigation.moveTo(target, store.lengthChars)
            renderLocked(policy)
        }
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        if (count <= 0) {
            return ReaderResult.Ok(Unit)
        }
        return mutex.withLock {
            val constraintsLocal = constraints ?: return@withLock ReaderResult.Ok(Unit)
            val currentSlice = sliceCache.getOrBuild(
                start = navigation.currentStart,
                constraints = constraintsLocal,
                config = currentConfig,
                allowCache = true
            )

            var forwardStart = currentSlice.endOffset
            repeat(count) {
                if (forwardStart >= store.lengthChars) return@repeat
                val next = sliceCache.getOrBuild(
                    start = forwardStart,
                    constraints = constraintsLocal,
                    config = currentConfig,
                    allowCache = true
                )
                if (next.endOffset <= forwardStart) {
                    return@repeat
                }
                forwardStart = next.endOffset
            }

            var backwardStart = currentSlice.startOffset
            repeat(count) {
                if (backwardStart <= 0L) return@repeat
                val prevStart = navigation.findPreviousStart(
                    fromStart = backwardStart,
                    maxOffset = store.lengthChars,
                    constraints = constraintsLocal
                ) { start, constraintsArg ->
                    sliceCache.getOrBuild(
                        start = start,
                        constraints = constraintsArg,
                        config = currentConfig,
                        allowCache = true
                    )
                }
                if (prevStart >= backwardStart) {
                    return@repeat
                }
                sliceCache.getOrBuild(
                    start = prevStart,
                    constraints = constraintsLocal,
                    config = currentConfig,
                    allowCache = true
                )
                backwardStart = prevStart
            }
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            sliceCache.clear()
            pageExtrasCache.clear()
            if (reason == InvalidateReason.CONFIG_CHANGED || reason == InvalidateReason.LAYOUT_CHANGED) {
                paginationIndex.invalidateProfile()
                pageCompletionJob?.cancel()
            }
            ReaderResult.Ok(Unit)
        }
    }

    override fun onClose() {
        runCatching { paginationIndex.saveIfDirty() }
            .onFailure { logWarn(TAG, "TXT controller failed to save pagination index", it) }
        pageCompletionJob?.cancel()
        annotationObserverJob?.cancel()
        runCatching { softBreakIndex?.close() }
            .onFailure { logWarn(TAG, "TXT controller failed to close soft-break index", it) }
    }

    private fun resolveSoftBreakProfile(config: RenderConfig.ReflowText): SoftBreakTuningProfile {
        return SoftBreakTuningProfile.fromStorageValue(config.extra[SOFT_BREAK_PROFILE_EXTRA_KEY])
    }

    private fun openSoftBreakIndex(profile: SoftBreakTuningProfile): SoftBreakIndex? {
        val ruleConfig = SoftBreakRuleConfig.forProfile(profile)
        return SoftBreakIndex.openIfValid(
            file = files.softBreakIdx,
            meta = meta,
            profile = profile,
            rulesVersion = ruleConfig.rulesVersion
        )
    }

    private fun buildSoftBreakIndexAsync(profile: SoftBreakTuningProfile) {
        if (!softBreakEnabled) {
            return
        }
        launchSafely("soft-break-index") {
            SoftBreakIndexBuilder.buildIfNeeded(
                files = files,
                meta = meta,
                ioDispatcher = ioDispatcher,
                profile = profile
            )
            val loaded = openSoftBreakIndex(profile)
            mutex.withLock {
                if (profile != softBreakProfile) {
                    runCatching { loaded?.close() }
                    return@withLock
                }
                softBreakIndex?.close()
                softBreakIndex = loaded
                if (loaded == null) {
                    logWarn(
                        TAG,
                        "soft-break index build completed but index still unavailable " +
                            "profile=${profile.storageValue}",
                        null
                    )
                } else {
                    logInfo(
                        TAG,
                        "soft-break index activated profile=${profile.storageValue} " +
                            "lengthChars=${loaded.lengthChars} newlineCount=${loaded.newlineCount} " +
                            "rulesVersion=${loaded.rulesVersion}; clearing page caches"
                    )
                }
                paginator.setSoftBreakIndex(loaded)
                sliceCache.clear()
                pageExtrasCache.clear()
            }
        }
    }

    private suspend fun renderLocked(policy: RenderPolicy): ReaderResult<RenderPage> {
        val constraintsLocal = constraints
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        var builtSlice: ReflowPageSlice? = null
        var cacheHit = false
        val elapsed = measureTimeMillis {
            if (policy.allowCache) {
                builtSlice = sliceCache.getCached(navigation.currentStart)
                if (builtSlice != null) {
                    cacheHit = true
                }
            }
            if (builtSlice == null) {
                builtSlice = sliceCache.getOrBuild(
                    start = navigation.currentStart,
                    constraints = constraintsLocal,
                    config = currentConfig,
                    allowCache = false
                )
            }
        }
        val slice = builtSlice ?: return ReaderResult.Err(ReaderError.Internal("Failed to paginate TXT page"))

        val page = buildPageResultLocked(
            slice = slice,
            renderTimeMs = elapsed,
            cacheHit = cacheHit
        )
        if (page is ReaderResult.Ok) {
            paginationIndex.record(slice.startOffset)
            maybeSchedulePageCompletionLocked()
        }
        return page
    }

    private suspend fun buildPageResultLocked(
        slice: ReflowPageSlice,
        renderTimeMs: Long,
        cacheHit: Boolean
    ): ReaderResult<RenderPage> {
        navigation.updateFromSlice(slice)
        updateStateLocked()
        val pageRange = TxtBlockLocatorCodec.rangeForOffsets(
            startOffset = slice.startOffset,
            endOffset = slice.endOffset,
            maxOffset = store.lengthChars
        )
        val extras = pageExtrasFor(
            startOffset = slice.startOffset,
            endOffset = slice.endOffset,
            text = slice.text,
            range = pageRange
        )

        val page = RenderPage(
            id = PageId("${slice.startOffset}-${slice.endOffset}"),
            locator = navigation.locatorFor(store.lengthChars),
            content = RenderContent.Text(
                text = slice.text,
                mapping = TxtTextMapping(slice.startOffset, slice.endOffset)
            ),
            links = extras.links,
            decorations = extras.decorations,
            metrics = RenderMetrics(
                renderTimeMs = renderTimeMs,
                cacheHit = cacheHit
            )
        )
        eventsMutable.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        eventsMutable.tryEmit(ReaderEvent.PageChanged(page.locator))
        return ReaderResult.Ok(page)
    }

    private suspend fun pageExtrasFor(
        startOffset: Long,
        endOffset: Long,
        text: CharSequence,
        range: LocatorRange
    ): PageExtras {
        val key = PageExtrasCacheKey(
            startOffset = startOffset,
            endOffset = endOffset,
            annotationRevision = annotationRevision
        )
        val cached = pageExtrasCache[key]
        if (cached != null) {
            return cached
        }
        val computed = PageExtras(
            links = LinkDetector.detect(
                text = text,
                pageStartOffset = startOffset,
                maxOffset = store.lengthChars
            ),
            decorations = annotationProvider
                ?.decorationsFor(AnnotationQuery(range = range))
                ?.getOrNull()
                ?: emptyList()
        )
        pageExtrasCache[key] = computed
        return computed
    }

    private fun updateStateLocked() {
        val locatorExtras = paginationIndex.locatorExtras()
        stateMutable.value = stateMutable.value.copy(
            locator = navigation.locatorFor(store.lengthChars, extras = locatorExtras),
            progression = navigation.progressionFor(store.lengthChars),
            nav = NavigationAvailability(
                canGoPrev = navigation.canGoPrev(),
                canGoNext = navigation.canGoNext(store.lengthChars)
            ),
            config = currentConfig
        )
    }

    private fun reloadPaginationIndexIfNeededLocked() {
        paginationIndex.reloadIfNeeded(
            constraints = constraints,
            profileConfig = currentConfig
        )
        if (!restoredLocatorAnchors) {
            paginationIndex.mergeLocatorAnchors(initialLocator?.extras.orEmpty())
            restoredLocatorAnchors = true
        }
        pageCompletionJob?.cancel()
    }

    private fun maybeSchedulePageCompletionLocked() {
        if (!paginationIndex.hasActiveProfile()) {
            return
        }
        if (pageCompletionJob?.isActive == true) {
            return
        }
        val constraintsLocal = constraints ?: return
        pageCompletionJob = launchSafely("page-map-completion") {
            completePageMapForward(
                constraints = constraintsLocal,
                maxPages = 24,
                batchSize = PAGE_COMPLETION_BATCH_SIZE
            )
        }
    }

    private suspend fun completePageMapForward(
        constraints: LayoutConstraints,
        maxPages: Int,
        batchSize: Int
    ) {
        if (maxPages <= 0 || store.lengthChars <= 0L) {
            return
        }
        var remaining = maxPages
        var cursor = mutex.withLock {
            (paginationIndex.lastKnownStart() ?: navigation.currentStart).coerceIn(0L, store.lengthChars)
        }
        while (remaining > 0 && cursor < store.lengthChars) {
            val batchResult = mutex.withLock {
                completePageMapBatchLocked(
                    constraints = constraints,
                    startCursor = cursor,
                    maxPages = minOf(batchSize, remaining)
                )
            }
            cursor = batchResult.nextCursor
            if (batchResult.steps <= 0) {
                break
            }
            remaining -= batchResult.steps
        }
        mutex.withLock {
            paginationIndex.saveIfDirty()
        }
    }

    private suspend fun completePageMapBatchLocked(
        constraints: LayoutConstraints,
        startCursor: Long,
        maxPages: Int
    ): PageCompletionBatchResult {
        if (maxPages <= 0 || startCursor >= store.lengthChars) {
            return PageCompletionBatchResult(nextCursor = startCursor, steps = 0)
        }
        var cursor = startCursor.coerceIn(0L, store.lengthChars)
        var steps = 0
        while (cursor < store.lengthChars && steps < maxPages) {
            val slice = sliceCache.getOrBuild(
                start = cursor,
                constraints = constraints,
                config = currentConfig,
                allowCache = true
            )
            paginationIndex.record(slice.startOffset)
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            steps++
        }
        return PageCompletionBatchResult(nextCursor = cursor, steps = steps)
    }

    private fun observeAnnotationChangesIfNeeded() {
        val provider = annotationProvider ?: return
        annotationObserverJob = launchSafely("observe-annotations") {
            provider.observeAll().collect {
                mutex.withLock {
                    annotationRevision++
                    pageExtrasCache.clear()
                }
            }
        }
    }

    override fun onCoroutineError(name: String, throwable: Throwable) {
        logWarn(TAG, "TXT controller background task failed: $name", throwable)
    }

    private data class PageCompletionBatchResult(
        val nextCursor: Long,
        val steps: Int
    )

    private data class PageExtrasCacheKey(
        val startOffset: Long,
        val endOffset: Long,
        val annotationRevision: Long
    )

    private data class PageExtras(
        val links: List<DocumentLink>,
        val decorations: List<Decoration>
    )

    private companion object {
        private const val TAG = "TxtController"
        private const val PAGE_COMPLETION_BATCH_SIZE = 4

        private fun logInfo(tag: String, message: String) {
            runCatching { Log.i(tag, message) }
        }

        private fun logWarn(tag: String, message: String, throwable: Throwable?) {
            runCatching {
                if (throwable == null) {
                    Log.w(tag, message)
                } else {
                    Log.w(tag, message, throwable)
                }
            }
        }
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\render\TxtNavigationState.kt

```kotlin
package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.common.android.reflow.ReflowPageSlice
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.Progression
import java.util.Locale
import kotlin.math.roundToInt

internal class TxtNavigationState(
    initialStart: Long
) {
    var currentStart: Long = initialStart
        private set
    var currentEnd: Long = initialStart
        private set
    private var avgCharsPerPage: Int = 1800

    fun moveTo(start: Long, maxOffset: Long) {
        currentStart = start.coerceIn(0L, maxOffset)
    }

    fun updateFromSlice(slice: ReflowPageSlice) {
        currentStart = slice.startOffset
        currentEnd = slice.endOffset
        val consumed = (slice.endOffset - slice.startOffset).toInt().coerceAtLeast(1)
        avgCharsPerPage = ((avgCharsPerPage * 3) + consumed) / 4
    }

    fun canGoPrev(): Boolean = currentStart > 0L

    fun canGoNext(maxOffset: Long): Boolean = currentEnd < maxOffset

    fun progressionFor(maxOffset: Long): Progression {
        val percent = if (maxOffset == 0L) {
            0.0
        } else {
            currentStart.toDouble() / maxOffset.toDouble()
        }.coerceIn(0.0, 1.0)
        return Progression(
            percent = percent,
            label = "${(percent * 100.0).roundToInt()}%"
        )
    }

    fun locatorFor(maxOffset: Long, extras: Map<String, String> = emptyMap()): Locator {
        val percent = if (maxOffset == 0L) {
            0.0
        } else {
            currentStart.toDouble() / maxOffset.toDouble()
        }.coerceIn(0.0, 1.0)
        val mergedExtras = extras + mapOf(
            LocatorExtraKeys.PROGRESSION to String.format(Locale.US, "%.6f", percent)
        )
        return TxtBlockLocatorCodec.locatorForOffset(
            offset = currentStart.coerceIn(0L, maxOffset),
            maxOffset = maxOffset,
            extras = mergedExtras
        )
    }

    suspend fun findPreviousStart(
        fromStart: Long,
        maxOffset: Long,
        constraints: LayoutConstraints,
        resolveSlice: suspend (Long, LayoutConstraints) -> ReflowPageSlice
    ): Long {
        if (fromStart <= 0L) {
            return 0L
        }
        val estimateDistance = (avgCharsPerPage * 2L).coerceAtLeast(1_200L)
        var cursor = (fromStart - estimateDistance).coerceAtLeast(0L)
        var previousStart = 0L
        var safety = 0

        while (cursor < fromStart && safety < 256) {
            val slice = resolveSlice(cursor, constraints)
            if (slice.endOffset >= fromStart) {
                return previousStart.coerceAtMost(fromStart)
            }
            previousStart = slice.startOffset
            if (slice.endOffset <= cursor) {
                break
            }
            cursor = slice.endOffset
            safety++
        }
        return cursor.coerceAtMost(fromStart).coerceAtLeast(0L).coerceAtMost(maxOffset)
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\render\TxtPageEndAdjuster.kt

```kotlin
package com.ireader.engines.txt.internal.render

import android.util.Log
import com.ireader.engines.common.android.reflow.ReflowPageEndAdjuster
import com.ireader.engines.txt.internal.provider.ChapterDetector

internal class TxtPageEndAdjuster(
    private val detector: ChapterDetector = ChapterDetector()
) : ReflowPageEndAdjuster {

    private val strongEndPunctuation = setOf('。', '！', '？', '.', '!', '?', '…', ';', '；', ':', '：')

    override fun adjust(
        raw: String,
        measuredEnd: Int,
        rawLength: Int,
        pageStartOffset: Long
    ): Int {
        val safeMeasuredEnd = measuredEnd.coerceIn(0, rawLength)
        if (safeMeasuredEnd <= 0) {
            return safeMeasuredEnd
        }
        val decision = findBoundaryLineStart(raw, safeMeasuredEnd)
            ?: return safeMeasuredEnd
        val adjusted = decision.chapterStart.coerceAtLeast(1)
        if (isDebugLoggingEnabled()) {
            val rewindChars = safeMeasuredEnd - adjusted
            logDebug(
                TAG,
                "chapter-rewind start=$adjusted measuredEnd=$safeMeasuredEnd rewindChars=$rewindChars " +
                    "pageStartOffset=$pageStartOffset title='${decision.titlePreview}' " +
                    "prelude='${decision.preludePreview}'"
            )
        }
        return adjusted
    }

    private fun findBoundaryLineStart(raw: String, measuredEnd: Int): ChapterBoundaryDecision? {
        var lineStart = 0
        var candidate: ChapterBoundaryDecision? = null
        while (lineStart < measuredEnd) {
            val lineEnd = raw.indexOf('\n', lineStart)
                .takeIf { it >= 0 && it < measuredEnd }
                ?: measuredEnd
            val line = raw.substring(lineStart, lineEnd).trim()
            val prelude = previousLine(raw, lineStart).trim()
            if (
                lineStart > 0 &&
                detector.isChapterBoundaryTitle(line) &&
                hasStrongPrelude(raw, lineStart, prelude)
            ) {
                val rewindChars = measuredEnd - lineStart
                if (rewindChars in 1..MAX_CHAPTER_REWIND_CHARS) {
                    candidate = ChapterBoundaryDecision(
                        chapterStart = lineStart,
                        titlePreview = line.take(MAX_DEBUG_PREVIEW),
                        preludePreview = prelude.take(MAX_DEBUG_PREVIEW)
                    )
                }
            }
            if (lineEnd >= measuredEnd) {
                break
            }
            lineStart = lineEnd + 1
        }
        return candidate
    }

    private fun hasStrongPrelude(raw: String, lineStart: Int, previousLine: String): Boolean {
        val beforeNewline = lineStart - 1
        if (beforeNewline !in raw.indices || raw[beforeNewline] != '\n') {
            return false
        }
        if (previousLine.isEmpty()) {
            return true
        }
        val last = previousLine.lastOrNull() ?: return false
        return strongEndPunctuation.contains(last)
    }

    private fun previousLine(raw: String, lineStart: Int): String {
        val beforeNewline = lineStart - 1
        if (beforeNewline !in raw.indices || raw[beforeNewline] != '\n') {
            return ""
        }
        val previousBreak = raw.lastIndexOf('\n', startIndex = beforeNewline - 1)
        val previousStart = previousBreak + 1
        return raw.substring(previousStart, beforeNewline)
    }

    private data class ChapterBoundaryDecision(
        val chapterStart: Int,
        val titlePreview: String,
        val preludePreview: String
    )

    private companion object {
        private const val TAG = "TxtPageEndAdjuster"
        private const val MAX_DEBUG_PREVIEW = 48
        private const val MAX_CHAPTER_REWIND_CHARS = 240

        private fun isDebugLoggingEnabled(): Boolean {
            return runCatching { Log.isLoggable(TAG, Log.DEBUG) }
                .getOrDefault(false)
        }

        private fun logDebug(tag: String, message: String) {
            runCatching { Log.d(tag, message) }
        }
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\render\TxtTextMapping.kt

```kotlin
@file:Suppress("ReturnCount")

package com.ireader.engines.txt.internal.render

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.reader.api.render.TextMapping
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange

internal class TxtTextMapping(
    private val pageStart: Long,
    private val pageEnd: Long
) : TextMapping {

    override fun locatorAt(charIndex: Int): Locator {
        val clamped = charIndex.toLong().coerceAtLeast(0L)
        val global = (pageStart + clamped).coerceAtMost(pageEnd)
        return TxtBlockLocatorCodec.locatorForOffset(global, pageEnd)
    }

    override fun rangeFor(startChar: Int, endChar: Int): LocatorRange {
        val localStart = startChar.coerceAtLeast(0)
        val localEnd = endChar.coerceAtLeast(0)
        val minLocal = minOf(localStart, localEnd)
        val maxLocal = maxOf(localStart, localEnd)
        val startGlobal = (pageStart + minLocal.toLong()).coerceAtMost(pageEnd)
        val endGlobal = (pageStart + maxLocal.toLong()).coerceAtMost(pageEnd)
        return TxtBlockLocatorCodec.rangeForOffsets(
            startOffset = startGlobal,
            endOffset = endGlobal,
            maxOffset = pageEnd
        )
    }

    override fun charRangeFor(range: LocatorRange): IntRange? {
        val startGlobal = TxtBlockLocatorCodec.parseOffset(range.start) ?: return null
        val endGlobal = TxtBlockLocatorCodec.parseOffset(range.end) ?: return null
        val minGlobal = minOf(startGlobal, endGlobal)
        val maxGlobal = maxOf(startGlobal, endGlobal)
        if (minGlobal < pageStart || maxGlobal > pageEnd) {
            return null
        }
        val localStart = (minGlobal - pageStart).toInt()
        val localEndExclusive = (maxGlobal - pageStart).toInt()
        return localStart until localEndExclusive
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\render\TxtTextSource.kt

```kotlin
package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.ReflowTextSource
import com.ireader.engines.txt.internal.store.Utf16TextStore

internal class TxtTextSource(
    private val store: Utf16TextStore
) : ReflowTextSource {
    override val lengthChars: Long
        get() = store.lengthChars

    override fun readString(start: Long, count: Int): String {
        return store.readString(start, count)
    }
}

```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\search\TrigramBloomIndex.kt

```kotlin
package com.ireader.engines.txt.internal.search

import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.readStringUtf8
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.io.writeStringUtf8
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class TrigramBloomIndex private constructor(
    val blockChars: Int,
    private val bitsetBits: Int,
    val lengthChars: Long,
    val sampleHash: String,
    private val blocksCount: Int,
    private val dataOffset: Long
) {

    data class BlockRange(
        val start: Long,
        val endExclusive: Long
    )

    companion object {
        private const val MAGIC = "TBI1"
        private const val VERSION = 1
        private const val BLOCK_CHARS = 32 * 1024
        private const val BITSET_BITS = 16 * 1024
        private const val MIN_CHARS_FOR_INDEX = 1_000_000L

        fun openIfValid(file: File, meta: TxtMeta): TrigramBloomIndex? {
            if (!file.exists()) {
                return null
            }
            return runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    val magic = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.US_ASCII)
                    if (magic != MAGIC) {
                        return null
                    }
                    val version = raf.readInt()
                    if (version != VERSION) {
                        return null
                    }
                    val blockChars = raf.readInt()
                    val bitsetBits = raf.readInt()
                    val lengthChars = raf.readLong()
                    val sampleHash = raf.readStringUtf8()
                    val blocksCount = raf.readInt()
                    val dataOffset = raf.filePointer

                    if (lengthChars != meta.lengthChars || sampleHash != meta.sampleHash) {
                        return null
                    }
                    TrigramBloomIndex(
                        blockChars = blockChars,
                        bitsetBits = bitsetBits,
                        lengthChars = lengthChars,
                        sampleHash = sampleHash,
                        blocksCount = blocksCount,
                        dataOffset = dataOffset
                    )
                }
            }.getOrNull()
        }

        suspend fun buildIfNeeded(
            file: File,
            lockFile: File,
            store: Utf16TextStore,
            meta: TxtMeta,
            ioDispatcher: CoroutineDispatcher
        ) = withContext(ioDispatcher) {
            if (meta.lengthChars < MIN_CHARS_FOR_INDEX) {
                return@withContext
            }
            openIfValid(file, meta)?.also { return@withContext }
            lockFile.parentFile?.mkdirs()
            RandomAccessFile(lockFile, "rw").channel.use { lockChannel ->
                lockChannel.lock().use {
                    openIfValid(file, meta)?.also { return@withContext }

                    val blocksCount = ceil(meta.lengthChars.toDouble() / BLOCK_CHARS.toDouble()).toInt()
                        .coerceAtLeast(1)
                    val bitsetBytes = BITSET_BITS / 8

                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    prepareTempFile(tmp)

                    RandomAccessFile(tmp, "rw").use { raf ->
                        raf.setLength(0L)
                        raf.write(MAGIC.toByteArray(Charsets.US_ASCII))
                        raf.writeInt(VERSION)
                        raf.writeInt(BLOCK_CHARS)
                        raf.writeInt(BITSET_BITS)
                        raf.writeLong(meta.lengthChars)
                        raf.writeStringUtf8(meta.sampleHash)
                        raf.writeInt(blocksCount)

                        for (bi in 0 until blocksCount) {
                            coroutineContext.ensureActive()
                            val rangeStart = bi.toLong() * BLOCK_CHARS.toLong()
                            val len = min(BLOCK_CHARS.toLong(), meta.lengthChars - rangeStart)
                                .toInt()
                                .coerceAtLeast(0)
                            val bitset = ByteArray(bitsetBytes)
                            if (len >= 3) {
                                val text = store.readChars(rangeStart, len)
                                fillBitset(text, bitset, BITSET_BITS)
                            }
                            raf.write(bitset)
                        }
                    }

                    replaceFileAtomically(tempFile = tmp, targetFile = file)
                }
            }
        }

        private fun fillBitset(text: CharArray, bitset: ByteArray, bitsetBits: Int) {
            if (text.size < 3) {
                return
            }
            var i = 0
            while (i <= text.size - 3) {
                val h = trigramHash(
                    text[i].lowercaseChar(),
                    text[i + 1].lowercaseChar(),
                    text[i + 2].lowercaseChar()
                )
                val (h1, h2) = splitHashes(h)
                setBit(bitset, h1, bitsetBits)
                setBit(bitset, h2, bitsetBits)
                i++
            }
        }

        private fun trigramHash(a: Char, b: Char, c: Char): Int {
            var h = a.code * 31 + b.code
            h = h * 31 + c.code
            h = h xor (h ushr 16)
            h *= -0x7a143595
            h = h xor (h ushr 13)
            return h
        }

        private fun splitHashes(hash: Int): Pair<Int, Int> {
            val h1 = hash
            val h2 = hash xor (hash ushr 7) xor (hash shl 9)
            return h1 to h2
        }

        private fun normalizeHash(hash: Int, mod: Int): Int {
            val value = hash and Int.MAX_VALUE
            return value % mod
        }

        private fun setBit(bitset: ByteArray, hash: Int, bitsetBits: Int) {
            val bit = normalizeHash(hash, bitsetBits)
            val byteIdx = bit ushr 3
            val mask = 1 shl (bit and 7)
            bitset[byteIdx] = (bitset[byteIdx].toInt() or mask).toByte()
        }
    }

    fun blocksCount(): Int = blocksCount

    fun buildQueryTrigramHashes(query: String): IntArray {
        if (query.length < 3) {
            return intArrayOf()
        }
        val chars = query.toCharArray()
        val out = LinkedHashSet<Int>(chars.size * 2)
        var i = 0
        while (i <= chars.size - 3) {
            val h = trigramHash(
                chars[i].lowercaseChar(),
                chars[i + 1].lowercaseChar(),
                chars[i + 2].lowercaseChar()
            )
            val (h1, h2) = splitHashes(h)
            out.add(h1)
            out.add(h2)
            i++
        }
        return out.toIntArray()
    }

    fun mayContainAll(raf: RandomAccessFile, blockIndex: Int, hashes: IntArray): Boolean {
        if (hashes.isEmpty()) {
            return true
        }
        if (blockIndex < 0 || blockIndex >= blocksCount) {
            return false
        }
        val bitsetBytes = bitsetBits / 8
        val bitset = ByteArray(bitsetBytes)
        raf.seek(dataOffset + blockIndex.toLong() * bitsetBytes.toLong())
        raf.readFully(bitset)
        for (hash in hashes) {
            val bit = normalizeHash(hash, bitsetBits)
            val byteIdx = bit ushr 3
            val mask = 1 shl (bit and 7)
            if ((bitset[byteIdx].toInt() and mask) == 0) {
                return false
            }
        }
        return true
    }

    fun blockRange(blockIndex: Int): BlockRange {
        val start = blockIndex.toLong() * blockChars.toLong()
        val endExclusive = min(lengthChars, start + blockChars.toLong())
        return BlockRange(start = start, endExclusive = endExclusive)
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\softbreak\SoftBreakIndex.kt

```kotlin
package com.ireader.engines.txt.internal.softbreak

import com.ireader.engines.common.android.reflow.ReflowSoftBreakIndex
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.common.io.readStringUtf8
import com.ireader.engines.common.io.readVarLongOrNull
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max

internal class SoftBreakIndex private constructor(
    private val raf: RandomAccessFile,
    val version: Int,
    private val blockNewlines: Int,
    val lengthChars: Long,
    val newlineCount: Long,
    val sampleHash: String,
    val profile: SoftBreakTuningProfile,
    val rulesVersion: Int,
    private val blocks: List<BlockMeta>
) : Closeable, ReflowSoftBreakIndex {

    internal data class BlockMeta(
        val filePos: Long,
        val firstOffset: Long,
        val lastOffset: Long,
        val count: Int
    )

    companion object {
        private const val MAGIC = "SBX1"
        private const val VERSION = 6

        fun openIfValid(
            file: File,
            meta: TxtMeta,
            profile: SoftBreakTuningProfile,
            rulesVersion: Int
        ): SoftBreakIndex? {
            if (!file.exists()) {
                return null
            }
            val raf = RandomAccessFile(file, "r")
            return try {
                val magic = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.US_ASCII)
                if (magic != MAGIC) {
                    raf.close()
                    return null
                }
                val version = raf.readInt()
                if (version != VERSION) {
                    raf.close()
                    return null
                }
                val blockNewlines = raf.readInt()
                val lengthChars = raf.readLong()
                val newlineCount = raf.readLong()
                val sampleHash = raf.readStringUtf8()
                val profileRaw = raf.readStringUtf8()
                val fileProfile = SoftBreakTuningProfile.fromStorageValue(profileRaw)
                val fileRulesVersion = raf.readInt()
                val indexOffset = raf.readLong()

                if (lengthChars != meta.lengthChars || sampleHash != meta.sampleHash) {
                    raf.close()
                    return null
                }
                if (fileProfile != profile || fileRulesVersion != rulesVersion) {
                    raf.close()
                    return null
                }

                raf.seek(indexOffset)
                val count = raf.readInt()
                val blocks = ArrayList<BlockMeta>(count)
                for (i in 0 until count) {
                    val filePos = raf.readLong()
                    val first = raf.readLong()
                    val last = raf.readLong()
                    val c = raf.readInt()
                    blocks.add(
                        BlockMeta(
                            filePos = filePos,
                            firstOffset = first,
                            lastOffset = last,
                            count = c
                        )
                    )
                }

                SoftBreakIndex(
                    raf = raf,
                    version = version,
                    blockNewlines = blockNewlines,
                    lengthChars = lengthChars,
                    newlineCount = newlineCount,
                    sampleHash = sampleHash,
                    profile = fileProfile,
                    rulesVersion = fileRulesVersion,
                    blocks = blocks
                )
            } catch (_: Throwable) {
                runCatching { raf.close() }
                null
            }
        }
    }

    override fun forEachNewlineInRange(
        startChar: Long,
        endChar: Long,
        consumer: (offset: Long, isSoft: Boolean) -> Unit
    ) {
        if (blocks.isEmpty()) {
            return
        }
        val start = startChar.coerceAtLeast(0L)
        val end = endChar.coerceAtMost(lengthChars).coerceAtLeast(start)
        if (start >= end) {
            return
        }
        val first = findFirstBlockByLastOffset(start)
        if (first >= blocks.size) {
            return
        }

        for (index in first until blocks.size) {
            val block = blocks[index]
            if (block.firstOffset >= end) {
                break
            }
            if (block.lastOffset < start) {
                continue
            }
            decodeBlockAndVisit(block, start, end, consumer)
        }
    }

    private fun findFirstBlockByLastOffset(start: Long): Int {
        var low = 0
        var high = blocks.size - 1
        var ans = blocks.size
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (blocks[mid].lastOffset >= start) {
                ans = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return ans
    }

    private fun decodeBlockAndVisit(
        block: BlockMeta,
        start: Long,
        end: Long,
        consumer: (offset: Long, isSoft: Boolean) -> Unit
    ) {
        raf.seek(block.filePos)
        val count = raf.readInt()
        val firstOffset = raf.readLong()

        val flagsLen = (count + 7) / 8
        val flags = ByteArray(flagsLen)
        raf.readFully(flags)

        var offset = firstOffset
        for (i in 0 until count) {
            if (offset in start until end) {
                val isSoft = ((flags[i ushr 3].toInt() ushr (i and 7)) and 1) == 1
                consumer(offset, isSoft)
            }
            if (i < count - 1) {
                val delta = raf.readVarLongOrNull() ?: break
                offset = max(offset + delta, offset)
            }
        }
    }

    override fun close() {
        raf.close()
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\softbreak\SoftBreakIndexBuilder.kt

```kotlin
@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber",
    "NestedBlockDepth",
    "ReturnCount"
)

package com.ireader.engines.txt.internal.softbreak

import com.ireader.engines.common.android.reflow.SoftBreakClassifier
import com.ireader.engines.common.android.reflow.SoftBreakClassifierContext
import com.ireader.engines.common.android.reflow.SoftBreakLineInfo
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.provider.ChapterDetector
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.io.writeStringUtf8
import com.ireader.engines.common.io.writeVarLong
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object SoftBreakIndexBuilder {

    private const val MAGIC = "SBX1"
    private const val VERSION = 6
    private const val BLOCK_NEWLINES = 4096
    private const val MAX_TITLE_CHARS = 80
    private const val CHUNK_CHARS = 128 * 1024

    private val detector = ChapterDetector()

    suspend fun buildIfNeeded(
        files: TxtBookFiles,
        meta: TxtMeta,
        ioDispatcher: CoroutineDispatcher,
        profile: SoftBreakTuningProfile
    ) = withContext(ioDispatcher) {
        val ruleConfig = SoftBreakRuleConfig.forProfile(profile)
        if (!files.contentU16.exists()) {
            return@withContext
        }
        SoftBreakIndex.openIfValid(
            file = files.softBreakIdx,
            meta = meta,
            profile = profile,
            rulesVersion = ruleConfig.rulesVersion
        )?.close()?.also {
            return@withContext
        }

        files.bookDir.mkdirs()
        RandomAccessFile(files.softBreakLock, "rw").channel.use { lockChannel ->
            lockChannel.lock().use {
                SoftBreakIndex.openIfValid(
                    file = files.softBreakIdx,
                    meta = meta,
                    profile = profile,
                    rulesVersion = ruleConfig.rulesVersion
                )?.close()?.also {
                    return@withContext
                }

                val tmp = File(files.bookDir, "softbreak.idx.tmp")
                prepareTempFile(tmp)
                val blocks = ArrayList<SoftBreakIndex.BlockMeta>(128)

                RandomAccessFile(tmp, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(MAGIC.toByteArray(Charsets.US_ASCII))
                    raf.writeInt(VERSION)
                    raf.writeInt(BLOCK_NEWLINES)
                    raf.writeLong(meta.lengthChars)

                    val newlineCountPos = raf.filePointer
                    raf.writeLong(0L)
                    raf.writeStringUtf8(meta.sampleHash)
                    raf.writeStringUtf8(profile.storageValue)
                    raf.writeInt(ruleConfig.rulesVersion)
                    val indexOffsetPos = raf.filePointer
                    raf.writeLong(0L)

                    val offsets = LongArray(BLOCK_NEWLINES)
                    val flags = BooleanArray(BLOCK_NEWLINES)
                    var inBlock = 0

                    fun flushBlock() {
                        if (inBlock <= 0) {
                            return
                        }
                        val filePos = raf.filePointer
                        val first = offsets[0]
                        val last = offsets[inBlock - 1]

                        raf.writeInt(inBlock)
                        raf.writeLong(first)

                        val flagsLen = (inBlock + 7) / 8
                        val packedFlags = ByteArray(flagsLen)
                        for (i in 0 until inBlock) {
                            if (flags[i]) {
                                packedFlags[i ushr 3] =
                                    (packedFlags[i ushr 3].toInt() or (1 shl (i and 7))).toByte()
                            }
                        }
                        raf.write(packedFlags)

                        var prev = first
                        for (i in 1 until inBlock) {
                            val delta = offsets[i] - prev
                            raf.writeVarLong(delta.coerceAtLeast(0L))
                            prev = offsets[i]
                        }

                        blocks.add(
                            SoftBreakIndex.BlockMeta(
                                filePos = filePos,
                                firstOffset = first,
                                lastOffset = last,
                                count = inBlock
                            )
                        )
                        inBlock = 0
                    }

                    var newlineCount = 0L
                    var globalOffset = 0L

                    var lineLength = 0
                    var leadingSpaces = 0
                    var seenNonSpace = false
                    var firstNonSpace: Char? = null
                    var secondNonSpace: Char? = null
                    var lastNonSpace: Char? = null
                    val lineTitle = StringBuilder(MAX_TITLE_CHARS)

                    var nonEmptyLineCount = 0L
                    var nonEmptyLineLengthSum = 0L

                    fun estimateTypicalLineLength(): Int {
                        if (nonEmptyLineCount == 0L) {
                            return 72
                        }
                        val avg = (nonEmptyLineLengthSum / nonEmptyLineCount).toInt()
                        val minTypical = if (meta.hardWrapLikely) {
                            ruleConfig.minTypicalHardWrap
                        } else {
                            ruleConfig.minTypicalNormal
                        }
                        return avg.coerceIn(minTypical, ruleConfig.maxTypical)
                    }

                    fun resetLineState() {
                        lineLength = 0
                        leadingSpaces = 0
                        seenNonSpace = false
                        firstNonSpace = null
                        secondNonSpace = null
                        lastNonSpace = null
                        lineTitle.setLength(0)
                    }

                    fun finishLine(): SoftBreakLineInfo {
                        val titleText = lineTitle.toString().trim()
                        val isBoundary = titleText.isNotEmpty() && detector.isChapterBoundaryTitle(titleText)
                        val info = SoftBreakLineInfo(
                            length = lineLength,
                            leadingSpaces = leadingSpaces,
                            firstNonSpace = firstNonSpace,
                            secondNonSpace = secondNonSpace,
                            lastNonSpace = lastNonSpace,
                            isBoundaryTitle = isBoundary,
                            startsWithListMarker = SoftBreakClassifier.detectListMarker(firstNonSpace, secondNonSpace),
                            startsWithDialogueMarker = SoftBreakClassifier.detectDialogueMarker(firstNonSpace)
                        )
                        if (lineLength > 0) {
                            nonEmptyLineCount++
                            nonEmptyLineLengthSum += lineLength.toLong()
                        }
                        resetLineState()
                        return info
                    }

                    fun finishLineIfHasData(): SoftBreakLineInfo? {
                        return if (lineLength > 0 || firstNonSpace != null || lastNonSpace != null) {
                            finishLine()
                        } else {
                            null
                        }
                    }

                    fun isSoftBreak(line0: SoftBreakLineInfo, line1: SoftBreakLineInfo): Boolean {
                        val context = SoftBreakClassifierContext(
                            typicalLineLength = estimateTypicalLineLength(),
                            hardWrapLikely = meta.hardWrapLikely,
                            rules = ruleConfig
                        )
                        return SoftBreakClassifier.classify(
                            line0 = line0,
                            line1 = line1,
                            context = context
                        ).isSoft
                    }

                    data class Pending(val newlineOffset: Long, val line0: SoftBreakLineInfo)
                    var pending: Pending? = null

                    fun recordNewline(offset: Long, isSoft: Boolean) {
                        offsets[inBlock] = offset
                        flags[inBlock] = isSoft
                        inBlock++
                        newlineCount++
                        if (inBlock >= BLOCK_NEWLINES) {
                            flushBlock()
                        }
                    }

                    Utf16TextStore(files.contentU16).use { store ->
                        while (globalOffset < store.lengthChars) {
                            coroutineContext.ensureActive()
                            val chunk = store.readChars(globalOffset, CHUNK_CHARS)
                            if (chunk.isEmpty()) {
                                break
                            }
                            for (c in chunk) {
                                coroutineContext.ensureActive()
                                if (c == '\n') {
                                    val currentLine = finishLine()
                                    val oldPending = pending
                                    if (oldPending != null) {
                                        recordNewline(
                                            offset = oldPending.newlineOffset,
                                            isSoft = isSoftBreak(oldPending.line0, currentLine)
                                        )
                                    }
                                    pending = Pending(newlineOffset = globalOffset, line0 = currentLine)
                                } else {
                                    lineLength++
                                    if (!seenNonSpace) {
                                        if (c == ' ' || c == '\t' || c == '\u3000') {
                                            leadingSpaces++
                                        } else {
                                            seenNonSpace = true
                                            firstNonSpace = c
                                        }
                                    } else if (secondNonSpace == null && !c.isWhitespace()) {
                                        secondNonSpace = c
                                    }
                                    if (!c.isWhitespace()) {
                                        lastNonSpace = c
                                    }
                                    if (lineTitle.length < MAX_TITLE_CHARS) {
                                        lineTitle.append(c)
                                    }
                                }
                                globalOffset++
                            }
                        }
                    }

                    val trailingLine = finishLineIfHasData()
                    val pendingLine = pending
                    if (pendingLine != null) {
                        val soft = trailingLine != null && isSoftBreak(pendingLine.line0, trailingLine)
                        recordNewline(pendingLine.newlineOffset, soft)
                    }
                    flushBlock()

                    val indexOffset = raf.filePointer
                    raf.writeInt(blocks.size)
                    for (block in blocks) {
                        raf.writeLong(block.filePos)
                        raf.writeLong(block.firstOffset)
                        raf.writeLong(block.lastOffset)
                        raf.writeInt(block.count)
                    }

                    raf.seek(indexOffsetPos)
                    raf.writeLong(indexOffset)
                    raf.seek(newlineCountPos)
                    raf.writeLong(newlineCount)
                }

                replaceFileAtomically(tempFile = tmp, targetFile = files.softBreakIdx)
            }
        }
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\internal\store\Utf16TextStore.kt

```kotlin
@file:Suppress("ComplexCondition", "ReturnCount")

package com.ireader.engines.txt.internal.store

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.min

internal class Utf16TextStore(
    file: File
) : Closeable {

    private val raf = RandomAccessFile(file, "r")
    private val channel: FileChannel = raf.channel
    val lengthChars: Long = channel.size() / 2L

    fun readChars(start: Long, count: Int): CharArray {
        if (count <= 0 || lengthChars <= 0L) {
            return CharArray(0)
        }
        val alignedStart = alignStart(start.coerceIn(0L, lengthChars))
        val available = (lengthChars - alignedStart).coerceAtLeast(0L)
        if (available == 0L) {
            return CharArray(0)
        }

        var requestedChars = min(count.toLong(), available).toInt()
        requestedChars = alignCount(alignedStart, requestedChars)
        if (requestedChars <= 0) {
            return CharArray(0)
        }

        val bytes = ByteBuffer.allocate(requestedChars * 2).order(ByteOrder.LITTLE_ENDIAN)
        var read = 0L
        val positionBytes = alignedStart * 2L
        while (bytes.hasRemaining()) {
            val readNow = channel.read(bytes, positionBytes + read)
            if (readNow <= 0) {
                break
            }
            read += readNow
        }
        bytes.flip()

        val charCount = bytes.remaining() / 2
        val out = CharArray(charCount)
        var i = 0
        while (i < charCount) {
            out[i] = bytes.char
            i++
        }
        return out
    }

    fun readString(start: Long, count: Int): String {
        val chars = readChars(start, count)
        return String(chars)
    }

    fun readAround(center: Long, before: Int, after: Int): String {
        val safeCenter = center.coerceIn(0L, lengthChars)
        val start = (safeCenter - before.toLong()).coerceAtLeast(0L)
        val end = (safeCenter + after.toLong()).coerceAtMost(lengthChars)
        val count = (end - start).toInt().coerceAtLeast(0)
        return readString(start, count)
    }

    override fun close() {
        channel.close()
        raf.close()
    }

    private fun alignStart(start: Long): Long {
        if (start <= 0L || start >= lengthChars) {
            return start
        }
        val current = readSingle(start) ?: return start
        if (!Character.isLowSurrogate(current)) {
            return start
        }
        val previous = readSingle(start - 1L)
        return if (previous != null && Character.isHighSurrogate(previous)) {
            start - 1L
        } else {
            start
        }
    }

    private fun alignCount(start: Long, requested: Int): Int {
        val end = start + requested
        if (requested <= 0 || end <= 0L || end >= lengthChars) {
            return requested
        }
        val last = readSingle(end - 1L)
        val next = readSingle(end)
        if (last != null && next != null &&
            Character.isHighSurrogate(last) &&
            Character.isLowSurrogate(next)
        ) {
            return requested - 1
        }
        return requested
    }

    private fun readSingle(index: Long): Char? {
        if (index < 0L || index >= lengthChars) {
            return null
        }
        val bytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        val read = channel.read(bytes, index * 2L)
        if (read != 2) {
            return null
        }
        bytes.flip()
        return bytes.char
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\TxtEngine.kt

```kotlin
package com.ireader.engines.txt

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.internal.open.TxtDocument
import com.ireader.engines.txt.internal.open.TxtOpenResult
import com.ireader.engines.txt.internal.open.TxtOpener
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat

class TxtEngine(
    private val config: TxtEngineConfig
) : ReaderEngine {

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.TXT)

    private val opener by lazy(LazyThreadSafetyMode.NONE) {
        TxtOpener(
            cacheDir = config.cacheDir,
            ioDispatcher = config.ioDispatcher
        )
    }

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return when (val result = opener.open(source, options)) {
            is ReaderResult.Err -> result
            is ReaderResult.Ok -> {
                ReaderResult.Ok(
                    createDocument(
                        openResult = result.value,
                        source = source,
                        options = options
                    )
                )
            }
        }
    }

    private fun createDocument(
        openResult: TxtOpenResult,
        source: DocumentSource,
        options: OpenOptions
    ): ReaderDocument {
        return TxtDocument(
            id = openResult.documentId,
            source = source,
            files = openResult.files,
            meta = openResult.meta,
            openOptions = options,
            persistPagination = config.persistPagination,
            persistOutline = config.persistOutline,
            maxPageCache = config.maxPageCache,
            annotationProviderFactory = config.annotationProviderFactory,
            ioDispatcher = config.ioDispatcher,
            defaultDispatcher = config.defaultDispatcher
        )
    }
}
```

## engines/txt\src\main\kotlin\com\ireader\engines\txt\TxtEngineConfig.kt

```kotlin
package com.ireader.engines.txt

import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.model.DocumentId
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class TxtEngineConfig(
    val cacheDir: File,
    val persistPagination: Boolean = true,
    val persistOutline: Boolean = false,
    val maxPageCache: Int = 7,
    val annotationProviderFactory: ((DocumentId) -> AnnotationProvider?)? = null,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
)
```

