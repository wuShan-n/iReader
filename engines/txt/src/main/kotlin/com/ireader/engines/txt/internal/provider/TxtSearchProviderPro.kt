@file:Suppress("MagicNumber")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.search.TrigramBloomIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import java.io.RandomAccessFile
import kotlin.math.min
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TxtSearchProviderPro(
    private val files: TxtBookFiles,
    private val store: Utf16TextStore,
    private val meta: TxtMeta,
    private val ioDispatcher: CoroutineDispatcher
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = channelFlow {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return@channelFlow
        }

        val context = SearchContext(
            query = normalizedQuery,
            options = options,
            startOffset = options.startFrom
                ?.let { TxtBlockLocatorCodec.parseOffset(it, store.lengthChars) }
                ?: 0L
        )
        val bloom = TrigramBloomIndex.openIfValid(files.bloomIdx, meta)

        if (shouldBuildBloomAsync(bloom, context.query.length)) {
            scheduleBloomBuild()
        }

        if (bloom != null && context.query.length >= BLOOM_MIN_QUERY_LENGTH) {
            fastSearchWithBloom(
                bloom = bloom,
                context = context
            )
        } else {
            streamingScan(context)
        }
    }

    private fun shouldBuildBloomAsync(bloom: TrigramBloomIndex?, queryLength: Int): Boolean {
        return bloom == null &&
            queryLength >= BLOOM_MIN_QUERY_LENGTH &&
            meta.lengthChars >= BLOOM_MIN_BOOK_CHARS
    }

    private fun ProducerScope<SearchHit>.scheduleBloomBuild() {
        // Build index opportunistically; current search falls back to streaming scan.
        launch(ioDispatcher) {
            TrigramBloomIndex.buildIfNeeded(
                file = files.bloomIdx,
                lockFile = files.bloomLock,
                store = store,
                meta = meta,
                ioDispatcher = ioDispatcher
            )
        }
    }

    private suspend fun ProducerScope<SearchHit>.fastSearchWithBloom(
        bloom: TrigramBloomIndex,
        context: SearchContext
    ) = withContext(ioDispatcher) {
        val matcher = KmpMatcher(
            pattern = context.query.toCharArray(),
            caseSensitive = context.options.caseSensitive
        )
        val trigramHashes = bloom.buildQueryTrigramHashes(context.query.lowercase())
        val blocks = bloom.blocksCount()
        val startBlock = (context.startOffset / bloom.blockChars).toInt().coerceAtLeast(0)
        val state = BloomScanState(
            matcher = matcher,
            queryLength = context.query.length,
            maxHits = context.options.maxHits,
            startOffset = context.startOffset,
            wholeWord = context.options.wholeWord
        )

        RandomAccessFile(files.bloomIdx, "r").use { raf ->
            for (blockIndex in startBlock until blocks) {
                coroutineContext.ensureActive()
                if (state.reachedMaxHits()) {
                    break
                }
                if (bloom.mayContainAll(raf, blockIndex, trigramHashes)) {
                    scanBloomBlockWithNeighbors(
                        bloom = bloom,
                        blockIndex = blockIndex,
                        blocksCount = blocks,
                        state = state
                    )
                }
            }
        }
    }

    private suspend fun ProducerScope<SearchHit>.scanBloomBlockWithNeighbors(
        bloom: TrigramBloomIndex,
        blockIndex: Int,
        blocksCount: Int,
        state: BloomScanState
    ) {
        for (candidate in intArrayOf(blockIndex, blockIndex - 1, blockIndex + 1)) {
            if (state.reachedMaxHits()) {
                break
            }
            scanBloomCandidateBlock(
                bloom = bloom,
                blockIndex = candidate,
                blocksCount = blocksCount,
                state = state
            )
        }
    }

    @Suppress("NestedBlockDepth")
    private suspend fun ProducerScope<SearchHit>.scanBloomCandidateBlock(
        blockIndex: Int,
        blocksCount: Int,
        bloom: TrigramBloomIndex,
        state: BloomScanState
    ) {
        if (!state.shouldScanBlock(blockIndex, blocksCount)) {
            return
        }

        val range = bloom.blockRange(blockIndex)
        val scanStart = (range.start - (state.queryLength + BLOOM_SCAN_PADDING).toLong())
            .coerceAtLeast(state.startOffset)
        val scanEnd = (range.endExclusive + (state.queryLength + BLOOM_SCAN_PADDING).toLong())
            .coerceAtMost(store.lengthChars)
        val readLength = (scanEnd - scanStart).toInt().coerceAtLeast(0)
        if (readLength > 0) {
            val chunk = store.readChars(scanStart, readLength)
            var canSend = true
            for (hitIndex in state.matcher.findAll(chunk)) {
                coroutineContext.ensureActive()
                if (canSend && !state.reachedMaxHits()) {
                    val globalStart = scanStart + hitIndex.toLong()
                    val passesWordBoundary = !state.wholeWord ||
                        isWholeWord(chars = chunk, start = hitIndex, len = state.queryLength)
                    if (passesWordBoundary && state.canEmit(globalStart)) {
                        val globalEnd = globalStart + state.queryLength.toLong()
                        canSend = emitHit(
                            startOffset = globalStart,
                            endOffset = globalEnd,
                            queryLength = state.queryLength
                        )
                        if (canSend) {
                            state.markEmitted()
                        }
                    }
                }
                if (!canSend || state.reachedMaxHits()) {
                    break
                }
            }
        }
    }

    private suspend fun ProducerScope<SearchHit>.streamingScan(
        context: SearchContext
    ) = withContext(ioDispatcher) {
        val pattern = context.query.toCharArray()
        val matcher = KmpMatcher(pattern = pattern, caseSensitive = context.options.caseSensitive)
        val overlap = (pattern.size - 1).coerceAtLeast(0)
        val maxHits = context.options.maxHits

        var carry = CharArray(0)
        var cursor = context.startOffset
        var emitted = 0
        var keepScanning = true

        while (keepScanning && cursor < store.lengthChars && emitted < maxHits) {
            coroutineContext.ensureActive()
            val readCount = min(STREAM_CHUNK_SIZE.toLong(), store.lengthChars - cursor).toInt()
            val chunk = store.readChars(cursor, readCount)
            if (chunk.isEmpty()) {
                keepScanning = false
            } else {
                val merged = mergeChunks(carry, chunk)
                val mergedStart = cursor - carry.size.toLong()
                var canSend = true
                for (matchIndex in matcher.findAll(merged)) {
                    coroutineContext.ensureActive()
                    if (!canSend || emitted >= maxHits) {
                        break
                    }
                    val globalStart = mergedStart + matchIndex.toLong()
                    val candidate = StreamingCandidate(
                        matchIndex = matchIndex,
                        globalStart = globalStart,
                        carrySize = carry.size
                    )
                    val eligible = isEligibleStreamingHit(
                        candidate = candidate,
                        merged = merged,
                        context = context,
                        queryLength = pattern.size
                    )
                    if (eligible) {
                        val globalEnd = globalStart + pattern.size.toLong()
                        canSend = emitHit(
                            startOffset = globalStart,
                            endOffset = globalEnd,
                            queryLength = pattern.size
                        )
                        if (canSend) {
                            emitted++
                        }
                    }
                }
                keepScanning = canSend
                carry = keepTrailingOverlap(merged, overlap)
            }
            cursor += readCount.toLong()
        }
    }

    private suspend fun ProducerScope<SearchHit>.emitHit(
        startOffset: Long,
        endOffset: Long,
        queryLength: Int
    ): Boolean {
        val sent = trySend(
            SearchHit(
                range = TxtBlockLocatorCodec.rangeForOffsets(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    maxOffset = store.lengthChars
                ),
                excerpt = buildExcerpt(startOffset, queryLength),
                sectionTitle = null
            )
        )
        return sent.isSuccess
    }

    private fun isEligibleStreamingHit(
        candidate: StreamingCandidate,
        merged: CharArray,
        context: SearchContext,
        queryLength: Int
    ): Boolean {
        val startsInCarry = candidate.matchIndex + queryLength <= candidate.carrySize
        val startsBeforeRequestedOffset = candidate.globalStart < context.startOffset
        val failsWordBoundary = context.options.wholeWord &&
            !isWholeWord(chars = merged, start = candidate.matchIndex, len = queryLength)
        return !startsInCarry && !startsBeforeRequestedOffset && !failsWordBoundary
    }

    private fun buildExcerpt(matchStart: Long, patternLength: Int): String {
        val center = matchStart + patternLength / 2L
        return store.readAround(center, before = 64, after = 128)
            .replace('\n', ' ')
            .trim()
    }

    private data class SearchContext(
        val query: String,
        val options: SearchOptions,
        val startOffset: Long
    )

    private data class StreamingCandidate(
        val matchIndex: Int,
        val globalStart: Long,
        val carrySize: Int
    )

    private data class BloomScanState(
        val matcher: KmpMatcher,
        val queryLength: Int,
        val maxHits: Int,
        val startOffset: Long,
        val wholeWord: Boolean,
        private val emittedStarts: MutableSet<Long> = hashSetOf(),
        private val scannedBlocks: MutableSet<Int> = hashSetOf()
    ) {
        private var emittedCount: Int = 0

        fun reachedMaxHits(): Boolean = emittedCount >= maxHits

        fun shouldScanBlock(blockIndex: Int, blocksCount: Int): Boolean {
            if (blockIndex < 0 || blockIndex >= blocksCount || reachedMaxHits()) {
                return false
            }
            return scannedBlocks.add(blockIndex)
        }

        fun canEmit(globalStart: Long): Boolean {
            if (globalStart < startOffset || !emittedStarts.add(globalStart)) {
                return false
            }
            return true
        }

        fun markEmitted() {
            emittedCount++
        }
    }

    private companion object {
        private const val BLOOM_MIN_QUERY_LENGTH = 3
        private const val BLOOM_MIN_BOOK_CHARS = 1_000_000L
        private const val BLOOM_SCAN_PADDING = 8
        private const val STREAM_CHUNK_SIZE = 64_000
    }
}

private fun mergeChunks(carry: CharArray, chunk: CharArray): CharArray {
    val merged = CharArray(carry.size + chunk.size)
    if (carry.isNotEmpty()) {
        System.arraycopy(carry, 0, merged, 0, carry.size)
    }
    System.arraycopy(chunk, 0, merged, carry.size, chunk.size)
    return merged
}

private fun keepTrailingOverlap(merged: CharArray, overlap: Int): CharArray {
    if (overlap <= 0) {
        return CharArray(0)
    }
    val keep = min(overlap, merged.size)
    return merged.copyOfRange(merged.size - keep, merged.size)
}

private fun isWholeWord(chars: CharArray, start: Int, len: Int): Boolean {
    val before = if (start - 1 >= 0) chars[start - 1] else null
    val after = if (start + len < chars.size) chars[start + len] else null
    val beforeOk = before == null || !Character.isLetterOrDigit(before)
    val afterOk = after == null || !Character.isLetterOrDigit(after)
    return beforeOk && afterOk
}
