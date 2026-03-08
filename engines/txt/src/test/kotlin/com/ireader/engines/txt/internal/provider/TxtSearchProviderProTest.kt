package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.locator.TxtProjectionVersion
import com.ireader.engines.txt.internal.search.TrigramBloomIndex
import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import com.ireader.reader.api.provider.SearchOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtSearchProviderProTest {

    @Test
    fun `streaming scan should honor whole word and start offset`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "alpha beta alphabeta ALPHA\nline two alpha\nline three alpha\n",
            sampleHash = "txt-search-streaming",
            ioDispatcher = Dispatchers.IO
        )
        val provider = TxtSearchProviderPro(
            files = fixture.files,
            meta = fixture.meta,
            blockIndex = fixture.blockIndex,
            projectionEngine = fixture.projectionEngine,
            blockStore = fixture.blockStore,
            ioDispatcher = Dispatchers.IO
        )
        try {
            val allHits = provider.search(
                query = "alpha",
                options = SearchOptions(caseSensitive = false, wholeWord = true, maxHits = 20)
            ).toList()
            val starts = allHits.mapNotNull { fixture.parseOffset(it.range.start) }
            assertEquals(4, starts.size)

            val secondLineOffset = fixture.sourceText.indexOf("line two").toLong()
            val secondLineLocator = fixture.locatorFor(secondLineOffset)
            val resolvedStartOffset = requireNotNull(fixture.parseOffset(secondLineLocator))
            val fromSecondLine = provider.search(
                query = "alpha",
                options = SearchOptions(
                    caseSensitive = false,
                    wholeWord = true,
                    maxHits = 20,
                    startFrom = secondLineLocator
                )
            ).toList()
            val fromSecondStarts = fromSecondLine.mapNotNull { fixture.parseOffset(it.range.start) }
            assertTrue(fromSecondStarts.size >= 2)
            assertTrue(
                "resolvedStartOffset=$resolvedStartOffset, fromSecondStarts=$fromSecondStarts",
                fromSecondStarts.all { it >= resolvedStartOffset }
            )
        } finally {
            provider.close()
            fixture.close()
        }
    }

    @Test
    fun `bloom path should return limited and deduplicated hits`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = buildLargeText(),
            sampleHash = "txt-search-bloom",
            ioDispatcher = Dispatchers.IO
        )
        try {
            val projectionVersion = TxtProjectionVersion.current(fixture.files, fixture.meta)
            TrigramBloomIndex.buildIfNeeded(
                file = fixture.files.searchIdx,
                lockFile = fixture.files.searchLock,
                blockIndex = fixture.blockIndex,
                projectionEngine = fixture.projectionEngine,
                meta = fixture.meta,
                projectionVersion = projectionVersion,
                ioDispatcher = Dispatchers.IO
            )
            assertTrue(fixture.files.searchIdx.exists())

            val provider = TxtSearchProviderPro(
                files = fixture.files,
                meta = fixture.meta,
                blockIndex = fixture.blockIndex,
                projectionEngine = fixture.projectionEngine,
                blockStore = fixture.blockStore,
                ioDispatcher = Dispatchers.IO
            )
            try {
                val hits = provider.search(
                    query = "needle",
                    options = SearchOptions(maxHits = 7)
                ).toList()
                val starts = hits.mapNotNull { fixture.parseOffset(it.range.start) }

                assertTrue(starts.isNotEmpty())
                assertTrue(starts.size <= 7)
                assertEquals(starts.size, starts.toSet().size)
            } finally {
                provider.close()
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `invalidate should drop persisted bloom index`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = buildLargeText(),
            sampleHash = "txt-search-invalidate",
            ioDispatcher = Dispatchers.IO
        )
        try {
            val projectionVersion = TxtProjectionVersion.current(fixture.files, fixture.meta)
            TrigramBloomIndex.buildIfNeeded(
                file = fixture.files.searchIdx,
                lockFile = fixture.files.searchLock,
                blockIndex = fixture.blockIndex,
                projectionEngine = fixture.projectionEngine,
                meta = fixture.meta,
                projectionVersion = projectionVersion,
                ioDispatcher = Dispatchers.IO
            )
            val provider = TxtSearchProviderPro(
                files = fixture.files,
                meta = fixture.meta,
                blockIndex = fixture.blockIndex,
                projectionEngine = fixture.projectionEngine,
                blockStore = fixture.blockStore,
                ioDispatcher = Dispatchers.IO
            )
            try {
                assertTrue(fixture.files.searchIdx.exists())
                provider.invalidate()
                assertTrue(!fixture.files.searchIdx.exists())
            } finally {
                provider.close()
            }
        } finally {
            fixture.close()
        }
    }

    private fun buildLargeText(): String {
        val chunk = "abcdefghijklmnopqrstuvwxyz0123456789".repeat(700)
        return buildString {
            repeat(65) { index ->
                append(chunk)
                if (index % 5 == 0) {
                    append(" needle ")
                }
                append('\n')
            }
        }
    }
}
