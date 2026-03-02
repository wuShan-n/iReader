@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MagicNumber"
)

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class TxtSearchProvider(
    private val store: Utf16TextStore,
    private val ioDispatcher: CoroutineDispatcher
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        if (query.isBlank()) {
            return@flow
        }
        val pattern = query.toCharArray()
        val matcher = KmpMatcher(pattern = pattern, caseSensitive = options.caseSensitive)
        val overlap = (pattern.size - 1).coerceAtLeast(0)
        val chunkSize = 64_000
        val startOffset = options.startFrom
            ?.takeIf { it.scheme == LocatorSchemes.TXT_OFFSET }
            ?.value
            ?.toLongOrNull()
            ?.coerceIn(0L, store.lengthChars)
            ?: 0L

        var carry = CharArray(0)
        var cursor = startOffset
        var emitted = 0

        while (cursor < store.lengthChars) {
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

            val hits = matcher.findAll(merged)
            for (index in hits) {
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

                emit(
                    SearchHit(
                        range = LocatorRange(
                            start = Locator(LocatorSchemes.TXT_OFFSET, globalStart.toString()),
                            end = Locator(LocatorSchemes.TXT_OFFSET, globalEnd.toString())
                        ),
                        excerpt = buildExcerpt(globalStart, pattern.size),
                        sectionTitle = null
                    )
                )
                emitted++
                if (emitted >= options.maxHits) {
                    return@flow
                }
            }

            if (overlap > 0) {
                val keep = min(overlap, merged.size)
                carry = merged.copyOfRange(merged.size - keep, merged.size)
            } else {
                carry = CharArray(0)
            }
            cursor += readCount.toLong()
        }
    }.flowOn(ioDispatcher)

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
