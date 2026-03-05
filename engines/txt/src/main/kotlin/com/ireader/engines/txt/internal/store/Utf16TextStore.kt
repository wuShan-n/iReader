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

        val bytesToRead = requestedChars * 2
        val bytes = acquireReadBuffer(bytesToRead)
        var positionBytes = alignedStart * 2L

        while (bytes.hasRemaining()) {
            val readNow = channel.read(bytes, positionBytes)
            if (readNow <= 0) {
                break
            }
            positionBytes += readNow.toLong()
        }
        bytes.flip()

        val charCount = bytes.remaining() / 2
        if (charCount <= 0) {
            return CharArray(0)
        }
        bytes.limit(charCount * 2)

        val out = CharArray(charCount)
        bytes.asCharBuffer().get(out, 0, charCount)
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

        val bytes = checkNotNull(singleCharBufferTL.get())
        bytes.clear()
        val read = channel.read(bytes, index * 2L)
        if (read != 2) {
            return null
        }
        bytes.flip()
        return bytes.char
    }

    private fun acquireReadBuffer(requiredBytes: Int): ByteBuffer {
        var buffer = checkNotNull(readBufferTL.get())
        if (requiredBytes > buffer.capacity() && requiredBytes <= MAX_TL_READ_BUFFER_BYTES) {
            buffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.LITTLE_ENDIAN)
            readBufferTL.set(buffer)
        }
        if (requiredBytes > buffer.capacity()) {
            return ByteBuffer.allocate(requiredBytes).order(ByteOrder.LITTLE_ENDIAN)
        }
        buffer.clear()
        buffer.limit(requiredBytes)
        return buffer
    }

    private companion object {
        private const val DEFAULT_TL_READ_BUFFER_BYTES = 256 * 1024
        private const val MAX_TL_READ_BUFFER_BYTES = 1024 * 1024

        private val readBufferTL: ThreadLocal<ByteBuffer> = ThreadLocal.withInitial {
            ByteBuffer.allocateDirect(DEFAULT_TL_READ_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        }

        private val singleCharBufferTL: ThreadLocal<ByteBuffer> = ThreadLocal.withInitial {
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        }
    }
}
