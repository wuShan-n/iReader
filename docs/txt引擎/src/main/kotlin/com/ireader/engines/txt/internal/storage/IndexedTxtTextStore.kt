package com.ireader.engines.txt.internal.storage

import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.internal.open.TxtTextNormalizer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class IndexedTxtTextStore(
    private val source: DocumentSource,
    private val pfd: ParcelFileDescriptor,
    override val charset: Charset,
    private val ioDispatcher: CoroutineDispatcher,
    private val anchorEveryBytes: Long,
    private val windowCacheChars: Int
) : TxtTextStore {
    private companion object {
        private const val MIN_WINDOW_CACHE_CHARS = 8 * 1024
    }

    private data class CharWindow(
        val startChar: Int,
        val endCharExclusive: Int,
        val text: String
    )

    private val mutex = Mutex()
    private var index: ChunkIndex? = null
    private var charWindowCache: CharWindow? = null

    private val channel: FileChannel by lazy {
        FileInputStream(pfd.fileDescriptor).channel
    }

    override suspend fun totalChars(): Int = withContext(ioDispatcher) {
        ensureIndex().totalChars
    }

    override suspend fun readRange(startChar: Int, endCharExclusive: Int): String = withContext(ioDispatcher) {
        val idx = ensureIndex()
        if (idx.totalChars <= 0) return@withContext ""

        val start = startChar.coerceIn(0, idx.totalChars)
        val end = endCharExclusive.coerceIn(start, idx.totalChars)
        if (start == end) return@withContext ""

        val anchor = idx.anchorFor(start)
        mutex.withLock {
            charWindowCache?.let { window ->
                sliceFromWindow(window, start, end)?.let { return@withLock it }
            }

            val maxWindow = windowCacheChars.coerceAtLeast(MIN_WINDOW_CACHE_CHARS)
            val requestLen = end - start
            if (requestLen > maxWindow) {
                readFromAnchorLocked(
                    anchor = anchor,
                    startChar = start,
                    endChar = end
                )
            } else {
                val window = buildWindowLocked(
                    anchor = anchor,
                    startChar = start,
                    maxWindowChars = maxWindow,
                    totalChars = idx.totalChars
                )
                charWindowCache = window
                sliceFromWindow(window, start, end) ?: window.text
            }
        }
    }

    override suspend fun readChars(startChar: Int, maxChars: Int): String {
        val start = startChar.coerceAtLeast(0)
        val safe = maxChars.coerceAtLeast(0)
        return readRange(start, start + safe)
    }

    override suspend fun readAround(charOffset: Int, maxChars: Int): String = withContext(ioDispatcher) {
        val idx = ensureIndex()
        if (idx.totalChars <= 0) return@withContext ""

        val safe = maxChars.coerceAtLeast(1)
        val half = (safe / 2).coerceAtLeast(1)
        val center = charOffset.coerceIn(0, idx.totalChars)
        val start = (center - half).coerceAtLeast(0)
        val end = (start + safe).coerceAtMost(idx.totalChars)
        readRange(start, end)
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { pfd.close() }
        charWindowCache = null
    }

    private suspend fun ensureIndex(): ChunkIndex {
        index?.let { return it }
        return mutex.withLock {
            index ?: buildIndexLocked().also { index = it }
        }
    }

    private suspend fun buildIndexLocked(): ChunkIndex {
        val header = source.openInputStream().use { input ->
            val buf = ByteArray(4)
            val n = input.read(buf)
            if (n <= 0) ByteArray(0) else buf.copyOf(n)
        }
        val startByteOffset = BomUtil.bomSkipBytes(header, charset).toLong()

        val anchors = ArrayList<ChunkAnchor>(256)
        val streamState = TxtTextNormalizer.StreamState()
        anchors.add(
            ChunkAnchor(
                charOffset = 0,
                byteOffset = startByteOffset,
                lastWasCr = streamState.lastWasCr,
                emittedAny = streamState.emittedAny
            )
        )

        channel.position(startByteOffset)
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val byteBuf = ByteBuffer.allocateDirect(256 * 1024)
        val charBuf = CharBuffer.allocate(16 * 1024)

        var decodedChars = 0
        var consumedBytes = 0L
        var lastAnchorBytes = 0L
        var endOfInput = false

        while (!endOfInput) {
            coroutineContext.ensureActive()
            val read = channel.read(byteBuf)
            if (read < 0) {
                endOfInput = true
            }

            byteBuf.flip()
            while (true) {
                charBuf.clear()
                val before = byteBuf.position()
                val result = decoder.decode(byteBuf, charBuf, endOfInput)
                val after = byteBuf.position()
                val consumedNow = (after - before).toLong()
                if (consumedNow > 0) {
                    consumedBytes += consumedNow
                }

                val produced = charBuf.position()
                if (produced > 0) {
                    charBuf.flip()
                    TxtTextNormalizer.appendNormalized(charBuf, streamState) {
                        decodedChars += 1
                    }
                    while (consumedBytes - lastAnchorBytes >= anchorEveryBytes) {
                        anchors.add(
                            ChunkAnchor(
                                charOffset = decodedChars,
                                byteOffset = startByteOffset + consumedBytes,
                                lastWasCr = streamState.lastWasCr,
                                emittedAny = streamState.emittedAny
                            )
                        )
                        lastAnchorBytes += anchorEveryBytes
                    }
                }

                if (result.isOverflow) continue
                if (result.isUnderflow) break
            }
            byteBuf.compact()
        }

        while (true) {
            coroutineContext.ensureActive()
            charBuf.clear()
            val result = decoder.flush(charBuf)
            val produced = charBuf.position()
            if (produced > 0) {
                charBuf.flip()
                TxtTextNormalizer.appendNormalized(charBuf, streamState) {
                    decodedChars += 1
                }
            }
            if (result.isUnderflow) break
        }

        val endByteOffset = channel.size().coerceAtLeast(startByteOffset)
        if (anchors.lastOrNull()?.byteOffset != endByteOffset) {
            anchors.add(
                ChunkAnchor(
                    charOffset = decodedChars,
                    byteOffset = endByteOffset,
                    lastWasCr = streamState.lastWasCr,
                    emittedAny = streamState.emittedAny
                )
            )
        }

        return ChunkIndex(
            anchors = anchors,
            totalChars = decodedChars,
            startByteOffset = startByteOffset
        )
    }

    private suspend fun readFromAnchorLocked(
        anchor: ChunkAnchor,
        startChar: Int,
        endChar: Int
    ): String {
        channel.position(anchor.byteOffset)
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val byteBuf = ByteBuffer.allocateDirect(256 * 1024)
        val charBuf = CharBuffer.allocate(16 * 1024)
        val builder = StringBuilder((endChar - startChar).coerceAtMost(64 * 1024))
        val state = TxtTextNormalizer.StreamState(
            lastWasCr = anchor.lastWasCr,
            emittedAny = anchor.emittedAny
        )

        var currentChar = anchor.charOffset
        var endOfInput = false

        while (!endOfInput && currentChar < endChar) {
            coroutineContext.ensureActive()
            val read = channel.read(byteBuf)
            if (read < 0) {
                endOfInput = true
            }

            byteBuf.flip()
            while (true) {
                charBuf.clear()
                val result = decoder.decode(byteBuf, charBuf, endOfInput)
                val produced = charBuf.position()
                if (produced > 0) {
                    charBuf.flip()
                    currentChar = appendNormalizedRange(
                        buffer = charBuf,
                        state = state,
                        currentChar = currentChar,
                        startChar = startChar,
                        endChar = endChar,
                        out = builder
                    )
                }

                if (currentChar >= endChar) break
                if (result.isOverflow) continue
                if (result.isUnderflow) break
            }
            byteBuf.compact()
        }

        if (currentChar < endChar) {
            while (true) {
                coroutineContext.ensureActive()
                charBuf.clear()
                val result = decoder.flush(charBuf)
                val produced = charBuf.position()
                if (produced > 0) {
                    charBuf.flip()
                    currentChar = appendNormalizedRange(
                        buffer = charBuf,
                        state = state,
                        currentChar = currentChar,
                        startChar = startChar,
                        endChar = endChar,
                        out = builder
                    )
                }
                if (result.isUnderflow || currentChar >= endChar) break
            }
        }

        return builder.toString()
    }

    private suspend fun buildWindowLocked(
        anchor: ChunkAnchor,
        startChar: Int,
        maxWindowChars: Int,
        totalChars: Int
    ): CharWindow {
        val windowEnd = (startChar + maxWindowChars).coerceAtMost(totalChars)
        val text = readFromAnchorLocked(
            anchor = anchor,
            startChar = startChar,
            endChar = windowEnd
        )
        return CharWindow(
            startChar = startChar,
            endCharExclusive = startChar + text.length,
            text = text
        )
    }

    private fun appendNormalizedRange(
        buffer: CharBuffer,
        state: TxtTextNormalizer.StreamState,
        currentChar: Int,
        startChar: Int,
        endChar: Int,
        out: StringBuilder
    ): Int {
        var cursor = currentChar
        TxtTextNormalizer.appendNormalized(buffer, state) { normalizedChar ->
            if (cursor >= endChar) return@appendNormalized
            if (cursor >= startChar) {
                out.append(normalizedChar)
            }
            cursor += 1
        }
        return cursor
    }

    private fun sliceFromWindow(
        window: CharWindow,
        startChar: Int,
        endChar: Int
    ): String? {
        if (startChar < window.startChar || endChar > window.endCharExclusive) return null
        val localStart = startChar - window.startChar
        val localEnd = endChar - window.startChar
        if (localStart !in 0..window.text.length) return null
        if (localEnd !in localStart..window.text.length) return null
        return window.text.substring(localStart, localEnd)
    }
}
