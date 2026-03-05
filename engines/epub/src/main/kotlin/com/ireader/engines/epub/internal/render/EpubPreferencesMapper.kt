package com.ireader.engines.epub.internal.render

import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.READER_APPEARANCE_BG_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_DARK
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_LIGHT
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.toTypographySpec
import java.util.Locale
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign

@OptIn(ExperimentalReadiumApi::class)
internal fun RenderConfig.toEpubPreferences(): EpubPreferences =
    when (this) {
        is RenderConfig.ReflowText -> {
            val appearance = resolveAppearance(extra)
            val typography = toTypographySpec()

            val baseSp = 16f
            val fontScale = (typography.fontSizeSp / baseSp)
                .toDouble()
                .coerceIn(0.5, 4.0)

            val advanced = !respectPublisherStyles

            EpubPreferences(
                backgroundColor = appearance.backgroundColor,
                fontSize = fontScale,
                scroll = false,
                pageMargins = (typography.pagePaddingDp / 16f).toDouble().coerceIn(0.0, 4.0),
                publisherStyles = respectPublisherStyles,
                lineHeight = if (advanced) typography.lineHeightMult.toDouble().coerceIn(1.0, 2.0) else null,
                paragraphSpacing = if (advanced) (typography.paragraphSpacingDp / 16f).toDouble().coerceIn(0.0, 2.0) else null,
                paragraphIndent = if (advanced) 0.0 else null,
                textAlign = if (advanced) ReadiumTextAlign.JUSTIFY else null,
                hyphens = if (advanced) typography.hyphenationMode != HyphenationMode.NONE else null,
                textColor = appearance.textColor,
                theme = appearance.theme,
                // Readium does not expose a direct equivalent for includeFontPadding/pageInsetMode.
                // Keep those settings as explicit no-op on EPUB to avoid implicit behavior drift.
                fontFamily = typography.fontFamilyName?.toReadiumFontFamilyOrNull()
            )
        }

        is RenderConfig.FixedPage -> {
            val appearance = resolveAppearance(extra)
            EpubPreferences(
                backgroundColor = appearance.backgroundColor,
                textColor = appearance.textColor,
                theme = appearance.theme
            )
        }
    }

private fun String.toReadiumFontFamilyOrNull(): FontFamily? {
    val key = lowercase(Locale.ROOT).trim()
    if (key.isBlank() || key == "系统字体") return null

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

private data class EpubAppearance(
    val backgroundColor: Color?,
    val textColor: Color?,
    val theme: Theme?
)

private fun resolveAppearance(extra: Map<String, String>): EpubAppearance {
    val backgroundColor = extra[READER_APPEARANCE_BG_ARGB_EXTRA_KEY]
        ?.toIntOrNull()
        ?.let(::Color)
    val textColor = extra[READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY]
        ?.toIntOrNull()
        ?.let(::Color)
    val theme = when (extra[READER_APPEARANCE_THEME_EXTRA_KEY]?.lowercase(Locale.ROOT)) {
        READER_APPEARANCE_THEME_LIGHT -> Theme.LIGHT
        READER_APPEARANCE_THEME_DARK -> Theme.DARK
        else -> null
    }
    return EpubAppearance(
        backgroundColor = backgroundColor,
        textColor = textColor,
        theme = theme
    )
}
