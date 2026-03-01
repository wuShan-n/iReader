package com.ireader.reader.api.render

sealed interface RenderConfig {

    data class ReflowText(
        val fontSizeSp: Float = 18f,
        val lineHeightMult: Float = 1.5f,
        val paragraphSpacingDp: Float = 6f,
        val pagePaddingDp: Float = 16f,
        val fontFamilyName: String? = null,
        val hyphenation: Boolean = false,
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

