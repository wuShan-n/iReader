package com.ireader.engines.txt.internal.locator

import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlin.math.abs

internal object TxtStableLocatorCodec {
    private const val QUOTE_RADIUS = 12
    private const val CONTEXT_RADIUS = 24
    private const val SEARCH_RADIUS = 8_192L

    fun locatorForOffset(
        offset: Long,
        blockIndex: TxtBlockIndex,
        contentFingerprint: String,
        maxOffset: Long,
        projectionEngine: TextProjectionEngine,
        extras: Map<String, String> = emptyMap()
    ): Locator {
        val safeOffset = offset.coerceIn(0L, maxOffset.coerceAtLeast(0L))
        val blockId = blockIndex.blockIdForOffset(safeOffset)
        val intraBlockOffset = (safeOffset - blockIndex.blockStartOffset(blockId)).toInt().coerceAtLeast(0)
        val progression = if (maxOffset == 0L) {
            0.0
        } else {
            safeOffset.toDouble() / maxOffset.toDouble()
        }.coerceIn(0.0, 1.0)
        val quote = quoteSnapshotAt(
            offset = safeOffset,
            maxOffset = maxOffset,
            projectionEngine = projectionEngine
        )
        return Locator(
            scheme = LocatorSchemes.TXT_STABLE_ANCHOR,
            value = "$blockId:$intraBlockOffset",
            extras = extras + mapOf(
                LocatorExtraKeys.PROGRESSION to "%.6f".format(java.util.Locale.US, progression),
                LocatorExtraKeys.CONTENT_FINGERPRINT to contentFingerprint,
                LocatorExtraKeys.TEXT_QUOTE to quote.textQuote,
                LocatorExtraKeys.CONTEXT_BEFORE to quote.contextBefore,
                LocatorExtraKeys.CONTEXT_AFTER to quote.contextAfter
            )
        )
    }

    fun rangeForOffsets(
        startOffset: Long,
        endOffset: Long,
        blockIndex: TxtBlockIndex,
        contentFingerprint: String,
        maxOffset: Long,
        projectionEngine: TextProjectionEngine
    ): LocatorRange {
        return LocatorRange(
            start = locatorForOffset(
                offset = startOffset,
                blockIndex = blockIndex,
                contentFingerprint = contentFingerprint,
                maxOffset = maxOffset,
                projectionEngine = projectionEngine
            ),
            end = locatorForOffset(
                offset = endOffset,
                blockIndex = blockIndex,
                contentFingerprint = contentFingerprint,
                maxOffset = maxOffset,
                projectionEngine = projectionEngine
            )
        )
    }

    fun parseOffset(
        locator: Locator,
        blockIndex: TxtBlockIndex,
        contentFingerprint: String,
        maxOffset: Long,
        projectionEngine: TextProjectionEngine
    ): Long? {
        val pointer = parsePointer(locator) ?: return null
        val baseCandidate = candidateOffset(pointer, blockIndex, maxOffset)
        val fingerprintMatches = locator.extras[LocatorExtraKeys.CONTENT_FINGERPRINT] == contentFingerprint
        val candidate = if (fingerprintMatches) {
            baseCandidate
        } else {
            progressionFallback(locator, maxOffset) ?: baseCandidate
        }
        val textQuote = locator.extras[LocatorExtraKeys.TEXT_QUOTE].orEmpty()
        if (textQuote.isBlank()) {
            return candidate
        }
        val scanStart = (candidate - SEARCH_RADIUS).coerceAtLeast(0L)
        val scanEnd = (candidate + SEARCH_RADIUS).coerceAtMost(maxOffset.coerceAtLeast(0L))
        val projection = projectionEngine.projectRange(
            startOffset = scanStart,
            endOffsetExclusive = scanEnd
        )
        val displayText = projection.displayText
        if (displayText.isEmpty()) {
            return candidate
        }
        val contextBefore = locator.extras[LocatorExtraKeys.CONTEXT_BEFORE].orEmpty()
        val contextAfter = locator.extras[LocatorExtraKeys.CONTEXT_AFTER].orEmpty()
        var bestOffset: Long? = null
        var bestDistance = Long.MAX_VALUE
        var bestContextScore = Int.MIN_VALUE
        var searchIndex = 0
        while (true) {
            val matchIndex = displayText.indexOf(textQuote, startIndex = searchIndex)
            if (matchIndex < 0) {
                break
            }
            val resolvedOffset = projection.projectedBoundaryToRawOffsets
                .getOrElse(matchIndex) { candidate }
                .coerceIn(0L, maxOffset.coerceAtLeast(0L))
            val distance = abs(resolvedOffset - candidate)
            val contextScore = contextScore(
                displayText = displayText,
                matchIndex = matchIndex,
                textQuoteLength = textQuote.length,
                contextBefore = contextBefore,
                contextAfter = contextAfter
            )
            if (distance < bestDistance ||
                (distance == bestDistance && contextScore > bestContextScore)
            ) {
                bestDistance = distance
                bestContextScore = contextScore
                bestOffset = resolvedOffset
            }
            searchIndex = matchIndex + 1
        }
        return bestOffset ?: candidate
    }

