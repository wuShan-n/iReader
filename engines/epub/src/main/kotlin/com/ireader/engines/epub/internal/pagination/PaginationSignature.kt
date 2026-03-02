package com.ireader.engines.epub.internal.pagination

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import kotlin.math.roundToInt

internal object PaginationSignature {

    /**
     * Only include fields that materially affect reflow pagination.
     * config.extra is intentionally excluded to avoid feedback loops.
     */
    fun of(config: RenderConfig.ReflowText, constraints: LayoutConstraints?): Int {
        val viewportWidth = constraints?.viewportWidthPx ?: 0
        val viewportHeight = constraints?.viewportHeightPx ?: 0
        val density = ((constraints?.density ?: 1f) * 100f).roundToInt()
        val fontScale = ((constraints?.fontScale ?: 1f) * 100f).roundToInt()

        fun scaled(value: Float): Int = (value * 100f).roundToInt()

        var hash = 17
        hash = 31 * hash + viewportWidth
        hash = 31 * hash + viewportHeight
        hash = 31 * hash + density
        hash = 31 * hash + fontScale
        hash = 31 * hash + scaled(config.fontSizeSp)
        hash = 31 * hash + scaled(config.lineHeightMult)
        hash = 31 * hash + scaled(config.paragraphSpacingDp)
        hash = 31 * hash + scaled(config.pagePaddingDp)
        hash = 31 * hash + (config.fontFamilyName?.hashCode() ?: 0)
        hash = 31 * hash + if (config.hyphenation) 1 else 0
        return hash
    }
}
