package com.ireader.engines.pdf.internal.provider

import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.LocatorRange
import com.ireader.engines.pdf.internal.util.endCharLocator
import com.ireader.engines.pdf.internal.util.startCharLocator
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class PdfSearchProvider(
    private val pageCount: Int,
    private val textProvider: PdfTextProvider
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        val q = query.trim()
        if (q.isEmpty()) return@flow

        val ctx = currentCoroutineContext()
        val startPage = options.startFrom?.toPdfPageIndexOrNull(pageCount) ?: 0
        val needle = if (options.caseSensitive) q else q.lowercase(Locale.ROOT)
        var emitted = 0

        for (pageIndex in startPage until pageCount) {
            ctx.ensureActive()
            val pageText = textProvider.pageText(pageIndex).orEmpty()
            if (pageText.isEmpty()) continue

            val haystack = if (options.caseSensitive) pageText else pageText.lowercase(Locale.ROOT)
            var fromIndex = 0

            while (true) {
                ctx.ensureActive()
                val idx = haystack.indexOf(needle, startIndex = fromIndex)
                if (idx < 0) break
                val end = idx + needle.length
                if (options.wholeWord && !isWholeWord(haystack, idx, end)) {
                    fromIndex = end
                    continue
                }

                val excerpt = excerpt(pageText, idx, end)
                val startLocator = startCharLocator(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    charStart = idx
                )
                val endLocator = endCharLocator(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    charEnd = end
                )

                emit(
                    SearchHit(
                        range = LocatorRange(start = startLocator, end = endLocator),
                        excerpt = excerpt
                    )
                )
                emitted++
                if (emitted >= options.maxHits) return@flow
                fromIndex = end
            }
        }
    }

    private fun isWholeWord(text: String, start: Int, end: Int): Boolean {
        fun isWord(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
        val leftOk = start == 0 || !isWord(text[start - 1])
        val rightOk = end >= text.length || !isWord(text[end])
        return leftOk && rightOk
    }

    private fun excerpt(text: String, start: Int, end: Int): String {
        val left = (start - 40).coerceAtLeast(0)
        val right = (end + 40).coerceAtMost(text.length)
        return text.substring(left, right)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }
}
