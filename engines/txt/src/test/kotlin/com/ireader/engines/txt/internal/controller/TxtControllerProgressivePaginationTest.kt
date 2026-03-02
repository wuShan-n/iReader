package com.ireader.engines.txt.internal.controller

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.internal.paging.TxtLastPositionStore
import com.ireader.engines.txt.internal.paging.TxtPager
import com.ireader.engines.txt.internal.paging.TxtPaginationStore
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TxtControllerProgressivePaginationTest {

    @Test
    fun progressivePagination_switchesFromEstimateToKnownPageCount_andSupportsReflowPageJump() = runBlocking {
        val content = buildString {
            repeat(800) { chapter ->
                append("Chapter ")
                append(chapter)
                append('\n')
                append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ")
                append("Vestibulum porta mauris id sem suscipit, at varius orci vehicula.\n\n")
            }
        }
        val store = InMemoryStore(content)
        val config = TxtEngineConfig(
            persistPagination = false,
            persistLastPosition = false,
            prefetchAhead = 0,
            prefetchBehind = 0,
            chunkSizeChars = 8 * 1024
        ).normalized()

        val controller = TxtController(
            store = store,
            pager = TxtPager(store = store, chunkSizeChars = config.chunkSizeChars),
            ioDispatcher = Dispatchers.Default,
            annotations = null,
            paginationStore = TxtPaginationStore(
                config = config,
                docNamespace = "test-doc",
                charsetName = "UTF-8"
            ),
            lastPositionStore = TxtLastPositionStore(
                config = config,
                docNamespace = "test-doc",
                charsetName = "UTF-8"
            ),
            explicitInitial = true,
            documentId = DocumentId("doc:test"),
            initialStartChar = 0,
            locatorMapper = TxtLocatorMapper(store = store),
            engineConfig = config
        )

        try {
            controller.setLayoutConstraints(
                LayoutConstraints(
                    viewportWidthPx = 1080,
                    viewportHeightPx = 1920,
                    density = 3f,
                    fontScale = 1f
                )
            ).requireOk()

            val firstPage = controller.render(RenderPolicy(prefetchNeighbors = 0)).requireOk()
            assertEquals("false", firstPage.locator.extras["pageCountKnown"])
            assertTrue((firstPage.locator.extras["pageCountEstimate"]?.toIntOrNull() ?: 0) >= 1)

            val knownPage = withTimeout(4_000) {
                var latest = firstPage
                while (true) {
                    delay(50)
                    latest = controller.render(RenderPolicy(prefetchNeighbors = 0)).requireOk()
                    if (latest.locator.extras["pageCountKnown"] == "true") {
                        break
                    }
                }
                latest
            }

            val knownPageCount = knownPage.locator.extras["pageCount"]?.toIntOrNull() ?: 0
            assertTrue(knownPageCount >= 1)
            assertEquals("true", knownPage.locator.extras["pageCountKnown"])

            val targetIndex = (knownPageCount - 1).coerceAtMost(2).coerceAtLeast(0)
            val targetLocator = Locator(
                scheme = LocatorSchemes.REFLOW_PAGE,
                value = targetIndex.toString(),
                extras = mapOf("pageIndex" to targetIndex.toString())
            )
            val jumped = controller.goTo(targetLocator, RenderPolicy(prefetchNeighbors = 0)).requireOk()
            assertEquals(targetIndex.toString(), jumped.locator.extras["pageIndex"])
            assertEquals("true", jumped.locator.extras["pageCountKnown"])
        } finally {
            controller.close()
        }
    }

    private fun <T> ReaderResult<T>.requireOk(): T = when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> throw AssertionError("Expected ReaderResult.Ok but got error: ${error.message}")
    }

    private class InMemoryStore(
        private val content: String
    ) : TxtTextStore {
        override val charset: Charset = Charsets.UTF_8

        override suspend fun totalChars(): Int = content.length

        override suspend fun readRange(startChar: Int, endCharExclusive: Int): String {
            val start = startChar.coerceIn(0, content.length)
            val end = endCharExclusive.coerceIn(start, content.length)
            return content.substring(start, end)
        }

        override suspend fun readChars(startChar: Int, maxChars: Int): String {
            val start = startChar.coerceIn(0, content.length)
            val end = (start + maxChars.coerceAtLeast(0)).coerceAtMost(content.length)
            return content.substring(start, end)
        }

        override suspend fun readAround(charOffset: Int, maxChars: Int): String {
            val safe = maxChars.coerceAtLeast(1)
            val half = safe / 2
            val center = charOffset.coerceIn(0, content.length)
            val start = (center - half).coerceAtLeast(0)
            val end = (start + safe).coerceAtMost(content.length)
            return content.substring(start, end)
        }

        override fun close() = Unit
    }
}
