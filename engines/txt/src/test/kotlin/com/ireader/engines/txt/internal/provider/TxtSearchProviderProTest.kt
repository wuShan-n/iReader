package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.search.TrigramBloomIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.engines.txt.testing.createBookFiles
import com.ireader.engines.txt.testing.writeUtf16Text
import com.ireader.reader.api.provider.SearchOptions
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtSearchProviderProTest {

    @Test
    fun `streaming scan should honor whole word and start offset`() = runBlocking {
        val fixture = createFixture(
            text = "alpha beta alphabeta ALPHA\\nline two alpha\\nline three alpha\\n",
            sampleHash = "streaming"
        )
        try {
            val provider = TxtSearchProviderPro(
                files = fixture.files,
                store = fixture.store,
                meta = fixture.meta,
                ioDispatcher = Dispatchers.IO
            )

            val allHits = provider.search(
                query = "alpha",
                options = SearchOptions(caseSensitive = false, wholeWord = true, maxHits = 20)
            ).toList()
            val starts = allHits.mapNotNull { TxtBlockLocatorCodec.parseOffset(it.range.start) }
            assertEquals(4, starts.size)

            val secondLineOffset = fixture.text.indexOf("line two").toLong()
            val fromSecondLine = provider.search(
                query = "alpha",
                options = SearchOptions(
                    caseSensitive = false,
                    wholeWord = true,
                    maxHits = 20,
                    startFrom = TxtBlockLocatorCodec.locatorForOffset(secondLineOffset, fixture.store.lengthChars)
                )
            ).toList()
            val fromSecondStarts = fromSecondLine.mapNotNull { TxtBlockLocatorCodec.parseOffset(it.range.start) }
            assertEquals(2, fromSecondStarts.size)
            assertTrue(fromSecondStarts.all { it >= secondLineOffset })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `bloom path should return limited and deduplicated hits`() = runBlocking {
        val largeText = buildLargeText()
        val fixture = createFixture(
            text = largeText,
            sampleHash = "bloom"
        )
        try {
            TrigramBloomIndex.buildIfNeeded(
                file = fixture.files.searchIdx,
                lockFile = fixture.files.searchLock,
                store = fixture.store,
                meta = fixture.meta,
                ioDispatcher = Dispatchers.IO
            )
            assertTrue(fixture.files.searchIdx.exists())

            val provider = TxtSearchProviderPro(
                files = fixture.files,
                store = fixture.store,
                meta = fixture.meta,
                ioDispatcher = Dispatchers.IO
            )
            val hits = provider.search(
                query = "needle",
                options = SearchOptions(maxHits = 7)
            ).toList()
            val starts = hits.mapNotNull { TxtBlockLocatorCodec.parseOffset(it.range.start) }

            assertTrue(starts.isNotEmpty())
            assertTrue(starts.size <= 7)
            assertEquals(starts.size, starts.toSet().size)
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

    private fun createFixture(text: String, sampleHash: String): SearchFixture {
        val root = Files.createTempDirectory("txt_search_provider").toFile()
        val files = createBookFiles(root)
        writeUtf16Text(files.textStore, text)
        val store = Utf16TextStore(files.textStore)
        return SearchFixture(
            text = text,
            files = files,
            store = store,
            meta = TxtMeta(
                version = 1,
                sourceUri = "file://search.txt",
                displayName = "search.txt",
                sizeBytes = text.length.toLong(),
                sampleHash = sampleHash,
                originalCharset = "UTF-8",
                lengthChars = store.lengthChars,
                hardWrapLikely = false,
                createdAtEpochMs = 0L
            ),
            rootDir = root
        )
    }

    private class SearchFixture(
        val text: String,
        val files: com.ireader.engines.txt.internal.open.TxtBookFiles,
        val store: Utf16TextStore,
        val meta: TxtMeta,
        private val rootDir: File
    ) {
        fun close() {
            store.close()
            rootDir.deleteRecursively()
        }
    }
}
