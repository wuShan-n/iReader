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

    fun bitsetBytes(): Int = bitsetBits / 8

    fun mayContainAll(
        raf: RandomAccessFile,
        blockIndex: Int,
        hashes: IntArray,
        scratch: ByteArray?
    ): Boolean {
        if (hashes.isEmpty()) {
            return true
        }
        if (blockIndex < 0 || blockIndex >= blocksCount) {
            return false
        }
        val bytes = bitsetBytes()
        val bitset = if (scratch != null && scratch.size >= bytes) {
            scratch
        } else {
            ByteArray(bytes)
        }
        raf.seek(dataOffset + blockIndex.toLong() * bytes.toLong())
        raf.readFully(bitset, 0, bytes)
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

    fun mayContainAll(raf: RandomAccessFile, blockIndex: Int, hashes: IntArray): Boolean {
        return mayContainAll(raf, blockIndex, hashes, scratch = null)
    }

    fun blockRange(blockIndex: Int): BlockRange {
        val start = blockIndex.toLong() * blockChars.toLong()
        val endExclusive = min(lengthChars, start + blockChars.toLong())
        return BlockRange(start = start, endExclusive = endExclusive)
    }
}
