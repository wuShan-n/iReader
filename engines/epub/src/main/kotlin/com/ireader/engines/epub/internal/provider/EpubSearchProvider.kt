package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.EpubLocator
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class EpubSearchProvider(
    private val container: EpubContainer,
    private val textProvider: EpubTextProvider
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        if (query.isBlank()) return@flow

        val startIndex = options.startFrom
            ?.let { locator -> EpubLocator.spineIndexOf(container, locator) }
            ?.coerceIn(0, (container.spineCount - 1).coerceAtLeast(0))
            ?: 0

        val needle = if (options.caseSensitive) query else query.lowercase()
        var emitted = 0

        for (spineIndex in startIndex until container.spineCount) {
            coroutineContext.ensureActive()

            val chapterText = textProvider.chapterText(spineIndex)
            val haystack = if (options.caseSensitive) chapterText else chapterText.lowercase()

            var from = 0
            while (from <= haystack.length - needle.length && emitted < options.maxHits) {
                coroutineContext.ensureActive()

                val found = haystack.indexOf(needle, startIndex = from)
                if (found < 0) break

                val end = found + needle.length
                if (options.wholeWord && !isWholeWord(haystack, found, end)) {
                    from = end
                    continue
                }

                emit(
                    SearchHit(
                        range = LocatorRange(
                            start = Locator(
                                scheme = LocatorSchemes.EPUB_CFI,
                                value = "spine:$spineIndex",
                                extras = mapOf("charStart" to found.toString())
                            ),
                            end = Locator(
                                scheme = LocatorSchemes.EPUB_CFI,
                                value = "spine:$spineIndex",
                                extras = mapOf("charEnd" to end.toString())
                            )
                        ),
                        excerpt = buildExcerpt(chapterText, found, end),
                        sectionTitle = container.titleForSpine(spineIndex)
                    )
                )

                emitted += 1
                from = end
            }

            if (emitted >= options.maxHits) {
                break
            }
        }
    }

    private fun isWholeWord(text: String, start: Int, end: Int): Boolean {
        val leftOk = start == 0 || !text[start - 1].isLetterOrDigit()
        val rightOk = end >= text.length || !text[end].isLetterOrDigit()
        return leftOk && rightOk
    }

    private fun buildExcerpt(text: String, start: Int, end: Int): String {
        val left = (start - 40).coerceAtLeast(0)
        val right = (end + 60).coerceAtMost(text.length)
        val prefix = if (left > 0) "..." else ""
        val suffix = if (right < text.length) "..." else ""
        return prefix + text.substring(left, right).replace('\n', ' ') + suffix
    }
}
