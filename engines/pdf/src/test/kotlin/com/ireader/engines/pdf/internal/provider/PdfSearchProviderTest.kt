package com.ireader.engines.pdf.internal.provider

import android.graphics.Bitmap
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.backend.PdfBackendCapabilities
import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSearchProviderTest {

    @Test
    fun `search respects case sensitivity`() = runTest {
        val backend = FakeBackend(
            pages = listOf("Alpha beta ALPHA")
        )
        val textProvider = PdfTextProvider(backend = backend, pageCount = 1)
        val provider = PdfSearchProvider(pageCount = 1, textProvider = textProvider)

        val insensitive = provider.search("alpha", SearchOptions(caseSensitive = false)).toList()
        val sensitive = provider.search("alpha", SearchOptions(caseSensitive = true)).toList()

        assertEquals(2, insensitive.size)
        assertEquals(0, sensitive.size)
    }

    @Test
    fun `search respects whole word`() = runTest {
        val backend = FakeBackend(
            pages = listOf("cat scatter cat")
        )
        val textProvider = PdfTextProvider(backend = backend, pageCount = 1)
        val provider = PdfSearchProvider(pageCount = 1, textProvider = textProvider)

        val wholeWordHits = provider.search("cat", SearchOptions(wholeWord = true)).toList()
        assertEquals(2, wholeWordHits.size)
    }

    @Test
    fun `search respects start page and max hits`() = runTest {
        val backend = FakeBackend(
            pages = listOf(
                "alpha zero",
                "alpha one alpha",
                "alpha two"
            )
        )
        val textProvider = PdfTextProvider(backend = backend, pageCount = 3)
        val provider = PdfSearchProvider(pageCount = 3, textProvider = textProvider)

        val hits = provider.search(
            query = "alpha",
            options = SearchOptions(
                maxHits = 2,
                startFrom = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "1")
            )
        ).toList()

        assertEquals(2, hits.size)
        assertEquals("1", hits[0].range.start.value)
        assertEquals("1", hits[1].range.start.value)
    }

    @Test
    fun `search hit locators include char offsets`() = runTest {
        val backend = FakeBackend(
            pages = listOf("prefix alpha suffix")
        )
        val textProvider = PdfTextProvider(backend = backend, pageCount = 1)
        val provider = PdfSearchProvider(pageCount = 1, textProvider = textProvider)

        val hit = provider.search("alpha", SearchOptions()).toList().single()
        val startExtras = hit.range.start.extras
        val endExtras = hit.range.end.extras

        assertEquals("7", startExtras["charIndex"])
        assertEquals("7", startExtras["charStart"])
        assertEquals("12", endExtras["charIndex"])
        assertEquals("12", endExtras["charEnd"])
        assertTrue(hit.excerpt.contains("alpha"))
    }

    @Test
    fun `search startFrom charIndex starts from offset`() = runTest {
        val backend = FakeBackend(
            pages = listOf("alpha one alpha")
        )
        val textProvider = PdfTextProvider(backend = backend, pageCount = 1)
        val provider = PdfSearchProvider(pageCount = 1, textProvider = textProvider)

        val hits = provider.search(
            query = "alpha",
            options = SearchOptions(
                startFrom = Locator(
                    scheme = LocatorSchemes.PDF_PAGE,
                    value = "0",
                    extras = mapOf("charIndex" to "6")
                )
            )
        ).toList()

        assertEquals(1, hits.size)
        assertEquals("10", hits.first().range.start.extras["charIndex"])
    }

    @Test
    fun `blank query returns no hits`() = runTest {
        val backend = FakeBackend(
            pages = listOf("alpha beta")
        )
        val textProvider = PdfTextProvider(backend = backend, pageCount = 1)
        val provider = PdfSearchProvider(pageCount = 1, textProvider = textProvider)

        val hits = provider.search("   ", SearchOptions()).toList()
        assertTrue(hits.isEmpty())
    }

    private class FakeBackend(
        private val pages: List<String>
    ) : PdfBackend {
        override val capabilities: PdfBackendCapabilities = PdfBackendCapabilities(
            outline = true,
            links = true,
            textExtraction = true,
            search = true
        )

        override suspend fun pageCount(): Int = pages.size

        override suspend fun metadata(): DocumentMetadata = DocumentMetadata()

        override suspend fun pageSize(pageIndex: Int): PdfPageSize = PdfPageSize(1000, 1000)

        override suspend fun renderRegion(
            pageIndex: Int,
            bitmap: Bitmap,
            regionLeftPx: Int,
            regionTopPx: Int,
            regionWidthPx: Int,
            regionHeightPx: Int,
            quality: RenderPolicy.Quality
        ) = Unit

        override suspend fun pageLinks(pageIndex: Int): List<DocumentLink> = emptyList()

        override suspend fun outline(): List<OutlineNode> = emptyList()

        override suspend fun pageText(pageIndex: Int): String = pages[pageIndex]

        override fun close() = Unit
    }
}
