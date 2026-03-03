@file:Suppress("LongMethod", "MagicNumber", "ReturnCount")

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
        val q = query.trim()
        if (q.isEmpty()) {
            return@channelFlow
        }
        val startOffset = options.startFrom
            ?.let { TxtBlockLocatorCodec.parseOffset(it, store.lengthChars) }
            ?: 0L

        var bloom = TrigramBloomIndex.openIfValid(files.bloomIdx, meta)
        if (bloom == null && q.length >= 3 && meta.lengthChars >= 1_000_000L) {
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

        if (bloom != null && q.length >= 3) {
            fastSearchWithBloom(
                bloom = bloom,
                q = q,
                options = options,
                startOffset = startOffset
            )
        } else {
            streamingScan(
                q = q,
                options = options,
                startOffset = startOffset
            )
        }
    }

    private suspend fun ProducerScope<SearchHit>.fastSearchWithBloom(
        bloom: TrigramBloomIndex,
        q: String,
        options: SearchOptions,
        startOffset: Long
    ) = withContext(ioDispatcher) {
        val pattern = q.toCharArray()
        val matcher = KmpMatcher(pattern = pattern, caseSensitive = options.caseSensitive)
        val trigramHashes = bloom.buildQueryTrigramHashes(q.lowercase())
        val blocks = bloom.blocksCount()
        val startBlock = (startOffset / bloom.blockChars).toInt().coerceAtLeast(0)
        var emitted = 0
        val emittedStart = HashSet<Long>()

        RandomAccessFile(files.bloomIdx, "r").use { raf ->
            val candidates = ArrayList<Int>(128)
            for (bi in startBlock until blocks) {
                coroutineContext.ensureActive()
                if (bloom.mayContainAll(raf, bi, trigramHashes)) {
                    candidates.add(bi)
                    if (bi > 0) candidates.add(bi - 1)
                    if (bi + 1 < blocks) candidates.add(bi + 1)
                }
            }
            candidates.sort()
            val unique = candidates.distinct()
            for (bi in unique) {
                coroutineContext.ensureActive()
                if (emitted >= options.maxHits) {
                    break
                }
                val range = bloom.blockRange(bi)
                val scanStart = (range.start - (pattern.size + 8).toLong()).coerceAtLeast(startOffset)
                val scanEnd = (range.endExclusive + (pattern.size + 8).toLong())
                    .coerceAtMost(store.lengthChars)
                val len = (scanEnd - scanStart).toInt().coerceAtLeast(0)
                if (len <= 0) {
                    continue
                }
                val chunk = store.readChars(scanStart, len)
                for (hitIndex in matcher.findAll(chunk)) {
                    coroutineContext.ensureActive()
                    val globalStart = scanStart + hitIndex.toLong()
                    val globalEnd = globalStart + pattern.size.toLong()
                    if (globalStart < startOffset) {
                        continue
                    }
                    if (!emittedStart.add(globalStart)) {
                        continue
                    }
                    if (options.wholeWord && !isWholeWord(chunk, hitIndex, pattern.size)) {
                        continue
                    }
                    trySend(
                        SearchHit(
                            range = TxtBlockLocatorCodec.rangeForOffsets(
                                startOffset = globalStart,
                                endOffset = globalEnd,
                                maxOffset = store.lengthChars
                            ),
                            excerpt = buildExcerpt(globalStart, pattern.size),
                            sectionTitle = null
                        )
                    )
                    emitted++
                    if (emitted >= options.maxHits) {
                        break
                    }
                }
            }
        }
    }

    private suspend fun ProducerScope<SearchHit>.streamingScan(
        q: String,
        options: SearchOptions,
        startOffset: Long
    ) = withContext(ioDispatcher) {
        val pattern = q.toCharArray()
        val matcher = KmpMatcher(pattern = pattern, caseSensitive = options.caseSensitive)
        val overlap = (pattern.size - 1).coerceAtLeast(0)
        val chunkSize = 64_000

        var carry = CharArray(0)
        var cursor = startOffset
        var emitted = 0

        while (cursor < store.lengthChars && emitted < options.maxHits) {
            coroutineContext.ensureActive()
            val readCount = min(chunkSize.toLong(), store.lengthChars - cursor).toInt()
            val chunk = store.readChars(cursor, readCount)
            if (chunk.isEmpty()) {
                break
            }

            val merged = CharArray(carry.size + chunk.size)
            if (carry.isNotEmpty()) {
                System.arraycopy(carry, 0, merged, 0, carry.size)
            }
            System.arraycopy(chunk, 0, merged, carry.size, chunk.size)
            val mergedStart = cursor - carry.size.toLong()

            for (index in matcher.findAll(merged)) {
                coroutineContext.ensureActive()
                if (index + pattern.size <= carry.size) {
                    continue
                }
                val globalStart = mergedStart + index.toLong()
                val globalEnd = globalStart + pattern.size.toLong()
                if (globalStart < startOffset) {
                    continue
                }
                if (options.wholeWord && !isWholeWord(merged, index, pattern.size)) {
                    continue
                }

                trySend(
                    SearchHit(
                        range = TxtBlockLocatorCodec.rangeForOffsets(
                            startOffset = globalStart,
                            endOffset = globalEnd,
                            maxOffset = store.lengthChars
                        ),
                        excerpt = buildExcerpt(globalStart, pattern.size),
                        sectionTitle = null
                    )
                )
                emitted++
                if (emitted >= options.maxHits) {
                    break
                }
            }

            carry = if (overlap > 0) {
                val keep = min(overlap, merged.size)
                merged.copyOfRange(merged.size - keep, merged.size)
            } else {
                CharArray(0)
            }
            cursor += readCount.toLong()
        }
    }

    private fun isWholeWord(chars: CharArray, start: Int, len: Int): Boolean {
        val before = if (start - 1 >= 0) chars[start - 1] else null
        val after = if (start + len < chars.size) chars[start + len] else null
        val beforeOk = before == null || !Character.isLetterOrDigit(before)
        val afterOk = after == null || !Character.isLetterOrDigit(after)
        return beforeOk && afterOk
    }

    private fun buildExcerpt(matchStart: Long, patternLength: Int): String {
        val center = matchStart + patternLength / 2L
        return store.readAround(center, before = 64, after = 128)
            .replace('\n', ' ')
            .trim()
    }
}
