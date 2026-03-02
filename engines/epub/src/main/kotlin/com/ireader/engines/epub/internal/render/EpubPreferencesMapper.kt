package com.ireader.engines.epub.internal.render

import com.ireader.reader.api.render.RenderConfig
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily

@OptIn(ExperimentalReadiumApi::class)
internal fun RenderConfig.toEpubPreferences(): EpubPreferences =
    when (this) {
        is RenderConfig.ReflowText -> {
            val baseSp = 16f
            val fontScale = (fontSizeSp / baseSp)
                .toDouble()
                .coerceIn(0.5, 4.0)

            EpubPreferences(
                fontSize = fontScale,
                lineHeight = lineHeightMult.toDouble().coerceIn(1.0, 3.0),
                paragraphSpacing = (paragraphSpacingDp / 16f).toDouble().coerceIn(0.0, 4.0),
                pageMargins = (pagePaddingDp / 16f).toDouble().coerceIn(0.0, 6.0),
                hyphens = hyphenation,
                fontFamily = fontFamilyName?.toReadiumFontFamily()
            )
        }

        is RenderConfig.FixedPage -> {
            EpubPreferences()
        }
    }

private fun String.toReadiumFontFamily(): FontFamily {
    val key = lowercase().trim()
    return when (key) {
        "serif" -> FontFamily.SERIF
        "sans-serif", "sans" -> FontFamily.SANS_SERIF
        "monospace", "mono" -> FontFamily.MONOSPACE
        "cursive" -> FontFamily.CURSIVE
        else -> FontFamily(key)
    }
}
