package com.ireader.engines.pdf.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlin.math.max
import kotlin.math.min

internal class PdfTextProvider(
    private val backend: PdfBackend,
    private val pageCount: Int
) : TextProvider {

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        val startPage = range.start.toPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid PDF start locator"))
        val endPage = range.end.toPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid PDF end locator"))

        val from = min(startPage, endPage)
        val to = max(startPage, endPage)

        return runCatching {
            val parts = buildList {
                for (page in from..to) {
                    val text = backend.pageText(page).orEmpty()
                    val sliced = when {
                        page == startPage && page == endPage -> {
                            val start = range.start.extras["charStart"]?.toIntOrNull() ?: 0
                            val end = range.end.extras["charEnd"]?.toIntOrNull() ?: text.length
                            safeSubstring(text, start, end)
                        }

                        page == startPage -> {
                            val start = range.start.extras["charStart"]?.toIntOrNull() ?: 0
                            safeSubstring(text, start, text.length)
                        }

                        page == endPage -> {
                            val end = range.end.extras["charEnd"]?.toIntOrNull() ?: text.length
                            safeSubstring(text, 0, end)
                        }

                        else -> text
                    }
                    add(sliced)
                }
            }
            parts.joinToString(separator = "\n")
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(it.toReaderError()) }
        )
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        val pageIndex = locator.toPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid PDF locator"))
        return runCatching {
            val text = backend.pageText(pageIndex).orEmpty()
            if (text.isEmpty()) return@runCatching ""

            val center = locator.extras["charIndex"]?.toIntOrNull()
                ?: locator.extras["charStart"]?.toIntOrNull()
                ?: (text.length / 2)

            val half = (maxChars.coerceAtLeast(1) / 2).coerceAtLeast(1)
            val start = (center - half).coerceAtLeast(0)
            val end = (center + half).coerceAtMost(text.length)
            safeSubstring(text, start, end)
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(it.toReaderError()) }
        )
    }

    suspend fun pageText(pageIndex: Int): String? = backend.pageText(pageIndex)

    private fun safeSubstring(text: String, startInclusive: Int, endExclusive: Int): String {
        if (text.isEmpty()) return ""
        val safeStart = startInclusive.coerceIn(0, text.length)
        val safeEnd = endExclusive.coerceIn(safeStart, text.length)
        return text.substring(safeStart, safeEnd)
    }

    private fun Locator.toPageIndexOrNull(pageCount: Int): Int? {
        if (scheme != LocatorSchemes.PDF_PAGE) return null
        val index = value.toIntOrNull() ?: return null
        return index.coerceIn(0, pageCount.coerceAtLeast(1) - 1)
    }
}
