package com.ireader.feature.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChromeDefaultsTest {

    @Test
    fun `top bar actions should align with search and note semantics`() {
        assertEquals(ReaderTopActionIcon.Search, ReaderChromeDefaults.topSearchIcon)
        assertEquals(ReaderTopActionIcon.Note, ReaderChromeDefaults.topNotesIcon)
    }
}
