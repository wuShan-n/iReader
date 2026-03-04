package com.ireader.engines.common.android.pagination

import com.ireader.engines.common.hash.Hashing
import com.ireader.engines.common.android.reflow.SOFT_BREAK_PROFILE_EXTRA_KEY
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

object ReflowPaginationProfile {

    private const val PROFILE_SCHEMA_VERSION = 6

    fun keyFor(
        documentKey: String,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        keyLength: Int = 16
    ): String {
        val raw = buildString {
            append("v")
            append(PROFILE_SCHEMA_VERSION)
            append('|')
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
            append(config.paragraphIndentEm)
            append('|')
            append(config.pagePaddingDp)
            append('|')
            append(config.fontFamilyName.orEmpty())
            append('|')
            append(config.textAlign)
            append('|')
            append(config.breakStrategy)
            append('|')
            append(config.hyphenationMode)
            append('|')
            append(config.includeFontPadding)
            append('|')
            append(config.pageInsetMode)
            append('|')
            append(config.extra[SOFT_BREAK_PROFILE_EXTRA_KEY].orEmpty().trim().lowercase())
        }
        return Hashing.sha256Hex(raw).take(keyLength.coerceAtLeast(1))
    }
}
