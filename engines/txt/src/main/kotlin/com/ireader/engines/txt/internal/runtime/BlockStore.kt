package com.ireader.engines.txt.internal.runtime

import com.ireader.engines.common.cache.LruCache
import com.ireader.engines.txt.internal.locator.TextAnchor
import com.ireader.engines.txt.internal.locator.TextAnchorAffinity
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore

internal data class BlockText(
    val blockId: Int,
    val startOffset: Long,
    val endOffsetExclusive: Long,
    val rawText: String
)

internal data class LogicalParagraph(
    val startAnchor: TextAnchor,
    val endAnchor: TextAnchor,
    val projection: ProjectedTextRange
) {
    val startOffset: Long
        get() = projection.rawStartOffset

    val endOffsetExclusive: Long
        get() = projection.rawEndOffsetExclusive

    val displayText: String
        get() = projection.displayText
}

internal data class ParagraphBatch(
    val paragraphs: List<LogicalParagraph>,
    val nextAnchor: TextAnchor?
)

internal class BlockStore(
    private val store: Utf16TextStore,
    private val blockIndex: TxtBlockIndex,
    private val revision: Int,
    private val breakResolver: BreakResolver,
    blockCacheSize: Int = 12
) {
    private val blockCache = LruCache<Int, BlockText>(blockCacheSize.coerceAtLeast(4))

    fun anchorForOffset(
        offset: Long,
        affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD
    ): TextAnchor {
        return blockIndex.anchorForOffset(
            offset = offset.coerceIn(0L, store.lengthCodeUnits),
            revision = revision,
            affinity = affinity
        )
    }

    fun offsetForAnchor(anchor: TextAnchor): Long? {
        if (anchor.revision != revision) {
            return null
        }
        val safeOffset = anchor.utf16Offset.coerceIn(0L, store.lengthCodeUnits)
        val expectedBlockId = blockIndex.blockIdForOffset(safeOffset)
        if (expectedBlockId != anchor.blockId) {
            return null
        }
        return safeOffset
    }

    fun readBlock(blockId: Int): BlockText {
        blockCache[blockId]?.let { return it }
        val safeBlockId = blockId.coerceIn(0, (blockIndex.blockCount - 1).coerceAtLeast(0))
        val start = blockIndex.blockStartOffset(safeBlockId)
        val end = blockIndex.blockEndOffset(safeBlockId)
        val raw = store.readString(start, (end - start).toInt().coerceAtLeast(0))
        return BlockText(
            blockId = safeBlockId,
            startOffset = start,
            endOffsetExclusive = end,
            rawText = raw
        ).also { blockCache[safeBlockId] = it }
    }

    fun isParagraphBoundary(offset: Long): Boolean {
        if (offset <= 0L) {
            return true
        }
        val previousOffset = offset - 1L
        val previous = store.readString(previousOffset, 1).firstOrNull() ?: return false
        if (previous != '\n') {
            return false
        }
        return breakResolver.stateAt(previousOffset)?.emitsVisibleNewline != false
    }

    fun readParagraphs(startAnchor: TextAnchor, codeUnitBudget: Int): ParagraphBatch {
        val startOffset = offsetForAnchor(startAnchor) ?: return ParagraphBatch(emptyList(), null)
        if (startOffset >= store.lengthCodeUnits) {
            return ParagraphBatch(emptyList(), null)
        }
        val paragraphs = ArrayList<LogicalParagraph>(8)
        val targetOffset = (startOffset + codeUnitBudget.coerceAtLeast(1).toLong())
            .coerceAtMost(store.lengthCodeUnits)
        var cursor = startOffset
        while (cursor < store.lengthCodeUnits) {
            val end = findNextParagraphEnd(cursor)
            val projection = breakResolver.projectRange(cursor, end)
            val paragraph = LogicalParagraph(
                startAnchor = anchorForOffset(cursor, TextAnchorAffinity.FORWARD),
                endAnchor = anchorForOffset(end, TextAnchorAffinity.BACKWARD),
                projection = projection
            )
            paragraphs.add(paragraph)
            cursor = end
            if (paragraphs.isNotEmpty() && cursor >= targetOffset) {
                break
            }
        }
        val nextAnchor = if (cursor < store.lengthCodeUnits) {
            anchorForOffset(cursor, TextAnchorAffinity.FORWARD)
        } else {
            null
        }
        return ParagraphBatch(
            paragraphs = paragraphs,
            nextAnchor = nextAnchor
        )
    }

    private fun findNextParagraphEnd(startOffset: Long): Long {
        var blockId = blockIndex.blockIdForOffset(startOffset)
        while (blockId < blockIndex.blockCount) {
            val block = readBlock(blockId)
            val localStart = (startOffset - block.startOffset).toInt().coerceAtLeast(0)
            for (index in localStart until block.rawText.length) {
                if (block.rawText[index] != '\n') {
                    continue
                }
                val globalOffset = block.startOffset + index.toLong()
                val state = breakResolver.stateAt(globalOffset)
                if (state == null || state.emitsVisibleNewline) {
                    return (globalOffset + 1L).coerceAtMost(store.lengthCodeUnits)
                }
            }
            blockId++
        }
        return store.lengthCodeUnits
    }
}
