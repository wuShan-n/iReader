package com.ireader.reader.api.render

fun RenderConfig.ReflowText.effectivePagePaddingDp(): Float {
    return when (pageInsetMode) {
        PageInsetMode.RELAXED -> pagePaddingDp
        PageInsetMode.COMPACT -> (pagePaddingDp * 0.75f).coerceAtLeast(6f)
    }
}

fun RenderConfig.ReflowText.effectiveParagraphSpacingDp(): Float {
    return when (pageInsetMode) {
        PageInsetMode.RELAXED -> paragraphSpacingDp
        PageInsetMode.COMPACT -> (paragraphSpacingDp * 0.8f).coerceAtLeast(0f)
    }
}

fun RenderConfig.ReflowText.effectiveParagraphIndentEm(): Float {
    return paragraphIndentEm.coerceAtLeast(0f)
}
