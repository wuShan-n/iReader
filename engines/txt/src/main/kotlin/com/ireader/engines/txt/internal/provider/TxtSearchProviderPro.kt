@file:Suppress("MagicNumber")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.locator.TxtLocatorResolver
import com.ireader.engines.txt.internal.locator.TxtProjectionVersion
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.engines.txt.internal.search.TrigramBloomIndex
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TxtSearchProviderPro(
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    private val blockIndex: TxtBlockIndex,
    private val projectionEngine: TextProjectionEngine,
    private val blockStore: BlockStore,
    private val ioDispatcher: CoroutineDispatcher
) : SearchProvider {

    private val providerScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val bloomBuildScheduled = AtomicBoolean(false)

    fun warmup() {
        if (!projectionEngine.hasIndexedBreaks()) {
            return
        }
        if (meta.lengthCodeUnits >= WARMUP_MIN_CODE_UNITS) {
            scheduleBloomBuild()
        }
    }

    fun close() {
        providerScope.cancel()
    }

    fun invalidate() {
        providerScope.coroutineContext.cancelChildren()
        bloomBuildScheduled.set(false)
        runCatching { files.searchIdx.delete() }
        runCatching { files.searchLock.delete() }
    }

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = channelFlow {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return@channelFlow
        }

        val context = SearchContext(
            query = normalizedQuery,
            options = options,
            startOffset = options.startFrom
                ?.let {
                    TxtLocatorResolver.parsePublicOffset(
                        locator = it,
                        blockIndex = blockIndex,
                        contentFingerprint = meta.contentFingerprint,
                        maxOffset = blockIndex.lengthCodeUnits,
                        projectionEngine = projectionEngine
                    )
                }
                ?: 0L
        )
        val bloom = TrigramBloomIndex.openIfValid(files.searchIdx, meta, projectionVersion())
        if (bloom == null &&
            normalizedQuery.length >= BLOOM_MIN_QUERY_LENGTH &&
            projectionEngine.hasIndexedBreaks()
        ) {
            scheduleBloomBuild()
        }

        if (bloom != null && normalizedQuery.length >= BLOOM_MIN_QUERY_LENGTH) {
            searchWithBloom(bloom = bloom, context = context)
        } else {
            scanAllBlocks(context)
        }
    }

    private fun scheduleBloomBuild() {
        if (!projectionEngine.hasIndexedBreaks()) {
            return
        }
        if (!bloomBuildScheduled.compareAndSet(false, true)) {
            return
        }
        providerScope.launch {
            try {
                TrigramBloomIndex.buildIfNeeded(
                    file = files.searchIdx,
                    lockFile = files.searchLock,
                    blockIndex = blockIndex,
                    projectionEngine = projectionEngine,
                    meta = meta,
                    projectionVersion = projectionVersion(),
                    ioDispatcher = ioDispatcher
                )
            } finally {
                bloomBuildScheduled.set(false)
            }
        }
    }

    private suspend fun ProducerScope<SearchHit>.searchWithBloom(
        bloom: TrigramBloomIndex,
        context: SearchContext
    ) = withContext(ioDispatcher) {
        val hashes = bloom.buildQueryTrigramHashes(context.query.lowercase())
        val scratch = ByteArray(bloom.bitsetBytes())
        val startBlock = blockIndex.blockIdForOffset(context.startOffset).coerceAtLeast(0)
        val emittedStarts = LinkedHashSet<Long>()
        RandomAccessFile(files.searchIdx, "r").use { raf ->
            for (candidateBlock in startBlock until blockIndex.blockCount) {
                coroutineContext.ensureActive()
                if (emittedStarts.size >= context.options.maxHits) {
                    break
                }
                if (!bloom.mayContainAll(raf, candidateBlock, hashes, scratch = scratch)) {
                    continue
                }
                scanCandidateBlock(
                    blockId = candidateBlock,
                    context = context,
                    emittedStarts = emittedStarts
                )
            }
        }
    }

    private suspend fun ProducerScope<SearchHit>.scanAllBlocks(
        context: SearchContext
    ) = withContext(ioDispatcher) {
        val startBlock = blockIndex.blockIdForOffset(context.startOffset).coerceAtLeast(0)
        val emittedStarts = LinkedHashSet<Long>()
        for (candidateBlock in startBlock until blockIndex.blockCount) {
            coroutineContext.ensureActive()
            if (emittedStarts.size >= context.options.maxHits) {
                break
            }
            scanCandidateBlock(
                blockId = candidateBlock,
                context = context,
                emittedStarts = emittedStarts
            )
        }
    }

    private suspend fun ProducerScope<SearchHit>.scanCandidateBlock(
        blockId: Int,
        context: SearchContext,
        emittedStarts: MutableSet<Long>
    ) {
        val scanPadding = (context.query.length + BLOCK_SCAN_PADDING).coerceAtLeast(2)
        val rangeStart = (blockIndex.blockStartOffset(blockId) - scanPadding.toLong())
            .coerceAtLeast(context.startOffset)
        val rangeEnd = (blockIndex.blockEndOffset(blockId) + scanPadding.toLong())
            .coerceAtMost(blockIndex.lengthCodeUnits)
        if (rangeEnd <= rangeStart) {
            return
        }

        val projection = projectionEngine.projectRange(
            startOffset = rangeStart,
            endOffsetExclusive = rangeEnd
        )
        if (projection.displayText.isEmpty()) {
            return
        }

        val matcher = KmpMatcher(
            pattern = context.query.toCharArray(),
            caseSensitive = context.options.caseSensitive
        )
        val chars = projection.displayText.toCharArray()
        matcher.forEachMatch(chars) { matchIndex ->
            val endIndex = matchIndex + context.query.length
            if (endIndex > projection.projectedBoundaryToRawOffsets.lastIndex) {
                return@forEachMatch true
            }
            val globalStart = projection.projectedBoundaryToRawOffsets[matchIndex]
            val globalEnd = projection.projectedBoundaryToRawOffsets[endIndex]
            if (globalStart < context.startOffset) {
                return@forEachMatch true
            }
            if (globalEnd <= globalStart) {
                return@forEachMatch true
            }
            if (context.options.wholeWord && !isWholeWord(chars, matchIndex, context.query.length)) {
                return@forEachMatch true
            }
            if (!emittedStarts.add(globalStart)) {
                return@forEachMatch true
            }
            val sent = trySend(
                SearchHit(
                    range = TxtLocatorResolver.rangeForOffsets(
                        startOffset = globalStart,
                        endOffset = globalEnd,
                        blockIndex = blockIndex,
                        contentFingerprint = meta.contentFingerprint,
                        maxOffset = blockIndex.lengthCodeUnits,
                        projectionEngine = projectionEngine
                    ),
                    excerpt = buildExcerpt(projection.displayText, matchIndex, context.query.length),
                    sectionTitle = null
                )
            )
            sent.isSuccess && emittedStarts.size < context.options.maxHits
        }
    }

    private fun buildExcerpt(
        displayText: String,
        matchIndex: Int,
        queryLength: Int
    ): String {
        val center = matchIndex + (queryLength / 2)
        val start = (center - EXCERPT_BEFORE).coerceAtLeast(0)
        val end = (center + EXCERPT_AFTER).coerceAtMost(displayText.length)
        return displayText.substring(start, end)
            .replace('\n', ' ')
            .trim()
    }

    private data class SearchContext(
        val query: String,
        val options: SearchOptions,
        val startOffset: Long
    )

    private companion object {
        private const val BLOOM_MIN_QUERY_LENGTH = 3
        private const val BLOCK_SCAN_PADDING = 8
        private const val EXCERPT_BEFORE = 64
        private const val EXCERPT_AFTER = 128
        private const val WARMUP_MIN_CODE_UNITS = 128_000L
    }

    private fun projectionVersion(): String = TxtProjectionVersion.current(files, meta)
}

private fun isWholeWord(chars: CharArray, start: Int, len: Int): Boolean {
    val before = if (start - 1 >= 0) chars[start - 1] else null
    val after = if (start + len < chars.size) chars[start + len] else null
    val beforeOk = before == null || !Character.isLetterOrDigit(before)
    val afterOk = after == null || !Character.isLetterOrDigit(after)
    return beforeOk && afterOk
}
