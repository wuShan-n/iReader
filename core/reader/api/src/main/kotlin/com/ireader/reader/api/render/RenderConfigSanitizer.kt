package com.ireader.reader.api.render

import com.ireader.reader.model.DocumentCapabilities

object RenderConfigSanitizer {

    fun sanitize(config: RenderConfig, capabilities: DocumentCapabilities): RenderConfig {
        return if (capabilities.fixedLayout) {
            val fixed = config as? RenderConfig.FixedPage ?: RenderConfig.FixedPage()
            fixed.sanitized()
        } else {
            val reflow = config as? RenderConfig.ReflowText ?: RenderConfig.ReflowText()
            reflow.sanitized()
        }
    }
}

fun RenderConfig.ReflowText.sanitized(): RenderConfig.ReflowText {
    val defaults = RenderConfig.ReflowText()
    return copy(
        fontSizeSp = fontSizeSp.finiteOr(defaults.fontSizeSp).coerceIn(8f, 72f),
        lineHeightMult = lineHeightMult.finiteOr(defaults.lineHeightMult).coerceIn(1.0f, 3.0f),
        paragraphSpacingDp = paragraphSpacingDp.finiteOr(defaults.paragraphSpacingDp).coerceIn(0f, 64f),
        pagePaddingDp = pagePaddingDp.finiteOr(defaults.pagePaddingDp).coerceIn(0f, 64f)
    )
}

fun RenderConfig.FixedPage.sanitized(): RenderConfig.FixedPage {
    val defaults = RenderConfig.FixedPage()
    val rotation = rotationDegrees.finiteRotationDegrees()
    return copy(
        zoom = zoom.finiteOr(defaults.zoom).coerceIn(0.25f, 8.0f),
        rotationDegrees = rotation
    )
}

private fun Float.finiteOr(defaultValue: Float): Float {
    return if (isFinite()) this else defaultValue
}

private fun Int.finiteRotationDegrees(): Int {
    val raw = this % 360
    return if (raw < 0) raw + 360 else raw
}
