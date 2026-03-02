package com.ireader.engines.common.android.pagination

import com.ireader.engines.common.hash.Hashing
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

object ReflowPaginationProfile {

    fun keyFor(
        documentKey: String,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        keyLength: Int = 16
    ): String {
        val raw = buildString {
            append(documentKey)
            append('|')
            append(constraints.viewportWidthPx)
            append('x')
            append(constraints.viewportHeightPx)
            append('|')
            append(constraints.density)
            append('|')
            append(constraints.fontScale)
            append('|')
            append(config.fontSizeSp)
            append('|')
            append(config.lineHeightMult)
            append('|')
            append(config.paragraphSpacingDp)
            append('|')
            append(config.pagePaddingDp)
            append('|')
            append(config.fontFamilyName.orEmpty())
            append('|')
            append(config.hyphenation)
        }
        return Hashing.sha256Hex(raw).take(keyLength.coerceAtLeast(1))
    }
}
