package com.ireader.reader.api.render

import com.ireader.reader.api.engine.DocumentCapabilities

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
    val sanitizedPagePaddingDp = pagePaddingDp
        .finiteOr(defaults.pagePaddingDp)
        .coerceIn(REFLOW_PAGE_PADDING_HORIZONTAL_MIN_DP, REFLOW_PAGE_PADDING_HORIZONTAL_MAX_DP)
    val sanitizedExtra = extra.toMutableMap()
    if (extra.containsKey(PAGE_PADDING_TOP_DP_EXTRA_KEY)) {
        sanitizedExtra[PAGE_PADDING_TOP_DP_EXTRA_KEY] = sanitizeVerticalPaddingDp(
            raw = extra[PAGE_PADDING_TOP_DP_EXTRA_KEY],
            fallback = sanitizedPagePaddingDp
        ).toString()
    }
    if (extra.containsKey(PAGE_PADDING_BOTTOM_DP_EXTRA_KEY)) {
        sanitizedExtra[PAGE_PADDING_BOTTOM_DP_EXTRA_KEY] = sanitizeVerticalPaddingDp(
            raw = extra[PAGE_PADDING_BOTTOM_DP_EXTRA_KEY],
            fallback = sanitizedPagePaddingDp
        ).toString()
    }
    return copy(
        fontSizeSp = fontSizeSp.finiteOr(defaults.fontSizeSp).coerceIn(8f, 72f),
        lineHeightMult = lineHeightMult
            .finiteOr(defaults.lineHeightMult)
            .coerceIn(REFLOW_LINE_HEIGHT_MIN, REFLOW_LINE_HEIGHT_MAX),
        paragraphSpacingDp = paragraphSpacingDp
            .finiteOr(defaults.paragraphSpacingDp)
            .coerceIn(REFLOW_PARAGRAPH_SPACING_MIN_DP, REFLOW_PARAGRAPH_SPACING_MAX_DP),
        pagePaddingDp = sanitizedPagePaddingDp,
        extra = sanitizedExtra
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

private fun sanitizeVerticalPaddingDp(raw: String?, fallback: Float): Float {
    val parsed = raw
        ?.toFloatOrNull()
        ?.takeIf(Float::isFinite)
    return (parsed ?: fallback).coerceIn(
        REFLOW_PAGE_PADDING_VERTICAL_MIN_DP,
        REFLOW_PAGE_PADDING_VERTICAL_MAX_DP
    )
}
