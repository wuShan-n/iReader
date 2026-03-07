package com.ireader.engines.common.android.pagination

import com.ireader.core.common.android.typography.AndroidTextLayoutKind
import com.ireader.core.common.android.typography.resolveAndroidTextLayoutProfile
import com.ireader.core.common.android.typography.resolvePagePaddingDp
import com.ireader.engines.common.hash.Hashing
import com.ireader.engines.common.android.reflow.SOFT_BREAK_PROFILE_EXTRA_KEY
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import java.util.Locale
import kotlin.math.roundToInt

object ReflowPaginationProfile {

    private const val PROFILE_SCHEMA_VERSION = 12

    fun keyFor(
        documentKey: String,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        keyLength: Int = 16
    ): String {
        val pagePadding = config.resolvePagePaddingDp()
        val softBreakProfile = SoftBreakTuningProfile.fromStorageValue(
            config.extra[SOFT_BREAK_PROFILE_EXTRA_KEY]
        )
        val rulesVersion = SoftBreakRuleConfig.forProfile(softBreakProfile).rulesVersion
        val textLayoutProfile = resolveAndroidTextLayoutProfile(
            kind = AndroidTextLayoutKind.TXT,
            textAlign = TextAlignMode.JUSTIFY,
            breakStrategy = config.breakStrategy,
            hyphenationMode = config.hyphenationMode
        )
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
            append(constraints.density.normalizedKeyPart())
            append('|')
            append(constraints.fontScale.normalizedKeyPart())
            append('|')
            append(config.fontSizeSp.normalizedKeyPart())
            append('|')
            append(config.lineHeightMult.normalizedKeyPart())
            append('|')
            append(config.paragraphSpacingDp.normalizedKeyPart())
            append('|')
            append(pagePadding.horizontal.normalizedKeyPart())
            append('|')
            append(pagePadding.top.normalizedKeyPart())
            append('|')
            append(pagePadding.bottom.normalizedKeyPart())
            append('|')
            append(config.fontFamilyName.orEmpty())
            append('|')
            append(textLayoutProfile.breakStrategy)
            append('|')
            append(textLayoutProfile.hyphenationMode)
            append('|')
            append(textLayoutProfile.justificationMode)
            append('|')
            append(textLayoutProfile.lineBreakConfig.lineBreakStyle)
            append('|')
            append(textLayoutProfile.lineBreakConfig.lineBreakWordStyle)
            append('|')
            append(config.includeFontPadding)
            append('|')
            append(config.pageInsetMode)
            append('|')
            append(softBreakProfile.storageValue.lowercase(Locale.US))
            append('|')
            append(rulesVersion)
        }
        return Hashing.sha256Hex(raw).take(keyLength.coerceAtLeast(1))
    }

    private fun Float.normalizedKeyPart(scale: Int = 1_000): Int {
        return (this * scale).roundToInt()
    }
}
