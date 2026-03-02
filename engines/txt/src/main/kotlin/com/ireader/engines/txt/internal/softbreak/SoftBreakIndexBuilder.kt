@file:Suppress("LongMethod", "NestedBlockDepth", "ReturnCount")

package com.ireader.engines.txt.internal.softbreak

import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.provider.ChapterDetector
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.txt.internal.util.writeStringUtf8
import com.ireader.engines.txt.internal.util.writeVarLong
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object SoftBreakIndexBuilder {

    private const val MAGIC = "SBX1"
    private const val VERSION = 1
    private const val BLOCK_NEWLINES = 4096
    private const val MAX_TITLE_CHARS = 80
    private const val CHUNK_CHARS = 128 * 1024

    private val strongEndPunct = setOf('。', '！', '？', '.', '!', '?', '…', ':', '：', ';', '；')
    private val detector = ChapterDetector()

    suspend fun buildIfNeeded(
        files: TxtBookFiles,
        meta: TxtMeta,
        ioDispatcher: CoroutineDispatcher
    ) = withContext(ioDispatcher) {
        if (!meta.hardWrapLikely || !files.contentU16.exists()) {
            return@withContext
        }
        SoftBreakIndex.openIfValid(files.softBreakIdx, meta)?.close()?.also {
            return@withContext
        }

        files.bookDir.mkdirs()
        RandomAccessFile(files.softBreakLock, "rw").channel.use { lockChannel ->
            lockChannel.lock().use {
                SoftBreakIndex.openIfValid(files.softBreakIdx, meta)?.close()?.also {
                    return@withContext
                }

                val tmp = File(files.bookDir, "softbreak.idx.tmp")
                if (tmp.exists()) {
                    tmp.delete()
                }
                val blocks = ArrayList<SoftBreakIndex.BlockMeta>(128)

                RandomAccessFile(tmp, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(MAGIC.toByteArray(Charsets.US_ASCII))
                    raf.writeInt(VERSION)
                    raf.writeInt(BLOCK_NEWLINES)
                    raf.writeLong(meta.lengthChars)

                    val newlineCountPos = raf.filePointer
                    raf.writeLong(0L)
                    raf.writeStringUtf8(meta.sampleHash)
                    val indexOffsetPos = raf.filePointer
                    raf.writeLong(0L)

                    val offsets = LongArray(BLOCK_NEWLINES)
                    val flags = BooleanArray(BLOCK_NEWLINES)
                    var inBlock = 0

                    fun flushBlock() {
                        if (inBlock <= 0) {
                            return
                        }
                        val filePos = raf.filePointer
                        val first = offsets[0]
                        val last = offsets[inBlock - 1]

                        raf.writeInt(inBlock)
                        raf.writeLong(first)

                        val flagsLen = (inBlock + 7) / 8
                        val packedFlags = ByteArray(flagsLen)
                        for (i in 0 until inBlock) {
                            if (flags[i]) {
                                packedFlags[i ushr 3] =
                                    (packedFlags[i ushr 3].toInt() or (1 shl (i and 7))).toByte()
                            }
                        }
                        raf.write(packedFlags)

                        var prev = first
                        for (i in 1 until inBlock) {
                            val delta = offsets[i] - prev
                            raf.writeVarLong(delta.coerceAtLeast(0L))
                            prev = offsets[i]
                        }

                        blocks.add(
                            SoftBreakIndex.BlockMeta(
                                filePos = filePos,
                                firstOffset = first,
                                lastOffset = last,
                                count = inBlock
                            )
                        )
                        inBlock = 0
                    }

                    var newlineCount = 0L
                    var globalOffset = 0L

                    var lineLength = 0
                    var leadingSpaces = 0
                    var seenNonSpace = false
                    var firstNonSpace: Char? = null
                    var lastNonSpace: Char? = null
                    val lineTitle = StringBuilder(MAX_TITLE_CHARS)

                    var nonEmptyLineCount = 0L
                    var nonEmptyLineLengthSum = 0L

                    data class LineInfo(
                        val len: Int,
                        val leadingSpaces: Int,
                        val firstNonSpace: Char?,
                        val lastNonSpace: Char?,
                        val isTitle: Boolean,
                        val startsWithBullet: Boolean
                    )

                    fun estimateTypicalLineLength(): Int {
                        if (nonEmptyLineCount == 0L) {
                            return 72
                        }
                        val avg = (nonEmptyLineLengthSum / nonEmptyLineCount).toInt()
                        return avg.coerceIn(40, 140)
                    }

                    fun resetLineState() {
                        lineLength = 0
                        leadingSpaces = 0
                        seenNonSpace = false
                        firstNonSpace = null
                        lastNonSpace = null
                        lineTitle.setLength(0)
                    }

                    fun finishLine(): LineInfo {
                        val titleText = lineTitle.toString().trim()
                        val isTitle = titleText.isNotEmpty() && detector.isChapterTitle(titleText)
                        val startsBullet = firstNonSpace == '-' || firstNonSpace == '*' || firstNonSpace == '•'
                        val info = LineInfo(
                            len = lineLength,
                            leadingSpaces = leadingSpaces,
                            firstNonSpace = firstNonSpace,
                            lastNonSpace = lastNonSpace,
                            isTitle = isTitle,
                            startsWithBullet = startsBullet
                        )
                        if (lineLength > 0) {
                            nonEmptyLineCount++
                            nonEmptyLineLengthSum += lineLength.toLong()
                        }
                        resetLineState()
                        return info
                    }

                    fun finishLineIfHasData(): LineInfo? {
                        return if (lineLength > 0 || firstNonSpace != null || lastNonSpace != null) {
                            finishLine()
                        } else {
                            null
                        }
                    }

                    fun isSoftBreak(line0: LineInfo, line1: LineInfo): Boolean {
                        val typical = estimateTypicalLineLength()
                        if (line0.len == 0 || line1.len == 0) {
                            return false
                        }
                        if (line0.isTitle || line1.isTitle) {
                            return false
                        }
                        if (line1.leadingSpaces >= 2 || line1.startsWithBullet) {
                            return false
                        }
                        if (line0.len < (typical * 0.60).toInt()) {
                            return false
                        }
                        if (line1.len < (typical * 0.30).toInt()) {
                            return false
                        }
                        val last = line0.lastNonSpace
                        if (last != null && strongEndPunct.contains(last)) {
                            return false
                        }
                        return true
                    }

                    data class Pending(val newlineOffset: Long, val line0: LineInfo)
                    var pending: Pending? = null

                    fun recordNewline(offset: Long, isSoft: Boolean) {
                        offsets[inBlock] = offset
                        flags[inBlock] = isSoft
                        inBlock++
                        newlineCount++
                        if (inBlock >= BLOCK_NEWLINES) {
                            flushBlock()
                        }
                    }

                    Utf16TextStore(files.contentU16).use { store ->
                        while (globalOffset < store.lengthChars) {
                            coroutineContext.ensureActive()
                            val chunk = store.readChars(globalOffset, CHUNK_CHARS)
                            if (chunk.isEmpty()) {
                                break
                            }
                            for (c in chunk) {
                                coroutineContext.ensureActive()
                                if (c == '\n') {
                                    val currentLine = finishLine()
                                    val oldPending = pending
                                    if (oldPending != null) {
                                        recordNewline(
                                            offset = oldPending.newlineOffset,
                                            isSoft = isSoftBreak(oldPending.line0, currentLine)
                                        )
                                    }
                                    pending = Pending(newlineOffset = globalOffset, line0 = currentLine)
                                } else {
                                    lineLength++
                                    if (!seenNonSpace) {
                                        if (c == ' ' || c == '\t' || c == '\u3000') {
                                            leadingSpaces++
                                        } else {
                                            seenNonSpace = true
                                            firstNonSpace = c
                                        }
                                    }
                                    if (!c.isWhitespace()) {
                                        lastNonSpace = c
                                    }
                                    if (lineTitle.length < MAX_TITLE_CHARS) {
                                        lineTitle.append(c)
                                    }
                                }
                                globalOffset++
                            }
                        }
                    }

                    val trailingLine = finishLineIfHasData()
                    val pendingLine = pending
                    if (pendingLine != null) {
                        val soft = trailingLine != null && isSoftBreak(pendingLine.line0, trailingLine)
                        recordNewline(pendingLine.newlineOffset, soft)
                    }
                    flushBlock()

                    val indexOffset = raf.filePointer
                    raf.writeInt(blocks.size)
                    for (block in blocks) {
                        raf.writeLong(block.filePos)
                        raf.writeLong(block.firstOffset)
                        raf.writeLong(block.lastOffset)
                        raf.writeInt(block.count)
                    }

                    raf.seek(indexOffsetPos)
                    raf.writeLong(indexOffset)
                    raf.seek(newlineCountPos)
                    raf.writeLong(newlineCount)
                }

                if (files.softBreakIdx.exists()) {
                    files.softBreakIdx.delete()
                }
                if (!tmp.renameTo(files.softBreakIdx)) {
                    tmp.copyTo(files.softBreakIdx, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }
}
