@file:Suppress("ComplexCondition", "ReturnCount")

package com.ireader.engines.txt.internal.store

import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.math.min

internal class Utf16TextStore(
    file: File,
    private val chunkSizeCodeUnits: Int = DEFAULT_CHUNK_SIZE_CODE_UNITS,
    private val maxCachedChunks: Int = DEFAULT_MAX_CACHED_CHUNKS
) : Closeable {

    private val channel: FileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)
    val lengthCodeUnits: Long = channel.size() / 2L
    val lengthChars: Long
        get() = lengthCodeUnits

    private val chunkCache = LinkedHashMap<Long, CharArray>(
        maxCachedChunks.coerceAtLeast(2),
        0.75f,
        true
    )
    private val cacheLock = Any()

    fun readChars(start: Long, count: Int): CharArray {
        if (count <= 0 || lengthCodeUnits <= 0L) {
            return CharArray(0)
        }

        val alignedStart = alignStart(start.coerceIn(0L, lengthCodeUnits))
        val available = (lengthCodeUnits - alignedStart).coerceAtLeast(0L)
        if (available == 0L) {
            return CharArray(0)
        }

        var requestedCodeUnits = min(count.toLong(), available).toInt()
        requestedCodeUnits = alignCount(alignedStart, requestedCodeUnits)
        if (requestedCodeUnits <= 0) {
            return CharArray(0)
        }

        val out = CharArray(requestedCodeUnits)
        var cursor = alignedStart
        var copied = 0
        while (copied < requestedCodeUnits) {
            val chunkId = chunkIdForOffset(cursor)
            val chunk = loadChunk(chunkId)
            if (chunk.isEmpty()) {
                break
            }
            val chunkStart = chunkId * chunkSizeCodeUnits.toLong()
            val inChunk = (cursor - chunkStart).toInt().coerceAtLeast(0)
            if (inChunk >= chunk.size) {
                break
            }
            val toCopy = min(requestedCodeUnits - copied, chunk.size - inChunk)
            chunk.copyInto(out, destinationOffset = copied, startIndex = inChunk, endIndex = inChunk + toCopy)
            copied += toCopy
            cursor += toCopy.toLong()
        }

        return if (copied == out.size) out else out.copyOf(copied)
    }

    fun readString(start: Long, count: Int): String {
        val chars = readChars(start, count)
        return String(chars)
    }

    fun readAround(center: Long, before: Int, after: Int): String {
        val safeCenter = center.coerceIn(0L, lengthCodeUnits)
        val start = (safeCenter - before.toLong()).coerceAtLeast(0L)
        val end = (safeCenter + after.toLong()).coerceAtMost(lengthCodeUnits)
        val count = (end - start).toInt().coerceAtLeast(0)
        return readString(start, count)
    }

    override fun close() {
        synchronized(cacheLock) {
            chunkCache.clear()
        }
        channel.close()
    }

    private fun alignStart(start: Long): Long {
        if (start <= 0L || start >= lengthCodeUnits) {
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
        if (requested <= 0 || end <= 0L || end >= lengthCodeUnits) {
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
        if (index < 0L || index >= lengthCodeUnits) {
            return null
        }
        val chunk = loadChunk(chunkIdForOffset(index))
        if (chunk.isEmpty()) {
            return null
        }
        val chunkStart = chunkIdForOffset(index) * chunkSizeCodeUnits.toLong()
        val localIndex = (index - chunkStart).toInt()
        return chunk.getOrNull(localIndex)
    }

    private fun loadChunk(chunkId: Long): CharArray {
        synchronized(cacheLock) {
            chunkCache[chunkId]?.also { return it }
        }
        val chunk = readChunkFromChannel(chunkId)
        synchronized(cacheLock) {
            chunkCache[chunkId] = chunk
            trimCacheIfNeeded()
        }
        prefetchNeighbor(chunkId + 1L)
        return chunk
    }

    private fun prefetchNeighbor(chunkId: Long) {
        if (chunkId < 0L) {
            return
        }
        synchronized(cacheLock) {
            if (chunkCache.containsKey(chunkId) || chunkCache.size >= maxCachedChunks) {
                return
            }
        }
        val chunkStart = chunkId * chunkSizeCodeUnits.toLong()
        if (chunkStart >= lengthCodeUnits) {
            return
        }
        val chunk = readChunkFromChannel(chunkId)
        synchronized(cacheLock) {
            if (!chunkCache.containsKey(chunkId)) {
                chunkCache[chunkId] = chunk
                trimCacheIfNeeded()
            }
        }
    }

    private fun trimCacheIfNeeded() {
        while (chunkCache.size > maxCachedChunks) {
            val iterator = chunkCache.entries.iterator()
            if (!iterator.hasNext()) {
                break
            }
            val eldest = iterator.next()
            chunkCache.remove(eldest.key)
        }
    }

    private fun readChunkFromChannel(chunkId: Long): CharArray {
        val chunkStartCodeUnits = chunkId * chunkSizeCodeUnits.toLong()
        if (chunkStartCodeUnits >= lengthCodeUnits) {
            return CharArray(0)
        }
        val chunkLengthCodeUnits = min(
            chunkSizeCodeUnits.toLong(),
            lengthCodeUnits - chunkStartCodeUnits
        ).toInt()
        val byteCount = chunkLengthCodeUnits * 2
        val buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        var position = chunkStartCodeUnits * 2L
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, position)
            if (read <= 0) {
                break
            }
            position += read.toLong()
        }
        buffer.flip()
        val charCount = buffer.remaining() / 2
        if (charCount <= 0) {
            return CharArray(0)
        }
        val out = CharArray(charCount)
        buffer.asCharBuffer().get(out, 0, charCount)
        return out
    }

    private fun chunkIdForOffset(offset: Long): Long {
        return offset.coerceAtLeast(0L) / chunkSizeCodeUnits.toLong()
    }

    private companion object {
        private const val DEFAULT_CHUNK_SIZE_CODE_UNITS = 128 * 1024
        private const val DEFAULT_MAX_CACHED_CHUNKS = 6
    }
}
