package com.ireader.engines.pdf.internal.render

import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import kotlin.math.max
import kotlin.math.min

internal data class PageTransform(
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val scale: Float
)

internal fun rotatedSize(base: PdfPageSize, rotationDegrees: Int): PdfPageSize {
    val normalized = ((rotationDegrees % 360) + 360) % 360
    return if (normalized == 90 || normalized == 270) {
        PdfPageSize(width = base.height, height = base.width)
    } else {
        base
    }
}

internal fun computeTransform(
    basePage: PdfPageSize,
    config: RenderConfig.FixedPage,
    constraints: LayoutConstraints
): PageTransform {
    val rotatedPage = rotatedSize(basePage, config.rotationDegrees)
    val viewportWidth = constraints.viewportWidthPx.toFloat().coerceAtLeast(1f)
    val viewportHeight = constraints.viewportHeightPx.toFloat().coerceAtLeast(1f)
    val pageWidth = rotatedPage.width.toFloat().coerceAtLeast(1f)
    val pageHeight = rotatedPage.height.toFloat().coerceAtLeast(1f)

    val fitScale = when (config.fitMode) {
        RenderConfig.FitMode.FIT_WIDTH -> viewportWidth / pageWidth
        RenderConfig.FitMode.FIT_HEIGHT -> viewportHeight / pageHeight
        RenderConfig.FitMode.FIT_PAGE -> min(viewportWidth / pageWidth, viewportHeight / pageHeight)
        RenderConfig.FitMode.FREE -> 1f
    }
    val scale = max(0.05f, fitScale * config.zoom)

    return PageTransform(
        pageWidthPx = (pageWidth * scale).toInt().coerceAtLeast(1),
        pageHeightPx = (pageHeight * scale).toInt().coerceAtLeast(1),
        scale = scale
    )
}
