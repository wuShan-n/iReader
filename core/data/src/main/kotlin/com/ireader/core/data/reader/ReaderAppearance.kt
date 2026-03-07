package com.ireader.core.data.reader

import com.ireader.core.datastore.reader.ReaderBackgroundPreset
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.reader.api.render.READER_APPEARANCE_BG_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_DARK
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_LIGHT
import com.ireader.reader.api.render.RenderConfig

internal data class ReaderAppearance(
    val backgroundArgb: Int,
    val textArgb: Int,
    val theme: String
)

internal fun resolveReaderAppearance(prefs: ReaderDisplayPrefs): ReaderAppearance {
    return when (prefs.backgroundPreset) {
        ReaderBackgroundPreset.SYSTEM -> {
            if (prefs.nightMode) {
                ReaderAppearance(
                    backgroundArgb = BACKGROUND_SYSTEM_NIGHT,
                    textArgb = TEXT_NIGHT,
                    theme = READER_APPEARANCE_THEME_DARK
                )
            } else {
                ReaderAppearance(
                    backgroundArgb = BACKGROUND_SYSTEM_DAY,
                    textArgb = TEXT_DAY,
                    theme = READER_APPEARANCE_THEME_LIGHT
                )
            }
        }

        ReaderBackgroundPreset.PAPER -> ReaderAppearance(
            backgroundArgb = BACKGROUND_PAPER,
            textArgb = TEXT_DAY,
            theme = READER_APPEARANCE_THEME_LIGHT
        )

        ReaderBackgroundPreset.WARM -> ReaderAppearance(
            backgroundArgb = BACKGROUND_WARM,
            textArgb = TEXT_DAY,
            theme = READER_APPEARANCE_THEME_LIGHT
        )

        ReaderBackgroundPreset.GREEN -> ReaderAppearance(
            backgroundArgb = BACKGROUND_GREEN,
            textArgb = TEXT_DAY,
            theme = READER_APPEARANCE_THEME_LIGHT
        )

        ReaderBackgroundPreset.DARK -> ReaderAppearance(
            backgroundArgb = BACKGROUND_DARK,
            textArgb = TEXT_NIGHT,
            theme = READER_APPEARANCE_THEME_DARK
        )

        ReaderBackgroundPreset.NAVY -> ReaderAppearance(
            backgroundArgb = BACKGROUND_NAVY,
            textArgb = TEXT_NIGHT,
            theme = READER_APPEARANCE_THEME_DARK
        )
    }
}

fun RenderConfig.withReaderAppearance(prefs: ReaderDisplayPrefs): RenderConfig {
    return withReaderAppearance(resolveReaderAppearance(prefs))
}

internal fun RenderConfig.withReaderAppearance(appearance: ReaderAppearance): RenderConfig {
    val updatedExtra = mapOf(
        READER_APPEARANCE_BG_ARGB_EXTRA_KEY to appearance.backgroundArgb.toString(),
        READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY to appearance.textArgb.toString(),
        READER_APPEARANCE_THEME_EXTRA_KEY to appearance.theme
    )
    return when (this) {
        is RenderConfig.ReflowText -> copy(extra = extra + updatedExtra)
        is RenderConfig.FixedPage -> copy(extra = extra + updatedExtra)
    }
}

private const val BACKGROUND_SYSTEM_DAY: Int = 0xFFFDF9F3.toInt()
private const val BACKGROUND_SYSTEM_NIGHT: Int = 0xFF131313.toInt()
private const val BACKGROUND_PAPER: Int = 0xFFFDF9F3.toInt()
private const val BACKGROUND_WARM: Int = 0xFFF3E7CA.toInt()
private const val BACKGROUND_GREEN: Int = 0xFFCCE0D1.toInt()
private const val BACKGROUND_DARK: Int = 0xFF2B2B2B.toInt()
private const val BACKGROUND_NAVY: Int = 0xFF1A1F2B.toInt()
private const val TEXT_DAY: Int = 0xFF2D2A26.toInt()
private const val TEXT_NIGHT: Int = 0xFFBEB9B0.toInt()
