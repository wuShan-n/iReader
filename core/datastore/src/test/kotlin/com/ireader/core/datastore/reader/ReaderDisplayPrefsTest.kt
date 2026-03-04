package com.ireader.core.datastore.reader

import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderDisplayPrefsTest {

    @Test
    fun `prevent accidental turn should be disabled by default`() {
        assertFalse(ReaderDisplayPrefs().preventAccidentalTurn)
    }
}
