package com.ireader.reader.api.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderResultExtTest {

    @Test
    fun `map should transform ok and keep err`() {
        val ok = ReaderResult.Ok(2)
        val err: ReaderResult<Int> = ReaderResult.Err(ReaderError.NotFound())

        val mappedOk = ok.map { it * 3 }
        val mappedErr = err.map { it * 3 }

        assertEquals(6, (mappedOk as ReaderResult.Ok).value)
        assertSame(err, mappedErr)
    }

    @Test
    fun `flatMap should chain ok and skip err`() {
        val ok = ReaderResult.Ok("a")
        val err: ReaderResult<String> = ReaderResult.Err(ReaderError.NotFound())

        val chainedOk = ok.flatMap { ReaderResult.Ok(it + "b") }
        var invoked = false
        val chainedErr = err.flatMap {
            invoked = true
            ReaderResult.Ok(it + "b")
        }

        assertEquals("ab", (chainedOk as ReaderResult.Ok).value)
        assertSame(err, chainedErr)
        assertFalse(invoked)
    }

    @Test
    fun `mapError should transform err and keep ok`() {
        val ok = ReaderResult.Ok(7)
        val err: ReaderResult<Int> = ReaderResult.Err(ReaderError.NotFound())

        val mappedOk = ok.mapError { ReaderError.Internal(message = "x") }
        val mappedErr = err.mapError { ReaderError.Internal(message = "x") }

        assertSame(ok, mappedOk)
        assertEquals("INTERNAL", (mappedErr as ReaderResult.Err).error.code)
    }

    @Test
    fun `fold and side-effect helpers should choose correct branch`() {
        val ok = ReaderResult.Ok("done")
        val err: ReaderResult<String> = ReaderResult.Err(ReaderError.NotFound())
        var okCalled = false
        var errCalled = false

        val okFold = ok.fold(onOk = { "ok:$it" }, onErr = { "err:${it.code}" })
        val errFold = err.fold(onOk = { "ok:$it" }, onErr = { "err:${it.code}" })

        ok.onOk { okCalled = true }.onErr { errCalled = true }
        err.onOk { okCalled = true }.onErr { errCalled = true }

        assertEquals("ok:done", okFold)
        assertEquals("err:NOT_FOUND", errFold)
        assertTrue(okCalled)
        assertTrue(errCalled)
    }

    @Test
    fun `getOrNull and getOrThrow should expose value semantics`() {
        val ok = ReaderResult.Ok(11)
        val err: ReaderResult<Int> = ReaderResult.Err(ReaderError.NotFound())

        assertEquals(11, ok.getOrNull())
        assertNull(err.getOrNull())
        assertEquals(11, ok.getOrThrow())
        assertEquals("NOT_FOUND", runCatching { err.getOrThrow() }.exceptionOrNull()?.let {
            (it as ReaderError).code
        })
    }
}
