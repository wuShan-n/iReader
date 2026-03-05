@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber",
    "NestedBlockDepth",
    "ReturnCount"
)

package com.ireader.engines.txt.internal.softbreak

import com.ireader.engines.common.android.reflow.SoftBreakClassifier
import com.ireader.engines.common.android.reflow.SoftBreakClassifierContext
import com.ireader.engines.common.android.reflow.SoftBreakLineInfo
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.provider.ChapterDetector
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.io.writeStringUtf8
import com.ireader.engines.common.io.writeVarLong
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object SoftBreakIndexBuilder {

    private const val MAGIC = "SBX1"
    private const val VERSION = 6
    private const val BLOCK_NEWLINES = 4096
    private const val MAX_TITLE_CHARS = 80
    private const val CHUNK_CHARS = 128 * 1024
    private const val ENSURE_ACTIVE_MASK = 0xFFF
    private const val TYPICAL_UPDATE_INTERVAL = 256

    private val detector = ChapterDetector()

    suspend fun buildIfNeeded(
        files: TxtBookFiles,
        meta: TxtMeta,
        ioDispatcher: CoroutineDispatcher,
        profile: SoftBreakTuningProfile
    ) = withContext(ioDispatcher) {
        val ruleConfig = SoftBreakRuleConfig.forProfile(profile)
        if (!files.contentU16.exists()) {
            return@withContext
        }
        SoftBreakIndex.openIfValid(
            file = files.softBreakIdx,
            meta = meta,
            profile = profile,
            rulesVersion = ruleConfig.rulesVersion
        )?.close()?.also {
            return@withContext
        }

        files.bookDir.mkdirs()
        RandomAccessFile(files.softBreakLock, "rw").channel.use { lockChannel ->
            lockChannel.lock().use {
                SoftBreakIndex.openIfValid(
                    file = files.softBreakIdx,
                    meta = meta,
                    profile = profile,
                    rulesVersion = ruleConfig.rulesVersion
                )?.close()?.also {
                    return@withContext
                }

                val tmp = File(files.bookDir, "softbreak.idx.tmp")
                prepareTempFile(tmp)
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
                    raf.writeStringUtf8(profile.storageValue)
                    raf.writeInt(ruleConfig.rulesVersion)
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
                    var secondNonSpace: Char? = null
                    var lastNonSpace: Char? = null
                    val lineTitle = StringBuilder(MAX_TITLE_CHARS)

                    var nonEmptyLineCount = 0L
                    var nonEmptyLineLengthSum = 0L

                    fun estimateTypicalLineLength(): Int {
                        if (nonEmptyLineCount == 0L) {
                            return 72
                        }
                        val avg = (nonEmptyLineLengthSum / nonEmptyLineCount).toInt()
                        val minTypical = if (meta.hardWrapLikely) {
                            ruleConfig.minTypicalHardWrap
                        } else {
                            ruleConfig.minTypicalNormal
                        }
                        return avg.coerceIn(minTypical, ruleConfig.maxTypical)
                    }

                    fun resetLineState() {
                        lineLength = 0
                        leadingSpaces = 0
                        seenNonSpace = false
                        firstNonSpace = null
                        secondNonSpace = null
                        lastNonSpace = null
                        lineTitle.setLength(0)
                    }

                    fun finishLine(): SoftBreakLineInfo {
                        val titleText = lineTitle.toString().trim()
                        val isBoundary = titleText.isNotEmpty() && detector.isChapterBoundaryTitle(titleText)
                        val info = SoftBreakLineInfo(
                            length = lineLength,
                            leadingSpaces = leadingSpaces,
                            firstNonSpace = firstNonSpace,
                            secondNonSpace = secondNonSpace,
                            lastNonSpace = lastNonSpace,
                            isBoundaryTitle = isBoundary,
                            startsWithListMarker = SoftBreakClassifier.detectListMarker(firstNonSpace, secondNonSpace),
                            startsWithDialogueMarker = SoftBreakClassifier.detectDialogueMarker(firstNonSpace)
                        )
                        if (lineLength > 0) {
                            nonEmptyLineCount++
                            nonEmptyLineLengthSum += lineLength.toLong()
                        }
                        resetLineState()
                        return info
                    }

                    fun finishLineIfHasData(): SoftBreakLineInfo? {
                        return if (lineLength > 0 || firstNonSpace != null || lastNonSpace != null) {
                            finishLine()
                        } else {
                            null
                        }
                    }

                    var cachedTypicalLineLength = 72
                    var cachedContext = SoftBreakClassifierContext(
                        typicalLineLength = cachedTypicalLineLength,
                        hardWrapLikely = meta.hardWrapLikely,
                        rules = ruleConfig
                    )
                    var typicalUpdateCountdown = 0

                    fun isSoftBreak(line0: SoftBreakLineInfo, line1: SoftBreakLineInfo): Boolean {
                        if (typicalUpdateCountdown <= 0) {
                            val typical = estimateTypicalLineLength()
                            if (typical != cachedTypicalLineLength) {
                                cachedTypicalLineLength = typical
                                cachedContext = SoftBreakClassifierContext(
                                    typicalLineLength = cachedTypicalLineLength,
                                    hardWrapLikely = meta.hardWrapLikely,
                                    rules = ruleConfig
                                )
                            }
                            typicalUpdateCountdown = TYPICAL_UPDATE_INTERVAL
                        } else {
                            typicalUpdateCountdown--
                        }
                        return SoftBreakClassifier.classify(
                            line0 = line0,
                            line1 = line1,
                            context = cachedContext
                        ).isSoft
                    }

                    data class Pending(val newlineOffset: Long, val line0: SoftBreakLineInfo)
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
                            val baseOffset = globalOffset
                            for (i in chunk.indices) {
                                if ((i and ENSURE_ACTIVE_MASK) == 0) {
                                    coroutineContext.ensureActive()
                                }
                                val c = chunk[i]
                                val offset = baseOffset + i.toLong()
                                if (c == '\n') {
                                    val currentLine = finishLine()
                                    val oldPending = pending
                                    if (oldPending != null) {
                                        recordNewline(
                                            offset = oldPending.newlineOffset,
                                            isSoft = isSoftBreak(oldPending.line0, currentLine)
                                        )
                                    }
                                    pending = Pending(newlineOffset = offset, line0 = currentLine)
                                } else {
                                    lineLength++
                                    if (!seenNonSpace) {
                                        if (c == ' ' || c == '\t' || c == '\u3000') {
                                            leadingSpaces++
                                        } else {
                                            seenNonSpace = true
                                            firstNonSpace = c
                                        }
                                    } else if (secondNonSpace == null && !c.isWhitespace()) {
                                        secondNonSpace = c
                                    }
                                    if (!c.isWhitespace()) {
                                        lastNonSpace = c
                                    }
                                    if (lineTitle.length < MAX_TITLE_CHARS) {
                                        lineTitle.append(c)
                                    }
                                }
                            }
                            globalOffset += chunk.size.toLong()
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

                replaceFileAtomically(tempFile = tmp, targetFile = files.softBreakIdx)
            }
        }
    }
}
