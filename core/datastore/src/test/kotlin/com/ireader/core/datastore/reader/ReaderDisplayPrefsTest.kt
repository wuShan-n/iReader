package com.ireader.core.datastore.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDisplayPrefsTest {

    @Test
    fun `prevent accidental turn should be disabled by default`() {
        assertFalse(ReaderDisplayPrefs().preventAccidentalTurn)
    }

    @Test
    fun `volume key paging should be enabled by default`() {
        assertTrue(ReaderDisplayPrefs().volumeKeyPagingEnabled)
    }
}
