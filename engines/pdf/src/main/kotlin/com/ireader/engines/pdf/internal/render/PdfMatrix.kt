package com.ireader.engines.pdf.internal.render

import android.graphics.Matrix
import com.ireader.engines.pdf.internal.backend.PdfPageSize

internal fun buildPdfTileMatrix(
    basePage: PdfPageSize,
    scale: Float,
    rotationDegrees: Int,
    leftPx: Int,
    topPx: Int
): Matrix {
    val normalized = ((rotationDegrees % 360) + 360) % 360
    val scaledWidth = basePage.width.toFloat() * scale
    val scaledHeight = basePage.height.toFloat() * scale

    return Matrix().apply {
        setScale(scale, scale)
        when (normalized) {
            0 -> Unit
            90 -> {
                postRotate(90f)
                postTranslate(scaledHeight, 0f)
            }

            180 -> {
                postRotate(180f)
                postTranslate(scaledWidth, scaledHeight)
            }

            270 -> {
                postRotate(270f)
                postTranslate(0f, scaledWidth)
            }

            else -> {
                postRotate(normalized.toFloat())
            }
        }
        postTranslate(-leftPx.toFloat(), -topPx.toFloat())
    }
}
