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
