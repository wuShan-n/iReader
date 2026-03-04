package com.ireader.engines.common.io

import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile

fun RandomAccessFile.writeVarLong(value: Long) {
    require(value >= 0L) { "VarLong only supports non-negative values: $value" }
    var v = value
    while ((v and -128L) != 0L) {
        writeByte(((v and 0x7FL) or 0x80L).toInt())
        v = v ushr 7
    }
    writeByte(v.toInt())
}

fun RandomAccessFile.readVarLongOrNull(): Long? {
    var shift = 0
    var result = 0L
    try {
        while (shift < 64) {
            val b = readUnsignedByte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) {
                return result
            }
            shift += 7
        }
        throw IOException("VarLong too long")
    } catch (_: EOFException) {
        return null
    }
}

fun RandomAccessFile.writeStringUtf8(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= 0xFFFF) { "String too long for u16 length: ${bytes.size}" }
    writeShort(bytes.size)
    write(bytes)
}

fun RandomAccessFile.readStringUtf8(): String {
    val len = readShort().toInt() and 0xFFFF
    val bytes = ByteArray(len)
    readFully(bytes)
    return String(bytes, Charsets.UTF_8)
}

