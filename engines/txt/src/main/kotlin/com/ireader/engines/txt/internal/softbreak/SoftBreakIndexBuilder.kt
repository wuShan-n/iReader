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
import com.ireader.engines.common.android.reflow.SoftBreakDecisionReasons
import com.ireader.engines.common.android.reflow.SoftBreakLineInfo
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.io.writeStringUtf8
import com.ireader.engines.common.io.writeVarLong
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.provider.ChapterDetector
import com.ireader.engines.txt.internal.store.Utf16TextStore
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object SoftBreakIndexBuilder {

    private const val MAGIC = "BRK1"
    private const val VERSION = 7
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
        val ruleConfig = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED)
        if (!files.textStore.exists()) {
            return@withContext
        }
        SoftBreakIndex.openIfValid(
            file = files.breakMap,
            meta = meta,
            profile = profile,
            rulesVersion = ruleConfig.rulesVersion
        )?.close()?.also {
            return@withContext
        }

        files.bookDir.mkdirs()
        RandomAccessFile(files.breakLock, "rw").channel.use { lockChannel ->
            lockChannel.lock().use {
                SoftBreakIndex.openIfValid(
                    file = files.breakMap,
                    meta = meta,
                    profile = profile,
                    rulesVersion = ruleConfig.rulesVersion
                )?.close()?.also {
                    return@withContext
                }

                val tmp = File(files.bookDir, "break.map.tmp")
                prepareTempFile(tmp)
                val blocks = ArrayList<SoftBreakIndex.BlockMeta>(128)

                RandomAccessFile(tmp, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(MAGIC.toByteArray(Charsets.US_ASCII))
                    raf.writeInt(VERSION)
                    raf.writeInt(BLOCK_NEWLINES)
                    raf.writeLong(meta.lengthCodeUnits)

                    val newlineCountPos = raf.filePointer
                    raf.writeLong(0L)
                    raf.writeStringUtf8(meta.sampleHash)
                    raf.writeStringUtf8(profile.storageValue)
                    raf.writeInt(ruleConfig.rulesVersion)
                    val indexOffsetPos = raf.filePointer
                    raf.writeLong(0L)

                    val offsets = LongArray(BLOCK_NEWLINES)
                    val states = ByteArray(BLOCK_NEWLINES)
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
                        raf.write(states, 0, inBlock)

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

                    fun classifyBreak(line0: SoftBreakLineInfo, line1: SoftBreakLineInfo): BreakMapState {
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
                        if (line0.length == 0 || line1.length == 0) {
                            return BreakMapState.HARD_PARAGRAPH
                        }
                        if (line0.isBoundaryTitle || line1.isBoundaryTitle) {
                            return BreakMapState.HARD_PARAGRAPH
                        }
                        if (line1.startsWithListMarker || line1.startsWithDialogueMarker) {
                            return BreakMapState.PRESERVE
                        }
                        val decision = SoftBreakClassifier.classify(
                            line0 = line0,
                            line1 = line1,
                            context = cachedContext
                        )
                        if (decision.isSoft) {
                            return chooseSoftState(line0, line1)
                        }
                        if ((decision.reasons and SoftBreakDecisionReasons.INDENT_INCREASE) != 0 ||
                            (decision.reasons and SoftBreakDecisionReasons.INDENT_SHIFT_IN_NORMAL) != 0 ||
                            (decision.reasons and SoftBreakDecisionReasons.LIST_OR_DIALOGUE_START) != 0
                        ) {
                            return BreakMapState.PRESERVE
                        }
                        if (!meta.hardWrapLikely && decision.threshold - decision.score <= 1) {
                            return BreakMapState.UNKNOWN
                        }
                        return BreakMapState.HARD_PARAGRAPH
                    }

                    data class Pending(val newlineOffset: Long, val line0: SoftBreakLineInfo)
                    var pending: Pending? = null

                    fun recordNewline(offset: Long, state: BreakMapState) {
                        offsets[inBlock] = offset
                        states[inBlock] = state.storageCode.toByte()
                        inBlock++
                        newlineCount++
                        if (inBlock >= BLOCK_NEWLINES) {
                            flushBlock()
                        }
                    }

                    Utf16TextStore(files.textStore).use { store ->
                        while (globalOffset < store.lengthCodeUnits) {
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
                                            state = classifyBreak(oldPending.line0, currentLine)
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
                                        if (lineTitle.length < MAX_TITLE_CHARS) {
                                            lineTitle.append(c)
                                        }
                                    } else if (lineTitle.isNotEmpty() && lineTitle.length < MAX_TITLE_CHARS) {
                                        lineTitle.append(' ')
                                    }
                                }
                            }
                            globalOffset += chunk.size.toLong()
                        }

                        val lastLine = finishLineIfHasData()
                        val oldPending = pending
                        if (oldPending != null) {
                            val state = when {
                                lastLine == null -> BreakMapState.HARD_PARAGRAPH
                                else -> classifyBreak(oldPending.line0, lastLine)
                            }
                            recordNewline(offset = oldPending.newlineOffset, state = state)
                        }
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

                    raf.seek(newlineCountPos)
                    raf.writeLong(newlineCount)
                    raf.seek(indexOffsetPos)
                    raf.writeLong(indexOffset)
                }

                replaceFileAtomically(tempFile = tmp, targetFile = files.breakMap)
                if (!files.breakPatch.exists()) {
                    files.breakPatch.writeText("{}")
                }
            }
        }
    }

    private fun chooseSoftState(
        line0: SoftBreakLineInfo,
        line1: SoftBreakLineInfo
    ): BreakMapState {
        val prev = line0.lastNonSpace ?: return BreakMapState.SOFT_SPACE
        val next = line1.firstNonSpace ?: return BreakMapState.SOFT_SPACE
        if (shouldJoinWithoutSpace(prev, next)) {
            return BreakMapState.SOFT_JOIN
        }
        return BreakMapState.SOFT_SPACE
    }

    private fun shouldJoinWithoutSpace(previous: Char, next: Char): Boolean {
        if (previous == '-' && next.isLetterOrDigit()) {
            return true
        }
        val previousCjk = Character.UnicodeScript.of(previous.code) in CJK_SCRIPTS
        val nextCjk = Character.UnicodeScript.of(next.code) in CJK_SCRIPTS
        if (previousCjk && nextCjk) {
            return true
        }
        return previous in NO_SPACE_AFTER || next in NO_SPACE_BEFORE
    }

    private val NO_SPACE_AFTER = setOf('（', '【', '《', '“', '"', '\'', '「', '『', '—')
    private val NO_SPACE_BEFORE = setOf('）', '】', '》', '”', '"', '\'', '、', '，', '。', '！', '？', '；', '：')
    private val CJK_SCRIPTS = setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL
    )
}
