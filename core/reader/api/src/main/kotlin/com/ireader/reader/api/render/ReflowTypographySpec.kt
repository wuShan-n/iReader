package com.ireader.reader.api.render

data class ReflowTypographySpec(
    val fontSizeSp: Float,
    val lineHeightMult: Float,
    val paragraphSpacingDp: Float,
    val pagePaddingDp: Float,
    val fontFamilyName: String?,
    val breakStrategy: BreakStrategyMode,
    val hyphenationMode: HyphenationMode,
    val includeFontPadding: Boolean
)

fun RenderConfig.ReflowText.toTypographySpec(): ReflowTypographySpec {
    return ReflowTypographySpec(
        fontSizeSp = fontSizeSp,
        lineHeightMult = lineHeightMult,
        paragraphSpacingDp = effectiveParagraphSpacingDp(),
        pagePaddingDp = effectivePagePaddingDp(),
        fontFamilyName = fontFamilyName,
        breakStrategy = breakStrategy,
        hyphenationMode = hyphenationMode,
        includeFontPadding = includeFontPadding
    )
}
