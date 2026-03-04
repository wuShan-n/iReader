package com.ireader.feature.reader.ui.components

import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.PageInsetMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlPageTypographyCssTest {

    @Test
    fun `buildReflowCss should map compact typography values`() {
        val css = buildReflowCss(
            RenderConfig.ReflowText(
                fontSizeSp = 20f,
                lineHeightMult = 1.7f,
                paragraphSpacingDp = 10f,
                paragraphIndentEm = 2.0f,
                pagePaddingDp = 16f,
                textAlign = TextAlignMode.JUSTIFY,
                hyphenationMode = HyphenationMode.FULL,
                cjkLineBreakStrict = true,
                hangingPunctuation = true,
                pageInsetMode = PageInsetMode.COMPACT
            )
        )

        assertTrue(css.contains("--ireader-font-size: 20px;"))
        assertTrue(css.contains("--ireader-line-height: 1.7;"))
        assertTrue(css.contains("--ireader-page-padding: 12px;"))
        assertTrue(css.contains("--ireader-paragraph-spacing: 8px;"))
        assertTrue(css.contains("--ireader-paragraph-indent: 2em;"))
        assertTrue(css.contains("text-align: justify !important;"))
        assertTrue(css.contains("hyphens: auto !important;"))
        assertTrue(css.contains("line-break: strict !important;"))
        assertTrue(css.contains("hanging-punctuation: first allow-end last !important;"))
    }

    @Test
    fun `buildReflowCss should map text alignment and hyphenation toggles`() {
        val css = buildReflowCss(
            RenderConfig.ReflowText(
                textAlign = TextAlignMode.START,
                hyphenationMode = HyphenationMode.NONE,
                cjkLineBreakStrict = false,
                hangingPunctuation = false
            )
        )

        assertTrue(css.contains("text-align: start !important;"))
        assertTrue(css.contains("hyphens: manual !important;"))
        assertTrue(css.contains("line-break: auto !important;"))
        assertTrue(css.contains("hanging-punctuation: none !important;"))
    }

    @Test
    fun `buildReflowCss should return empty css for null config`() {
        assertEquals("", buildReflowCss(config = null))
    }
}
