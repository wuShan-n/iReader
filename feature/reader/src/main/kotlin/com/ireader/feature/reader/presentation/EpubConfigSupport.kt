package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_TURN_STYLE_EXTRA_KEY
import com.ireader.reader.api.render.RenderConfig

internal fun normalizeEpubEffectiveReflowConfig(
    requested: RenderConfig.ReflowText,
    current: RenderConfig.ReflowText
): RenderConfig.ReflowText {
    val normalizedExtra = requested.extra.toMutableMap()
    current.extra[PAGE_TURN_EXTRA_KEY]?.let { normalizedExtra[PAGE_TURN_EXTRA_KEY] = it }
    current.extra[PAGE_TURN_STYLE_EXTRA_KEY]?.let { normalizedExtra[PAGE_TURN_STYLE_EXTRA_KEY] = it }
    return requested.copy(
        breakStrategy = current.breakStrategy,
        includeFontPadding = current.includeFontPadding,
        extra = normalizedExtra
    )
}
