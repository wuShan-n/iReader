package com.ireader.engines.epub.internal.readium

import com.ireader.reader.api.render.RenderConfig
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

internal object ReadiumPreferencesMapper {

    fun fromRenderConfig(config: RenderConfig.ReflowText): EpubPreferences {
        val theme = when (config.extra["theme"]?.lowercase()) {
            "dark" -> Theme.DARK
            "sepia" -> Theme.SEPIA
            "light" -> Theme.LIGHT
            else -> null
        }
        val scroll = when (config.extra["scroll"]?.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
        val publisherStyles = when (config.extra["publisherStyles"]?.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }

        return EpubPreferences(
            fontFamily = config.fontFamilyName?.takeIf { it.isNotBlank() }?.let(::FontFamily),
            fontSize = (config.fontSizeSp / 16f).toDouble().coerceAtLeast(0.5),
            lineHeight = config.lineHeightMult.toDouble().coerceAtLeast(0.8),
            paragraphSpacing = (config.paragraphSpacingDp / 16f).toDouble().coerceAtLeast(0.0),
            pageMargins = (config.pagePaddingDp / 16f).toDouble().coerceAtLeast(0.0),
            hyphens = config.hyphenation,
            theme = theme,
            scroll = scroll,
            publisherStyles = publisherStyles
        )
    }
}
