package com.ireader.engines.txt.internal.paging

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TxtPagerConsistencyTest {

    @Test
    fun page_boundaries_are_stable_for_same_input() = runBlocking {
        val text = buildString {
            repeat(400) { chapter ->
                append("Chapter ")
                append(chapter)
                append('\n')
                append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ")
                append("Vestibulum porta mauris id sem suscipit, at varius orci vehicula.\n\n")
            }
        }
        val store = InMemoryStore(text)
        val pager = TxtPager(store = store, chunkSizeChars = 8 * 1024)
        val constraints = LayoutConstraints(
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            density = 3f,
            fontScale = 1f
        )
        val config = RenderConfig.ReflowText(
            fontSizeSp = 18f,
            lineHeightMult = 1.4f,
            pagePaddingDp = 16f
        )

        val first = pager.pageAt(0, constraints, config)
        val second = pager.pageAt(0, constraints, config)
        val third = pager.pageAt(first.endChar, constraints, config)
        val fourth = pager.pageAt(first.endChar, constraints, config)

        assertEquals(first.startChar, second.startChar)
        assertEquals(first.endChar, second.endChar)
        assertEquals(first.text, second.text)
        assertEquals(third.startChar, fourth.startChar)
        assertEquals(third.endChar, fourth.endChar)
        assertEquals(third.text, fourth.text)
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
            val end = (start + maxChars).coerceAtMost(content.length)
            return content.substring(start, end)
        }

        override suspend fun readAround(charOffset: Int, maxChars: Int): String {
            val safeMax = maxChars.coerceAtLeast(1)
            val half = safeMax / 2
            val center = charOffset.coerceIn(0, content.length)
            val start = (center - half).coerceAtLeast(0)
            val end = (start + safeMax).coerceAtMost(content.length)
            return content.substring(start, end)
        }

        override fun close() = Unit
    }
}
