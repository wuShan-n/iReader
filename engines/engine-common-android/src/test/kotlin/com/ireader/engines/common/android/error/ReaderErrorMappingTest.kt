package com.ireader.engines.common.android.error

import com.ireader.reader.api.error.ReaderError
import java.util.zip.ZipException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderErrorMappingTest {

    @Test
    fun `zip exception should map to corrupt error`() {
        val error = ZipException("bad zip").toReaderError()
        assertTrue(error is ReaderError.CorruptOrInvalid)
    }

    @Test
    fun `password keywords should map to invalid password`() {
        val error = IllegalStateException("document is encrypted").toReaderError(
            invalidPasswordKeywords = setOf("password", "encrypted")
        )
        assertTrue(error is ReaderError.InvalidPassword)
    }

    @Test
    fun `internal message should be dropped when disabled`() {
        val error = IllegalStateException("boom").toReaderError(
            preserveInternalMessage = false
        )
        assertTrue(error is ReaderError.Internal)
        assertEquals("Internal error", error.message)
    }
}
