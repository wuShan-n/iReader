package com.ireader.engines.common.pagination

import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.readVarLongOrNull
import com.ireader.engines.common.io.replaceFileAtomically
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.TreeSet
import java.util.zip.CRC32

object PageMap {

    private const val MAGIC = 0x504D4150 // PMAP
    private const val VERSION_V1 = 1
    private const val VERSION_V2 = 2

    fun load(binaryFile: File, legacyTextFile: File? = null): TreeSet<Long> {
        if (binaryFile.exists()) {
            readBinary(binaryFile)?.also { return it }
        }
        if (legacyTextFile != null && legacyTextFile.exists()) {
            return readLegacy(legacyTextFile)
        }
        return TreeSet()
    }

    fun save(binaryFile: File, starts: Collection<Long>) {
        val normalized = TreeSet<Long>()
        for (value in starts) {
            if (value >= 0L) {
                normalized.add(value)
            }
        }
        val tmp = File(binaryFile.parentFile, "${binaryFile.name}.tmp")
        prepareTempFile(tmp)

        RandomAccessFile(tmp, "rw").use { raf ->
            writeBinaryV2(raf, normalized)
        }

        replaceFileAtomically(tempFile = tmp, targetFile = binaryFile)
    }

    private fun readBinary(file: File): TreeSet<Long>? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.readInt() != MAGIC) {
                    return null
                }
                val version = raf.readInt()
                when (version) {
                    VERSION_V1 -> readBinaryV1(raf)
                    VERSION_V2 -> readBinaryV2(raf)
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun writeBinaryV2(raf: RandomAccessFile, values: Collection<Long>) {
        raf.setLength(0L)
        raf.writeInt(MAGIC)
        raf.writeInt(VERSION_V2)
        raf.writeInt(values.size)
        raf.writeInt(0) // placeholder

        val checksum = CRC32()
        var prev = 0L
        for (value in values) {
            val delta = (value - prev).coerceAtLeast(0L)
            val encoded = encodeVarLong(delta)
            raf.write(encoded)
            checksum.update(encoded)
            prev = value
        }

        raf.seek(12L)
        raf.writeInt(checksum.value.toInt())
        raf.fd.sync()
    }

    private fun readBinaryV1(raf: RandomAccessFile): TreeSet<Long>? {
        val count = raf.readInt()
        if (count < 0) {
            return null
        }
        val out = TreeSet<Long>()
        var value = 0L
        for (i in 0 until count) {
            val delta = raf.readVarLongOrNull() ?: return null
            value += delta
            out.add(value)
        }
        return out
    }

    private fun readBinaryV2(raf: RandomAccessFile): TreeSet<Long>? {
        val count = raf.readInt()
        if (count < 0) {
            return null
        }
        val expectedChecksum = raf.readInt()
        val checksum = CRC32()
        val out = TreeSet<Long>()
        var value = 0L
        for (i in 0 until count) {
            val delta = raf.readVarLongWithCrcOrNull(checksum) ?: return null
            value += delta
            out.add(value)
        }
        if (checksum.value.toInt() != expectedChecksum) {
            return null
        }
        return out
    }

    private fun RandomAccessFile.readVarLongWithCrcOrNull(checksum: CRC32): Long? {
        var shift = 0
        var result = 0L
        try {
            while (shift < 64) {
                val byte = readUnsignedByte()
                checksum.update(byte)
                result = result or ((byte and 0x7F).toLong() shl shift)
                if ((byte and 0x80) == 0) {
                    return result
                }
                shift += 7
            }
            throw IOException("VarLong too long")
        } catch (_: EOFException) {
            return null
        }
    }

    private fun encodeVarLong(value: Long): ByteArray {
        require(value >= 0L) { "VarLong only supports non-negative values: $value" }
        val out = ByteArray(10)
        var index = 0
        var remaining = value
        while ((remaining and -128L) != 0L) {
            out[index++] = ((remaining and 0x7FL) or 0x80L).toByte()
            remaining = remaining ushr 7
        }
        out[index++] = remaining.toByte()
        return out.copyOf(index)
    }

    private fun readLegacy(file: File): TreeSet<Long> {
        val out = TreeSet<Long>()
        file.forEachLine { line ->
            line.trim().toLongOrNull()?.takeIf { it >= 0L }?.also { out.add(it) }
        }
        return out
    }
}
