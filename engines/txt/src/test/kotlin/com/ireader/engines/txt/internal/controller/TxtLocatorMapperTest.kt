package com.ireader.engines.txt.internal.controller

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TxtLocatorMapperTest {

    @Test
    fun locator_contains_snippet_and_progression() = runBlocking {
        val store = FakeStore("0123456789abcdefghijklmnopqrstuvwxyz")
        val mapper = TxtLocatorMapper(store, snippetLength = 12)

        val locator = mapper.locatorForOffset(offset = 10, totalChars = store.totalChars())

        assertEquals(LocatorSchemes.TXT_OFFSET, locator.scheme)
        assertEquals("10", locator.value)
        assertTrue(locator.extras.containsKey(TxtLocatorExtras.SNIPPET))
        assertTrue(locator.extras.containsKey(TxtLocatorExtras.PROGRESSION))
    }

    @Test
    fun offset_for_locator_prefers_snippet_relocation() = runBlocking {
        val oldText = "Chapter 1\nHello world\nEnd"
        val oldMapper = TxtLocatorMapper(FakeStore(oldText), snippetLength = 16)
        val locator = oldMapper.locatorForOffset(
            offset = oldText.indexOf("world"),
            totalChars = oldText.length
        )

        val newText = "Inserted preface\nChapter 1\nHello world\nEnd"
        val newMapper = TxtLocatorMapper(FakeStore(newText), snippetLength = 16)

        val resolved = newMapper.offsetForLocator(locator, newText.length)
        val expected = newText.indexOf("world")

        assertTrue(abs(resolved - expected) <= 32)
    }

    @Test
    fun offset_for_locator_falls_back_to_progression() = runBlocking {
        val text = "a".repeat(1000)
        val mapper = TxtLocatorMapper(FakeStore(text), snippetLength = 16)
        val locator = Locator(
            scheme = LocatorSchemes.TXT_OFFSET,
            value = "12",
            extras = mapOf(
                TxtLocatorExtras.PROGRESSION to "0.5"
            )
        )

        val resolved = mapper.offsetForLocator(locator, text.length)

        assertEquals(500, resolved)
    }

    private class FakeStore(
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
            val half = (maxChars / 2).coerceAtLeast(1)
            val start = (charOffset - half).coerceAtLeast(0)
            val end = (start + maxChars).coerceAtMost(content.length)
            return content.substring(start, end)
        }

        override fun close() = Unit
    }
}
