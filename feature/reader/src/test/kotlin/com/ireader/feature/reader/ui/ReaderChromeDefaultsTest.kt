package com.ireader.feature.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChromeDefaultsTest {

    @Test
    fun `top bar actions should align with search and note semantics`() {
        assertEquals(ReaderTopActionIcon.Search, ReaderChromeDefaults.topSearchIcon)
        assertEquals(ReaderTopActionIcon.Note, ReaderChromeDefaults.topNotesIcon)
    }

    @Test
    fun `night mode toggle should only remain in bottom bar`() {
        val policy = ReaderChromeDefaults.nightModeEntryPolicy
        assertTrue(policy.showBottomBarToggle)
        assertFalse(policy.showSettingsDockToggle)
        assertFalse(policy.showFullSettingsToggle)
    }
}