    private fun parsePointer(locator: Locator): StablePointer? {
        if (locator.scheme != LocatorSchemes.TXT_STABLE_ANCHOR) {
            return null
        }
        val parts = locator.value.split(':')
        if (parts.size != 2) {
            return null
        }
        val blockId = parts[0].toIntOrNull() ?: return null
        val intraBlockOffset = parts[1].toIntOrNull() ?: return null
        if (blockId < 0 || intraBlockOffset < 0) {
            return null
        }
        return StablePointer(blockId = blockId, intraBlockOffset = intraBlockOffset)
    }

    private fun candidateOffset(
        pointer: StablePointer,
        blockIndex: TxtBlockIndex,
        maxOffset: Long
    ): Long {
        val blockStart = blockIndex.blockStartOffset(pointer.blockId)
        return (blockStart + pointer.intraBlockOffset.toLong()).coerceIn(0L, maxOffset.coerceAtLeast(0L))
    }

    private fun progressionFallback(locator: Locator, maxOffset: Long): Long? {
        val percent = locator.extras[LocatorExtraKeys.PROGRESSION]?.toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)
            ?: return null
        return (maxOffset.coerceAtLeast(0L) * percent).toLong()
    }

    private fun quoteSnapshotAt(
        offset: Long,
        maxOffset: Long,
        projectionEngine: TextProjectionEngine
    ): QuoteSnapshot {
        val safeOffset = offset.coerceIn(0L, maxOffset.coerceAtLeast(0L))
        val start = (safeOffset - CONTEXT_RADIUS.toLong()).coerceAtLeast(0L)
        val end = (
            safeOffset +
                CONTEXT_RADIUS.toLong() +
                QUOTE_RADIUS.toLong()
            ).coerceAtMost(maxOffset.coerceAtLeast(0L))
        val projection = projectionEngine.projectRange(startOffset = start, endOffsetExclusive = end)
        val displayText = projection.displayText
        if (displayText.isEmpty()) {
            return QuoteSnapshot("", "", "")
        }
        val localBoundary = (safeOffset - start).toInt().coerceIn(0, projection.rawBoundaryToProjectedIndex.lastIndex)
        val anchor = projection.rawBoundaryToProjectedIndex[localBoundary].coerceIn(0, displayText.length)
        val quoteStart = anchor.coerceAtMost(displayText.length)
        val quoteEnd = (quoteStart + QUOTE_RADIUS).coerceAtMost(displayText.length)
        val beforeStart = (quoteStart - CONTEXT_RADIUS).coerceAtLeast(0)
        val afterEnd = (quoteEnd + CONTEXT_RADIUS).coerceAtMost(displayText.length)
        return QuoteSnapshot(
            textQuote = displayText.substring(quoteStart, quoteEnd),
            contextBefore = displayText.substring(beforeStart, quoteStart),
            contextAfter = displayText.substring(quoteEnd, afterEnd)
        )
    }

    private fun contextScore(
        displayText: String,
        matchIndex: Int,
        textQuoteLength: Int,
        contextBefore: String,
        contextAfter: String
    ): Int {
        var score = 0
        if (contextBefore.isNotEmpty()) {
            val actualBefore = displayText.substring(
                startIndex = (matchIndex - contextBefore.length).coerceAtLeast(0),
                endIndex = matchIndex
            )
            if (actualBefore.endsWith(contextBefore)) {
                score += contextBefore.length * 4
            }
        }
        if (contextAfter.isNotEmpty()) {
            val afterStart = (matchIndex + textQuoteLength).coerceAtMost(displayText.length)
            val afterEnd = (afterStart + contextAfter.length).coerceAtMost(displayText.length)
            val actualAfter = displayText.substring(afterStart, afterEnd)
            if (actualAfter.startsWith(contextAfter)) {
                score += contextAfter.length * 4
            }
        }
        return score
    }

    private data class StablePointer(
        val blockId: Int,
        val intraBlockOffset: Int
    )

    private data class QuoteSnapshot(
        val textQuote: String,
        val contextBefore: String,
        val contextAfter: String
    )
}
