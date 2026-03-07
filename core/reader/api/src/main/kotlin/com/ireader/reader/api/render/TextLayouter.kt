package com.ireader.reader.api.render

data class TextLayoutInput(
    val widthPx: Int,
    val heightPx: Int,
    val fontSizeSp: Float,
    val lineHeightMult: Float,
    val textAlign: TextAlignMode,
    val breakStrategy: BreakStrategyMode,
    val hyphenationMode: HyphenationMode,
    val includeFontPadding: Boolean,
    val fontFamilyName: String? = null,
    val paragraphSpacingPx: Int = 0
)

data class TextLayoutMeasureResult(
    val endChar: Int,
    val lineCount: Int,
    val lastVisibleLine: Int
)

interface TextLayouter {
    fun measure(text: CharSequence, input: TextLayoutInput): TextLayoutMeasureResult
}

interface TextLayouterFactory {
    val environmentKey: String

    fun create(cacheSize: Int = 0): TextLayouter
}
