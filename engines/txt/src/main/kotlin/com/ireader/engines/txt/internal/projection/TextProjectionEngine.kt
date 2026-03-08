package com.ireader.engines.txt.internal.projection

import com.ireader.engines.common.android.reflow.SoftBreakClassifier
import com.ireader.engines.common.android.reflow.SoftBreakClassifierContext
import com.ireader.engines.common.android.reflow.SoftBreakLineInfo
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.common.cache.LruCache
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.provider.ChapterDetector
import com.ireader.engines.txt.internal.runtime.BreakPatchStore
import com.ireader.engines.txt.internal.softbreak.BreakMapState
import com.ireader.engines.txt.internal.softbreak.SoftBreakDecisionSupport
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

internal data class ProjectedTextRange(
    val rawStartOffset: Long,
    val rawEndOffsetExclusive: Long,
    val rawText: String,
    val displayText: String,
    val projectedBoundaryToRawOffsets: LongArray,
    val rawBoundaryToProjectedIndex: IntArray
) {
    companion object {
        fun identity(startOffset: Long, rawText: String): ProjectedTextRange {
            val projectedToRaw = LongArray(rawText.length + 1)
            val rawToProjected = IntArray(rawText.length + 1)
            for (index in 0..rawText.length) {
                projectedToRaw[index] = startOffset + index.toLong()
                rawToProjected[index] = index
            }
            return ProjectedTextRange(
                rawStartOffset = startOffset,
                rawEndOffsetExclusive = startOffset + rawText.length.toLong(),
                rawText = rawText,
                displayText = rawText,
                projectedBoundaryToRawOffsets = projectedToRaw,
                rawBoundaryToProjectedIndex = rawToProjected
            )
        }
    }
}

