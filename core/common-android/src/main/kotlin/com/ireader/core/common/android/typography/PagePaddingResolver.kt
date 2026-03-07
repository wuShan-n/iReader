package com.ireader.core.common.android.typography

import com.ireader.reader.api.render.PAGE_PADDING_BOTTOM_DP_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_PADDING_TOP_DP_EXTRA_KEY
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MAX_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MIN_DP
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.effectivePagePaddingDp

data class ResolvedPagePaddingDp(
    val horizontal: Float,
    val top: Float,
    val bottom: Float
)

fun RenderConfig.ReflowText.resolvePagePaddingDp(): ResolvedPagePaddingDp {
    val horizontal = effectivePagePaddingDp()
    return ResolvedPagePaddingDp(
        horizontal = horizontal,
        top = resolveVerticalPaddingDp(extra[PAGE_PADDING_TOP_DP_EXTRA_KEY], horizontal),
        bottom = resolveVerticalPaddingDp(extra[PAGE_PADDING_BOTTOM_DP_EXTRA_KEY], horizontal)
    )
}

private fun resolveVerticalPaddingDp(raw: String?, fallback: Float): Float {
    return (raw?.toFloatOrNull() ?: fallback)
        .takeIf(Float::isFinite)
        ?.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
        ?: fallback.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
}
