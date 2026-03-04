package com.ireader.engines.epub.internal.render

import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.RenderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign

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
            hyphenationMode = HyphenationMode.FULL,
            textAlign = TextAlignMode.START,
            paragraphIndentEm = 10f,
            extra = mapOf(PAGE_TURN_EXTRA_KEY to PageTurnMode.SCROLL_VERTICAL.storageValue)
        ).toEpubPreferences()

        assertNotNull(prefs.fontSize)
        assertNotNull(prefs.lineHeight)
        assertNotNull(prefs.paragraphIndent)
        assertNotNull(prefs.paragraphSpacing)
        assertNotNull(prefs.pageMargins)
        assertNotNull(prefs.scroll)
        assertNotNull(prefs.publisherStyles)
        assertNotNull(prefs.textAlign)
        assertEquals(4.0, prefs.fontSize!!, 0.0001)
        assertEquals(2.0, prefs.lineHeight!!, 0.0001)
        assertEquals(3.0, prefs.paragraphIndent!!, 0.0001)
        assertEquals(2.0, prefs.paragraphSpacing!!, 0.0001)
        assertEquals(4.0, prefs.pageMargins!!, 0.0001)
        assertEquals(true, prefs.scroll)
        assertEquals(false, prefs.publisherStyles)
        assertEquals(ReadiumTextAlign.START, prefs.textAlign)
        assertFalse(prefs.fontFamily == null)
    }

    @Test
    fun `fixed page config should map to defaults`() {
        val prefs = RenderConfig.FixedPage().toEpubPreferences()
        assertNull(prefs.fontSize)
    }

    @Test
    fun `system font should map to null family`() {
        val prefs = RenderConfig.ReflowText(fontFamilyName = "系统字体").toEpubPreferences()
        assertNull(prefs.fontFamily)
    }

    @Test
    fun `known chinese font names should map to serif family`() {
        val prefs = RenderConfig.ReflowText(fontFamilyName = "思源宋体").toEpubPreferences()
        assertEquals(FontFamily.SERIF, prefs.fontFamily)
    }

    @Test
    fun `respect publisher styles should disable advanced overrides`() {
        val prefs = RenderConfig.ReflowText(
            respectPublisherStyles = true,
            lineHeightMult = 2.0f,
            paragraphSpacingDp = 10f,
            paragraphIndentEm = 2.0f,
            textAlign = TextAlignMode.JUSTIFY,
            hyphenationMode = HyphenationMode.FULL
        ).toEpubPreferences()

        assertEquals(true, prefs.publisherStyles)
        assertNull(prefs.lineHeight)
        assertNull(prefs.paragraphIndent)
        assertNull(prefs.paragraphSpacing)
        assertNull(prefs.textAlign)
        assertNull(prefs.hyphens)
    }
}
