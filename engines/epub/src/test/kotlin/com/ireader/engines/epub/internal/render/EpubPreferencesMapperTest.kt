package com.ireader.engines.epub.internal.render

import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class EpubPreferencesMapperTest {

    @Test
    fun `reflow config should map and clamp values`() {
        val prefs = RenderConfig.ReflowText(
            fontSizeSp = 80f,
            lineHeightMult = 10f,
            paragraphSpacingDp = 200f,
            pagePaddingDp = 200f,
            fontFamilyName = "sans",
            hyphenation = true
        ).toEpubPreferences()

        assertNotNull(prefs.fontSize)
        assertNotNull(prefs.lineHeight)
        assertNotNull(prefs.paragraphSpacing)
        assertNotNull(prefs.pageMargins)
        assertEquals(4.0, prefs.fontSize!!, 0.0001)
        assertEquals(3.0, prefs.lineHeight!!, 0.0001)
        assertEquals(4.0, prefs.paragraphSpacing!!, 0.0001)
        assertEquals(6.0, prefs.pageMargins!!, 0.0001)
        assertFalse(prefs.fontFamily == null)
    }

    @Test
    fun `fixed page config should map to defaults`() {
        val prefs = RenderConfig.FixedPage().toEpubPreferences()
        assertNull(prefs.fontSize)
    }
}
