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

