package com.ireader.engines.epub.internal.render

import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.toTypographySpec
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily

@OptIn(ExperimentalReadiumApi::class)
internal fun RenderConfig.toEpubPreferences(): EpubPreferences =
    when (this) {
        is RenderConfig.ReflowText -> {
            val typography = toTypographySpec()
            val baseSp = 16f
            val fontScale = (typography.fontSizeSp / baseSp)
                .toDouble()
                .coerceIn(0.5, 4.0)

            EpubPreferences(
                fontSize = fontScale,
                lineHeight = typography.lineHeightMult.toDouble().coerceIn(1.0, 3.0),
                paragraphSpacing = (typography.paragraphSpacingDp / 16f).toDouble().coerceIn(0.0, 4.0),
                pageMargins = (typography.pagePaddingDp / 16f).toDouble().coerceIn(0.0, 6.0),
                hyphens = typography.hyphenationMode != HyphenationMode.NONE,
                // Readium does not expose a direct equivalent for includeFontPadding/pageInsetMode.
                // Keep those settings as explicit no-op on EPUB to avoid implicit behavior drift.
                fontFamily = typography.fontFamilyName?.toReadiumFontFamilyOrNull()
            )
        }

        is RenderConfig.FixedPage -> EpubPreferences()
    }

private fun String.toReadiumFontFamilyOrNull(): FontFamily? {
    val key = lowercase().trim()
    if (key.isBlank() || key == "系统字体") {
        return null
    }
    return when (key) {
        "serif" -> FontFamily.SERIF
        "sans-serif", "sans" -> FontFamily.SANS_SERIF
        "monospace", "mono" -> FontFamily.MONOSPACE
        "cursive" -> FontFamily.CURSIVE
        // Map common Chinese names from settings panel to generic stacks.
        "思源宋体", "方正新楷体", "霞鹜文楷" -> FontFamily.SERIF
        else -> FontFamily(key)
    }
}
