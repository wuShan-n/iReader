package com.ireader.core.work

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportErrorMapperTest {

    @Test
    fun `reader error should preserve code`() {
        val error = ReaderError.UnsupportedFormat(detected = "zip")

        val (code, message) = error.toImportError()

        assertEquals("UNSUPPORTED_FORMAT", code)
        assertEquals(error.message ?: "UNSUPPORTED_FORMAT", message)
    }

    @Test
    fun `security related errors should map to stable codes`() {
        assertEquals("NOT_FOUND", FileNotFoundException("x").toImportError().first)
        assertEquals("PERMISSION_DENIED", SecurityException("x").toImportError().first)
        assertEquals("CORRUPT_OR_INVALID", ZipException("x").toImportError().first)
        assertEquals("IO", IOException("x").toImportError().first)
        assertEquals("CANCELLED", CancellationException("x").toImportError().first)
    }
}

