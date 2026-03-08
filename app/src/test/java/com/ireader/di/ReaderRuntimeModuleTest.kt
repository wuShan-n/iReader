package com.ireader.di

import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.runtime.DefaultReaderRuntime
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRuntimeModuleTest {

    @Test
    fun `provideEngineRegistry should map formats to matching engine`() {
        val epubEngine = FakeEngine(setOf(BookFormat.EPUB))
        val pdfEngine = FakeEngine(setOf(BookFormat.PDF))

        val registry = ReaderRuntimeModule.provideEngineRegistry(setOf(epubEngine, pdfEngine))

        assertSame(epubEngine, registry.engineFor(BookFormat.EPUB))
        assertSame(pdfEngine, registry.engineFor(BookFormat.PDF))
        assertNull(registry.engineFor(BookFormat.TXT))
    }

    @Test
    fun `provideEngineRegistry should fail when two engines share same format`() {
        val first = FakeEngine(setOf(BookFormat.EPUB))
        val second = FakeEngine(setOf(BookFormat.EPUB))

        val error = runCatching {
            ReaderRuntimeModule.provideEngineRegistry(setOf(first, second))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `provideReaderRuntime should return default runtime backed by registry`() {
        val engine = FakeEngine(setOf(BookFormat.TXT))
        val registry: EngineRegistry = ReaderRuntimeModule.provideEngineRegistry(setOf(engine))

        val runtime = ReaderRuntimeModule.provideReaderRuntime(registry)

        assertTrue(runtime is DefaultReaderRuntime)
    }
}

private class FakeEngine(
    override val supportedFormats: Set<BookFormat>
) : ReaderEngine {
    override suspend fun open(source: DocumentSource, options: OpenOptions): ReaderResult<ReaderDocument> {
        return ReaderResult.Err(ReaderError.NotFound("not used in module wiring tests"))
    }
}
