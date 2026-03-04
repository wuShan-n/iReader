package com.ireader.engines.txt.internal.pagination

import com.ireader.engines.txt.internal.util.prepareTempFile
import com.ireader.engines.txt.internal.util.replaceFileAtomically
import com.ireader.engines.txt.internal.util.readVarLongOrNull
import com.ireader.engines.txt.internal.util.writeVarLong
import java.io.File
import java.io.RandomAccessFile
import java.util.TreeSet

internal object PageMap {

    private const val MAGIC = 0x504D4150 // PMAP
    private const val VERSION = 1

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
            raf.setLength(0L)
            raf.writeInt(MAGIC)
            raf.writeInt(VERSION)
            raf.writeInt(normalized.size)
            var prev = 0L
            for (value in normalized) {
                val delta = (value - prev).coerceAtLeast(0L)
                raf.writeVarLong(delta)
                prev = value
            }
        }

        replaceFileAtomically(tempFile = tmp, targetFile = binaryFile)
    }

    private fun readBinary(file: File): TreeSet<Long>? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.readInt() != MAGIC) {
                    return null
                }
                if (raf.readInt() != VERSION) {
                    return null
                }
                val count = raf.readInt()
                val out = TreeSet<Long>()
                var value = 0L
                for (i in 0 until count) {
                    val delta = raf.readVarLongOrNull() ?: break
                    value += delta
                    out.add(value)
                }
                out
            }
        }.getOrNull()
    }

    private fun readLegacy(file: File): TreeSet<Long> {
        val out = TreeSet<Long>()
        file.forEachLine { line ->
            line.trim().toLongOrNull()?.takeIf { it >= 0L }?.also { out.add(it) }
        }
        return out
    }
}
