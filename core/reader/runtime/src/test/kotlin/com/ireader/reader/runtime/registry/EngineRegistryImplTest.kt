package com.ireader.reader.runtime.registry

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EngineRegistryImplTest {

    @Test
    fun `engineFor returns engine mapped by format`() {
        val txt = FakeEngine(setOf(BookFormat.TXT))
        val epub = FakeEngine(setOf(BookFormat.EPUB))

        val registry = EngineRegistryImpl(setOf(txt, epub))

        assertSame(txt, registry.engineFor(BookFormat.TXT))
        assertSame(epub, registry.engineFor(BookFormat.EPUB))
        assertEquals(null, registry.engineFor(BookFormat.PDF))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate format throws`() {
        val first = FakeEngine(setOf(BookFormat.PDF))
        val second = FakeEngine(setOf(BookFormat.PDF))

        EngineRegistryImpl(setOf(first, second))
    }

    private class FakeEngine(
        override val supportedFormats: Set<BookFormat>
    ) : ReaderEngine {
        override suspend fun open(
            source: DocumentSource,
            options: OpenOptions
        ): ReaderResult<ReaderDocument> {
            error("Not required in this test")
        }
    }
}
