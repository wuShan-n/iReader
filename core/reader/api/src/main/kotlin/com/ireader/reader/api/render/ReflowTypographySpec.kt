package com.ireader.reader.api.render

data class ReflowTypographySpec(
    val fontSizeSp: Float,
    val lineHeightMult: Float,
    val paragraphSpacingDp: Float,
    val paragraphIndentEm: Float,
    val pagePaddingDp: Float,
    val fontFamilyName: String?,
    val textAlign: TextAlignMode,
    val breakStrategy: BreakStrategyMode,
    val hyphenationMode: HyphenationMode,
    val includeFontPadding: Boolean,
    val cjkLineBreakStrict: Boolean,
    val hangingPunctuation: Boolean
)

fun RenderConfig.ReflowText.toTypographySpec(): ReflowTypographySpec {
    return ReflowTypographySpec(
        fontSizeSp = fontSizeSp,
        lineHeightMult = lineHeightMult,
        paragraphSpacingDp = effectiveParagraphSpacingDp(),
        paragraphIndentEm = effectiveParagraphIndentEm(),
        pagePaddingDp = effectivePagePaddingDp(),
        fontFamilyName = fontFamilyName,
        textAlign = textAlign,
        breakStrategy = breakStrategy,
        hyphenationMode = hyphenationMode,
        includeFontPadding = includeFontPadding,
        cjkLineBreakStrict = cjkLineBreakStrict,
        hangingPunctuation = hangingPunctuation
    )
}
