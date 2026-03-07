package com.ireader.engines.txt.internal.open

import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.readStringUtf8
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.io.writeStringUtf8
import com.ireader.engines.txt.internal.locator.TextAnchor
import com.ireader.engines.txt.internal.locator.TextAnchorAffinity
import com.ireader.engines.txt.internal.store.Utf16TextStore
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class TxtBlockIndex private constructor(
    val blockSizeCodeUnits: Int,
    val lengthCodeUnits: Long,
    val sampleHash: String,
    val blockCount: Int,
    private val newlineCounts: IntArray,
    private val newlineOrdinals: LongArray
) {

    fun blockIdForOffset(offset: Long): Int {
        if (lengthCodeUnits <= 0L) {
            return 0
        }
        val safe = offset.coerceIn(0L, lengthCodeUnits)
        val raw = (safe / blockSizeCodeUnits.toLong()).toInt()
        return raw.coerceIn(0, (blockCount - 1).coerceAtLeast(0))
    }

    fun anchorForOffset(
        offset: Long,
        revision: Int,
        affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD
    ): TextAnchor {
        val safe = offset.coerceIn(0L, lengthCodeUnits)
        return TextAnchor(
            utf16Offset = safe,
            blockId = blockIdForOffset(safe),
            affinity = affinity,
            revision = revision
        )
    }

    fun blockStartOffset(blockId: Int): Long {
        val safe = blockId.coerceIn(0, (blockCount - 1).coerceAtLeast(0))
        return safe.toLong() * blockSizeCodeUnits.toLong()
    }

    fun blockEndOffset(blockId: Int): Long {
        val next = blockStartOffset(blockId) + blockSizeCodeUnits.toLong()
        return min(lengthCodeUnits, next)
    }

    fun firstNewlineOrdinal(blockId: Int): Long {
        val safe = blockId.coerceIn(0, newlineOrdinals.lastIndex.coerceAtLeast(0))
        return newlineOrdinals.getOrElse(safe) { 0L }
    }

    fun newlineCount(blockId: Int): Int {
        val safe = blockId.coerceIn(0, newlineCounts.lastIndex.coerceAtLeast(0))
        return newlineCounts.getOrElse(safe) { 0 }
    }

    companion object {
        private const val MAGIC = "TBI2"
        private const val VERSION = 1

        fun minimal(meta: TxtMeta): TxtBlockIndex {
            val blockSize = meta.defaultBlockSizeCodeUnits.coerceAtLeast(4 * 1024)
            val blockCount = if (meta.lengthCodeUnits <= 0L) {
                1
            } else {
                ((meta.lengthCodeUnits + blockSize - 1L) / blockSize.toLong()).toInt()
            }
            return TxtBlockIndex(
                blockSizeCodeUnits = blockSize,
                lengthCodeUnits = meta.lengthCodeUnits,
                sampleHash = meta.sampleHash,
                blockCount = blockCount,
                newlineCounts = IntArray(blockCount),
                newlineOrdinals = LongArray(blockCount)
            )
        }

        fun openIfValid(file: File, meta: TxtMeta): TxtBlockIndex? {
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
                    val blockSize = raf.readInt()
                    val lengthCodeUnits = raf.readLong()
                    val sampleHash = raf.readStringUtf8()
                    val blockCount = raf.readInt()
                    if (sampleHash != meta.sampleHash || lengthCodeUnits != meta.lengthCodeUnits) {
                        return null
                    }
                    val newlineCounts = IntArray(blockCount)
                    val newlineOrdinals = LongArray(blockCount)
                    for (index in 0 until blockCount) {
                        newlineCounts[index] = raf.readInt()
                        newlineOrdinals[index] = raf.readLong()
                    }
                    TxtBlockIndex(
                        blockSizeCodeUnits = blockSize,
                        lengthCodeUnits = lengthCodeUnits,
                        sampleHash = sampleHash,
                        blockCount = blockCount,
                        newlineCounts = newlineCounts,
                        newlineOrdinals = newlineOrdinals
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
            openIfValid(file, meta)?.also { return@withContext }

            lockFile.parentFile?.mkdirs()
            RandomAccessFile(lockFile, "rw").channel.use { lockChannel ->
                lockChannel.lock().use {
                    openIfValid(file, meta)?.also { return@withContext }

                    val blockSize = meta.defaultBlockSizeCodeUnits.coerceAtLeast(4 * 1024)
                    val blockCount = if (store.lengthCodeUnits <= 0L) {
                        1
                    } else {
                        ((store.lengthCodeUnits + blockSize - 1L) / blockSize.toLong()).toInt()
                    }
                    val newlineCounts = IntArray(blockCount)
                    val newlineOrdinals = LongArray(blockCount)
                    var runningNewlines = 0L
                    for (blockId in 0 until blockCount) {
                        coroutineContext.ensureActive()
                        val start = blockId.toLong() * blockSize.toLong()
                        val end = min(store.lengthCodeUnits, start + blockSize.toLong())
                        val count = (end - start).toInt().coerceAtLeast(0)
                        newlineOrdinals[blockId] = runningNewlines
                        if (count <= 0) {
                            continue
                        }
                        val chars = store.readChars(start, count)
                        var localNewlines = 0
                        for (char in chars) {
                            if (char == '\n') {
                                localNewlines++
                            }
                        }
                        newlineCounts[blockId] = localNewlines
                        runningNewlines += localNewlines.toLong()
                    }

                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    prepareTempFile(tmp)
                    RandomAccessFile(tmp, "rw").use { raf ->
                        raf.setLength(0L)
                        raf.write(MAGIC.toByteArray(Charsets.US_ASCII))
                        raf.writeInt(VERSION)
                        raf.writeInt(blockSize)
                        raf.writeLong(store.lengthCodeUnits)
                        raf.writeStringUtf8(meta.sampleHash)
                        raf.writeInt(blockCount)
                        for (index in 0 until blockCount) {
                            raf.writeInt(newlineCounts[index])
                            raf.writeLong(newlineOrdinals[index])
                        }
                    }
                    replaceFileAtomically(tempFile = tmp, targetFile = file)
                }
            }
        }
    }
}
