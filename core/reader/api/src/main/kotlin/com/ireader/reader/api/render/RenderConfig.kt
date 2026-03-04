package com.ireader.reader.api.render

enum class TextAlignMode {
    START,
    JUSTIFY
}

enum class BreakStrategyMode {
    SIMPLE,
    BALANCED,
    HIGH_QUALITY
}

enum class HyphenationMode {
    NONE,
    NORMAL,
    FULL
}

enum class PageInsetMode {
    RELAXED,
    COMPACT
}

enum class PageTurnMode(val storageValue: String) {
    COVER_HORIZONTAL("cover_horizontal"),
    SCROLL_VERTICAL("scroll_vertical");

    companion object {
        fun fromStorageValue(raw: String?): PageTurnMode {
            return entries.firstOrNull { it.storageValue == raw } ?: COVER_HORIZONTAL
        }
    }
}

const val PAGE_TURN_EXTRA_KEY: String = "page_turn"
const val PAGE_TURN_STYLE_EXTRA_KEY: String = "page_turn_style"

sealed interface RenderConfig {

    data class ReflowText(
        val fontSizeSp: Float = 18f,
        val lineHeightMult: Float = 1.5f,
        val paragraphSpacingDp: Float = 6f,
        val paragraphIndentEm: Float = 1.8f,
        val pagePaddingDp: Float = 16f,
        val fontFamilyName: String? = null,
        val textAlign: TextAlignMode = TextAlignMode.JUSTIFY,
        val breakStrategy: BreakStrategyMode = BreakStrategyMode.BALANCED,
        val hyphenationMode: HyphenationMode = HyphenationMode.NORMAL,
        val includeFontPadding: Boolean = true,
        val cjkLineBreakStrict: Boolean = true,
        val hangingPunctuation: Boolean = false,
        val pageInsetMode: PageInsetMode = PageInsetMode.RELAXED,
        // Readium: whether to observe the original publisher styles. Many typography overrides
        // only apply when this is disabled.
        val respectPublisherStyles: Boolean = false,
        val extra: Map<String, String> = emptyMap()
    ) : RenderConfig

    data class FixedPage(
        val fitMode: FitMode = FitMode.FIT_WIDTH,
        val zoom: Float = 1.0f,
        val rotationDegrees: Int = 0,
        val extra: Map<String, String> = emptyMap()
    ) : RenderConfig

    enum class FitMode { FIT_WIDTH, FIT_HEIGHT, FIT_PAGE, FREE }

    companion object {
        val Default: RenderConfig = ReflowText()
    }
}

