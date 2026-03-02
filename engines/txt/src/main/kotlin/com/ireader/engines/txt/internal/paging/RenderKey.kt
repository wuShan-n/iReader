package com.ireader.engines.txt.internal.paging

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

internal data class RenderKey(
    val docId: String,
    val charset: String,
    val viewportW: Int,
    val viewportH: Int,
    val densityBits: Int,
    val fontScaleBits: Int,
    val fontSizeBits: Int,
    val lineHeightBits: Int,
    val paragraphBits: Int,
    val paddingBits: Int,
    val hyphenation: Boolean,
    val fontFamily: String?
) {
    companion object {
        fun of(
            docId: String,
            charset: String,
            constraints: LayoutConstraints,
            config: RenderConfig.ReflowText
        ): RenderKey {
            return RenderKey(
                docId = docId,
                charset = charset,
                viewportW = constraints.viewportWidthPx,
                viewportH = constraints.viewportHeightPx,
                densityBits = constraints.density.toBits(),
                fontScaleBits = constraints.fontScale.toBits(),
                fontSizeBits = config.fontSizeSp.toBits(),
                lineHeightBits = config.lineHeightMult.toBits(),
                paragraphBits = config.paragraphSpacingDp.toBits(),
                paddingBits = config.pagePaddingDp.toBits(),
                hyphenation = config.hyphenation,
                fontFamily = config.fontFamilyName
            )
        }
    }
}
