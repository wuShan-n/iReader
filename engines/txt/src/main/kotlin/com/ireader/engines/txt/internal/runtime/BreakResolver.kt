package com.ireader.engines.txt.internal.runtime

import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.softbreak.BreakMapState
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import java.util.concurrent.atomic.AtomicReference

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

internal class BreakResolver(
    private val store: Utf16TextStore,
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    private val breakIndex: SoftBreakIndex
) {
    private val patchStore = BreakPatchStore(files = files, sampleHash = meta.sampleHash)
    private val patchRef = AtomicReference<Map<Long, BreakMapState>?>(null)

    fun stateAt(offset: Long): BreakMapState? {
        if (offset < 0L || offset >= store.lengthCodeUnits) {
            return null
        }
        val patch = patchMap()
        return patch[offset] ?: breakIndex.stateAt(offset)
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

        val states = HashMap<Long, BreakMapState>(raw.count { it == '\n' }.coerceAtLeast(4))
        breakIndex.forEachStateInRange(safeStart, safeEnd) { offset, state ->
            states[offset] = patchMap()[offset] ?: state
        }
        if (states.isEmpty()) {
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
            val state = if (char == '\n') states[globalOffset] else null
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

    private fun patchMap(): Map<Long, BreakMapState> {
        val cached = patchRef.get()
        if (cached != null) {
            return cached
        }
        val loaded = loadPatch()
        patchRef.compareAndSet(null, loaded)
        return patchRef.get() ?: loaded
    }

    private fun loadPatch(): Map<Long, BreakMapState> {
        return patchStore.read()
    }
}
