package com.ireader.engines.pdf.internal.render

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PageTransform(
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val scale: Float
)

internal fun computePageTransform(
    pageWidthPt: Int,
    pageHeightPt: Int,
    config: RenderConfig.FixedPage,
    constraints: LayoutConstraints
): PageTransform {
    val rotated = normalizeByRotation(
        pageWidth = pageWidthPt.coerceAtLeast(1),
        pageHeight = pageHeightPt.coerceAtLeast(1),
        rotation = config.rotationDegrees
    )

    val baseScale = when (config.fitMode) {
        RenderConfig.FitMode.FIT_WIDTH -> {
            constraints.viewportWidthPx.toFloat() / rotated.first.toFloat()
        }

        RenderConfig.FitMode.FIT_HEIGHT -> {
            constraints.viewportHeightPx.toFloat() / rotated.second.toFloat()
        }

        RenderConfig.FitMode.FIT_PAGE -> {
            min(
                constraints.viewportWidthPx.toFloat() / rotated.first.toFloat(),
                constraints.viewportHeightPx.toFloat() / rotated.second.toFloat()
            )
        }

        RenderConfig.FitMode.FREE -> 1f
    }.coerceAtLeast(0.01f)

    val finalScale = (baseScale * config.zoom.coerceAtLeast(0.1f)).coerceAtLeast(0.01f)
    return PageTransform(
        pageWidthPx = (rotated.first * finalScale).roundToInt().coerceAtLeast(1),
        pageHeightPx = (rotated.second * finalScale).roundToInt().coerceAtLeast(1),
        scale = finalScale
    )
}

internal fun zoomBucketMilli(zoom: Float): Int {
    val clamped = zoom.coerceIn(0.1f, 8f)
    return (clamped * 1000f).roundToInt()
}

private fun normalizeByRotation(pageWidth: Int, pageHeight: Int, rotation: Int): Pair<Int, Int> {
    val normalized = ((rotation % 360) + 360) % 360
    return if (normalized == 90 || normalized == 270) {
        pageHeight to pageWidth
    } else {
        pageWidth to pageHeight
    }
}