internal class TextProjectionEngine(
    private val store: Utf16TextStore,
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    breakIndex: SoftBreakIndex?
) {
    private val patchStore = BreakPatchStore(files = files, sampleHash = meta.sampleHash)
    private val patchRef = AtomicReference<Map<Long, BreakMapState>?>(null)
    private val breakIndexRef = AtomicReference(breakIndex)
    private val detector = ChapterDetector()
    private val ruleConfig = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED)
    private val classifierContext = SoftBreakClassifierContext(
        typicalLineLength = meta.typicalLineLength.coerceIn(
            if (meta.hardWrapLikely) {
                ruleConfig.minTypicalHardWrap
            } else {
                ruleConfig.minTypicalNormal
            },
            ruleConfig.maxTypical
        ),
        hardWrapLikely = meta.hardWrapLikely,
        rules = ruleConfig
    )
    private val localWindowCache = LruCache<LocalWindowKey, Map<Long, BreakMapState>>(16)
    private val localWindowCacheLock = Any()

    fun hasIndexedBreaks(): Boolean = breakIndexRef.get() != null

    fun attachIndex(index: SoftBreakIndex) {
        val previous = breakIndexRef.getAndSet(index)
        if (previous === index) {
            return
        }
        runCatching { previous?.close() }
        synchronized(localWindowCacheLock) {
            localWindowCache.clear()
        }
    }

    fun close() {
        runCatching { breakIndexRef.getAndSet(null)?.close() }
    }

    fun stateAt(offset: Long): BreakMapState? {
        if (offset < 0L || offset >= store.lengthCodeUnits) {
            return null
        }
        val patch = patchMap()
        patch[offset]?.let { return it }
        breakIndexRef.get()?.stateAt(offset)?.let { return it }
        if (store.readString(offset, 1).firstOrNull() != '\n') {
            return null
        }
        return localStatesInRange(offset, offset + 1L)[offset]
    }

    fun patch(offset: Long, state: BreakMapState) {
        if (offset < 0L || offset >= store.lengthCodeUnits) {
            return
        }
        patchEntries(mapOf(offset to state))
    }

    fun patchEntries(entries: Map<Long, BreakMapState>) {
        if (entries.isEmpty()) {
            return
        }
        val sanitized = buildMap {
            entries.forEach { (offset, state) ->
                if (offset in 0 until store.lengthCodeUnits) {
                    put(offset, state)
                }
            }
        }
        if (sanitized.isEmpty()) {
            return
        }
        while (true) {
            val current = patchMap()
            val merged = LinkedHashMap<Long, BreakMapState>(current.size + sanitized.size)
            merged.putAll(current)
            merged.putAll(sanitized)
            patchStore.write(merged)
            if (patchRef.compareAndSet(current, merged)) {
                return
            }
        }
    }

    fun clearPatches() {
        patchStore.clear()
        patchRef.set(emptyMap())
    }

    fun projectRange(startOffset: Long, endOffsetExclusive: Long): ProjectedTextRange {
        val safeStart = startOffset.coerceIn(0L, store.lengthCodeUnits)
        val safeEnd = endOffsetExclusive.coerceIn(safeStart, store.lengthCodeUnits)
        val length = (safeEnd - safeStart).toInt().coerceAtLeast(0)
        val raw = store.readString(safeStart, length)
        if (raw.isEmpty() || raw.indexOf('\n') < 0) {
            return ProjectedTextRange.identity(safeStart, raw)
        }

        val baseStates = breakIndexRef.get()?.let {
            indexedStatesInRange(startOffset = safeStart, endOffsetExclusive = safeEnd)
        } ?: localStatesInRange(startOffset = safeStart, endOffsetExclusive = safeEnd)
        val patch = patchMap()
        val hasPatchInRange = patch.keys.any { it in safeStart until safeEnd }
        if (baseStates.isEmpty() && !hasPatchInRange) {
            return ProjectedTextRange.identity(safeStart, raw)
        }

        val display = StringBuilder(raw.length)
        val projectedToRaw = LongArray(raw.length + 1)
        val rawToProjected = IntArray(raw.length + 1)
        var projectedLength = 0
        projectedToRaw[0] = safeStart
        rawToProjected[0] = 0

        raw.forEachIndexed { rawIndex, char ->
            val globalOffset = safeStart + rawIndex.toLong()
            val state = if (char == '\n') patch[globalOffset] ?: baseStates[globalOffset] else null
            when (state) {
                BreakMapState.SOFT_JOIN -> Unit
                BreakMapState.SOFT_SPACE -> {
                    display.append(' ')
                    projectedLength++
                    projectedToRaw[projectedLength] = globalOffset + 1L
                }
                BreakMapState.HARD_PARAGRAPH,
                BreakMapState.PRESERVE,
                BreakMapState.UNKNOWN,
                null -> {
                    display.append(char)
                    projectedLength++
                    projectedToRaw[projectedLength] = globalOffset + 1L
                }
            }
            rawToProjected[rawIndex + 1] = projectedLength
            if (projectedToRaw[projectedLength] == 0L) {
                projectedToRaw[projectedLength] = globalOffset + 1L
            }
        }
        if (projectedLength == 0) {
            projectedToRaw[0] = safeStart
        }
        return ProjectedTextRange(
            rawStartOffset = safeStart,
            rawEndOffsetExclusive = safeEnd,
            rawText = raw,
            displayText = display.toString(),
            projectedBoundaryToRawOffsets = projectedToRaw.copyOf(projectedLength + 1),
            rawBoundaryToProjectedIndex = rawToProjected
        )
    }

    private fun indexedStatesInRange(
        startOffset: Long,
        endOffsetExclusive: Long
    ): Map<Long, BreakMapState> {
        val index = breakIndexRef.get() ?: return emptyMap()
        val states = HashMap<Long, BreakMapState>(8)
        index.forEachStateInRange(startOffset, endOffsetExclusive) { offset, state ->
            states[offset] = state
        }
        return states
    }

    private fun localStatesInRange(
        startOffset: Long,
        endOffsetExclusive: Long
    ): Map<Long, BreakMapState> {
        val expandedStart = expandWindowStart(startOffset)
        val expandedEnd = expandWindowEnd(endOffsetExclusive)
        if (expandedEnd <= expandedStart) {
            return emptyMap()
        }
        val key = LocalWindowKey(expandedStart, expandedEnd)
        synchronized(localWindowCacheLock) {
            localWindowCache[key]?.let { cached ->
                return cached.filterKeys { it in startOffset until endOffsetExclusive }
            }
        }

        val computed = analyzeWindow(
            startOffset = expandedStart,
            endOffsetExclusive = expandedEnd
        )
        synchronized(localWindowCacheLock) {
            localWindowCache[key] = computed
        }
        return computed.filterKeys { it in startOffset until endOffsetExclusive }
    }

    private fun analyzeWindow(
        startOffset: Long,
        endOffsetExclusive: Long
    ): Map<Long, BreakMapState> {
        val length = (endOffsetExclusive - startOffset).toInt().coerceAtLeast(0)
        if (length <= 0) {
            return emptyMap()
        }
        val raw = store.readString(startOffset, length)
        if (raw.isEmpty() || raw.indexOf('\n') < 0) {
            return emptyMap()
        }

        val out = LinkedHashMap<Long, BreakMapState>()
        var lineLength = 0
        var leadingSpaces = 0
        var seenNonSpace = false
        var firstNonSpace: Char? = null
        var secondNonSpace: Char? = null
        var lastNonSpace: Char? = null
        val lineTitle = StringBuilder(MAX_TITLE_CHARS)

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

        data class Pending(val newlineOffset: Long, val line0: SoftBreakLineInfo)

        var pending: Pending? = null
        raw.forEachIndexed { index, char ->
            val globalOffset = startOffset + index.toLong()
            if (char == '\n') {
                val currentLine = finishLine()
                val oldPending = pending
                if (oldPending != null) {
                    out[oldPending.newlineOffset] = SoftBreakDecisionSupport.classifyBreak(
                        line0 = oldPending.line0,
                        line1 = currentLine,
                        context = classifierContext,
                        hardWrapLikely = meta.hardWrapLikely
                    )
                }
                pending = Pending(globalOffset, currentLine)
            } else {
                lineLength++
                if (!seenNonSpace) {
                    if (char == ' ' || char == '\t' || char == '\u3000') {
                        leadingSpaces++
                    } else {
                        seenNonSpace = true
                        firstNonSpace = char
                    }
                } else if (secondNonSpace == null && !char.isWhitespace()) {
                    secondNonSpace = char
                }
                if (!char.isWhitespace()) {
                    lastNonSpace = char
                    if (lineTitle.length < MAX_TITLE_CHARS) {
                        lineTitle.append(char)
                    }
                } else if (lineTitle.isNotEmpty() && lineTitle.length < MAX_TITLE_CHARS) {
                    lineTitle.append(' ')
                }
            }
        }

        val lastLine = finishLineIfHasData()
        val oldPending = pending
        if (oldPending != null) {
            out[oldPending.newlineOffset] = if (lastLine == null) {
                BreakMapState.HARD_PARAGRAPH
            } else {
                SoftBreakDecisionSupport.classifyBreak(
                    line0 = oldPending.line0,
                    line1 = lastLine,
                    context = classifierContext,
                    hardWrapLikely = meta.hardWrapLikely
                )
            }
        }
        return out
    }

    private fun expandWindowStart(offset: Long): Long {
        var windowEnd = offset.coerceIn(0L, store.lengthCodeUnits)
        while (windowEnd > 0L) {
            val chunkStart = (windowEnd - WINDOW_SCAN_CHARS).coerceAtLeast(0L)
            val chunk = store.readString(chunkStart, (windowEnd - chunkStart).toInt().coerceAtLeast(0))
            val newlineIndex = chunk.lastIndexOf('\n')
            if (newlineIndex >= 0) {
                return chunkStart + newlineIndex + 1L
            }
            windowEnd = chunkStart
        }
        return 0L
    }

    private fun expandWindowEnd(offset: Long): Long {
        var windowStart = offset.coerceIn(0L, store.lengthCodeUnits)
        while (windowStart < store.lengthCodeUnits) {
            val chunkSize = min(WINDOW_SCAN_CHARS.toLong(), store.lengthCodeUnits - windowStart)
                .toInt()
                .coerceAtLeast(0)
            if (chunkSize <= 0) {
                break
            }
            val chunk = store.readString(windowStart, chunkSize)
            val newlineIndex = chunk.indexOf('\n')
            if (newlineIndex >= 0) {
                return windowStart + newlineIndex + 1L
            }
            windowStart += chunkSize.toLong()
        }
        return store.lengthCodeUnits
    }

    private fun patchMap(): Map<Long, BreakMapState> {
        val cached = patchRef.get()
        if (cached != null) {
            return cached
        }
        val loaded = patchStore.read()
        patchRef.compareAndSet(null, loaded)
        return patchRef.get() ?: loaded
    }

    private data class LocalWindowKey(
        val startOffset: Long,
        val endOffsetExclusive: Long
    )

    private companion object {
        private const val MAX_TITLE_CHARS = 80
        private const val WINDOW_SCAN_CHARS = 4 * 1024
    }
}
