package com.ireader.engines.epub.internal.render

import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.READER_APPEARANCE_BG_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_DARK
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_LIGHT
import com.ireader.reader.api.render.RenderConfig
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalReadiumApi::class)
@RunWith(RobolectricTestRunner::class)
class EpubPreferencesMapperTest {

    @Test
    fun `reflow config should map and clamp values`() {
        val prefs = RenderConfig.ReflowText(
            fontSizeSp = 80f,
            lineHeightMult = 10f,
            paragraphSpacingDp = 200f,
            pagePaddingDp = 200f,
            fontFamilyName = "sans",
            hyphenationMode = HyphenationMode.FULL
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
        assertEquals(0.0, prefs.paragraphIndent!!, 0.0001)
        assertEquals(2.0, prefs.paragraphSpacing!!, 0.0001)
        assertEquals(4.0, prefs.pageMargins!!, 0.0001)
        assertEquals(false, prefs.scroll)
        assertEquals(false, prefs.publisherStyles)
        assertEquals(ReadiumTextAlign.JUSTIFY, prefs.textAlign)
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
    fun `font mapping should be locale-agnostic`() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            val prefs = RenderConfig.ReflowText(fontFamilyName = "SERIF").toEpubPreferences()
            assertEquals(FontFamily.SERIF, prefs.fontFamily)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `respect publisher styles should disable advanced overrides`() {
        val prefs = RenderConfig.ReflowText(
            respectPublisherStyles = true,
            lineHeightMult = 2.0f,
            paragraphSpacingDp = 10f,
            hyphenationMode = HyphenationMode.FULL
        ).toEpubPreferences()

        assertEquals(true, prefs.publisherStyles)
        assertNull(prefs.lineHeight)
        assertNull(prefs.paragraphIndent)
        assertNull(prefs.paragraphSpacing)
        assertNull(prefs.textAlign)
        assertNull(prefs.hyphens)
    }

    @Test
    fun `reflow appearance extras should map to readium theme and colors`() {
        val background = 0xFFF3E7CA.toInt()
        val text = 0xFF2D2A26.toInt()
        val prefs = RenderConfig.ReflowText(
            extra = mapOf(
                READER_APPEARANCE_BG_ARGB_EXTRA_KEY to background.toString(),
                READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY to text.toString(),
                READER_APPEARANCE_THEME_EXTRA_KEY to READER_APPEARANCE_THEME_LIGHT
            )
        ).toEpubPreferences()

        assertEquals(background, prefs.backgroundColor?.int)
        assertEquals(text, prefs.textColor?.int)
        assertEquals(Theme.LIGHT, prefs.theme)
    }

    @Test
    fun `fixed appearance extras should map to readium theme and colors`() {
        val background = 0xFF131313.toInt()
        val text = 0xFFBEB9B0.toInt()
        val prefs = RenderConfig.FixedPage(
            extra = mapOf(
                READER_APPEARANCE_BG_ARGB_EXTRA_KEY to background.toString(),
                READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY to text.toString(),
                READER_APPEARANCE_THEME_EXTRA_KEY to READER_APPEARANCE_THEME_DARK
            )
        ).toEpubPreferences()

        assertEquals(background, prefs.backgroundColor?.int)
        assertEquals(text, prefs.textColor?.int)
        assertEquals(Theme.DARK, prefs.theme)
    }
}
