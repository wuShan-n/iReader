package com.ireader.engines.txt.internal.softbreak

import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.util.readStringUtf8
import com.ireader.engines.txt.internal.util.readVarLongOrNull
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
    private val blocks: List<BlockMeta>
) : Closeable {

    internal data class BlockMeta(
        val filePos: Long,
        val firstOffset: Long,
        val lastOffset: Long,
        val count: Int
    )

    companion object {
        private const val MAGIC = "SBX1"
        private const val VERSION = 1

        fun openIfValid(file: File, meta: TxtMeta): SoftBreakIndex? {
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
                val indexOffset = raf.readLong()

                if (lengthChars != meta.lengthChars || sampleHash != meta.sampleHash) {
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
                    blocks = blocks
                )
            } catch (_: Throwable) {
                runCatching { raf.close() }
                null
            }
        }
    }

    fun forEachNewlineInRange(
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
