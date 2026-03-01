package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal class TxtSearchProvider(
    private val store: TxtTextStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        if (query.isBlank()) return@flow

        val chunkSize = 64 * 1024
        val overlap = (query.length + 64).coerceAtMost(8_192)
        val totalChars = withContext(ioDispatcher) { store.totalChars().coerceAtLeast(0) }
        if (totalChars == 0) return@flow

        var cursor = options.startFrom?.let { parseOffset(it) } ?: 0
        cursor = cursor.coerceIn(0, totalChars)
        var emitted = 0

        while (cursor < totalChars && emitted < options.maxHits) {
            coroutineContext.ensureActive()

            val readLen = (chunkSize + overlap).coerceAtMost(totalChars - cursor)
            val chunk = withContext(ioDispatcher) { store.readChars(cursor, readLen) }
            if (chunk.isEmpty()) break

            val hasNextChunk = cursor + chunkSize < totalChars
            var searchFrom = 0
            while (searchFrom <= chunk.length - query.length && emitted < options.maxHits) {
                coroutineContext.ensureActive()

                val found = indexOfMatch(
                    haystack = chunk,
                    needle = query,
                    start = searchFrom,
                    ignoreCase = !options.caseSensitive
                )
                if (found < 0) break
                if (hasNextChunk && found >= chunkSize) break
                if (options.wholeWord && !isWholeWord(chunk, found, query.length)) {
                    searchFrom = found + query.length
                    continue
                }

                val start = cursor + found
                val end = start + query.length
                emit(
                    SearchHit(
                        range = LocatorRange(
                            start = Locator(LocatorSchemes.TXT_OFFSET, start.toString()),
                            end = Locator(LocatorSchemes.TXT_OFFSET, end.toString())
                        ),
                        excerpt = buildExcerpt(chunk, found, query.length),
                        sectionTitle = null
                    )
                )
                emitted += 1
                searchFrom = found + query.length
            }

            cursor += chunkSize
        }
    }

    private fun parseOffset(locator: Locator): Int {
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) return 0
        return locator.value.toIntOrNull() ?: 0
    }

    private fun indexOfMatch(
        haystack: String,
        needle: String,
        start: Int,
        ignoreCase: Boolean
    ): Int {
        if (needle.isEmpty()) return -1
        val last = haystack.length - needle.length
        var i = start.coerceAtLeast(0)
        while (i <= last) {
            if (haystack.regionMatches(i, needle, 0, needle.length, ignoreCase = ignoreCase)) {
                return i
            }
            i += 1
        }
        return -1
    }

    private fun isWholeWord(text: String, start: Int, length: Int): Boolean {
        val leftIndex = start - 1
        val rightIndex = start + length
        val leftOk = leftIndex < 0 || !isWordChar(text[leftIndex])
        val rightOk = rightIndex >= text.length || !isWordChar(text[rightIndex])
        return leftOk && rightOk
    }

    private fun isWordChar(ch: Char): Boolean {
        return ch.isLetterOrDigit() || ch == '_'
    }

    private fun buildExcerpt(source: String, hitStart: Int, hitLen: Int): String {
        val left = (hitStart - 40).coerceAtLeast(0)
        val right = (hitStart + hitLen + 60).coerceAtMost(source.length)
        val prefix = if (left > 0) "..." else ""
        val suffix = if (right < source.length) "..." else ""
        return prefix + source.substring(left, right).replace('\n', ' ') + suffix
    }
}
