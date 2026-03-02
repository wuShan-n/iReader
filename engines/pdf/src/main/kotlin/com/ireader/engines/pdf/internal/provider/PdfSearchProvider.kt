package com.ireader.engines.pdf.internal.provider

import android.graphics.RectF
import com.ireader.engines.pdf.internal.pdfium.PdfiumDocument
import com.ireader.engines.pdf.internal.pdfium.pdfiumRotateIndex
import com.ireader.engines.pdf.internal.pdfium.rectToNormalized
import com.ireader.engines.pdf.internal.util.useSafely
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import io.legere.pdfiumandroid.api.FindFlags
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.coroutineContext

internal class PdfSearchProvider(
    private val pdfium: PdfiumDocument,
    private val rotationDegreesProvider: () -> Int,
    private val highlightStore: PdfHighlightStore
) : SearchProvider, HighlightControl {

    override fun clearHighlights() {
        highlightStore.clearHighlights()
    }

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        val q = query.trim()
        if (q.isEmpty()) {
            highlightStore.clearHighlights()
            return@flow
        }

        val searchGeneration = highlightStore.newSearch()
        val flags = buildSet {
            if (options.caseSensitive) add(FindFlags.MatchCase)
            if (options.wholeWord) add(FindFlags.MatchWholeWord)
        }

        val startPage = options.startFrom
            ?.takeIf { it.scheme == LocatorSchemes.PDF_PAGE }
            ?.value
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0

        val maxHits = options.maxHits.coerceAtLeast(1)
        var emitted = 0
        var stopped = false

        for (pageIndex in startPage until pdfium.pageCount) {
            coroutineContext.ensureActive()
            if (stopped) break

            val pageText = pdfium.pageText(pageIndex)
            val sectionTitle = pdfium.sectionTitleForPage(pageIndex)
            val rotation = rotationDegreesProvider()
            val rotate = pdfiumRotateIndex(rotation)
            val base = 10_000

            pdfium.withTextPage(pageIndex) { page, textPage ->
                val find = textPage.findStart(q, flags, 0) ?: return@withTextPage
                find.useSafely { fr ->
                    while (!stopped && fr.findNext()) {
                        coroutineContext.ensureActive()
                        val startIndex = fr.getSchResultIndex()
                        if (startIndex < 0) continue

                        val rects: List<NormalizedRect> = buildList {
                            val rectCount = textPage.textPageCountRects(startIndex, q.length)
                                .coerceAtLeast(0)
                            for (i in 0 until rectCount) {
                                val rect: RectF = textPage.textPageGetRect(i) ?: continue
                                val device = page.mapRectToDevice(
                                    startX = 0,
                                    startY = 0,
                                    sizeX = base,
                                    sizeY = base,
                                    rotate = rotate,
                                    coords = rect
                                )
                                add(rectToNormalized(device, base))
                            }
                        }

                        highlightStore.add(pageIndex, rects, searchGeneration)

                        val locator = Locator(LocatorSchemes.PDF_PAGE, pageIndex.toString())
                        val extras = if (rects.isNotEmpty()) {
                            mapOf(PdfRectCodec.EXTRA_RECTS to PdfRectCodec.encode(rects))
                        } else {
                            emptyMap()
                        }

                        emit(
                            SearchHit(
                                range = LocatorRange(start = locator, end = locator, extras = extras),
                                excerpt = buildExcerpt(pageText, startIndex, q.length),
                                sectionTitle = sectionTitle
                            )
                        )

                        emitted += 1
                        if (emitted >= maxHits) {
                            stopped = true
                        }
                    }
                }
            }
        }
    }

    private fun buildExcerpt(text: String, start: Int, len: Int): String {
        if (text.isEmpty()) return ""
        val s = (start - 24).coerceAtLeast(0)
        val e = (start + len + 24).coerceAtMost(text.length)
        return text.substring(s, e).replace('\n', ' ')
    }
}
