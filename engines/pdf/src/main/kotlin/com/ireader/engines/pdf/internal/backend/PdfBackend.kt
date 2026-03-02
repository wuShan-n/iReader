package com.ireader.engines.pdf.internal.backend

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import java.io.Closeable

internal data class PdfPageSize(
    val width: Int,
    val height: Int
)

internal enum class PdfRenderMode {
    DRAFT,
    FINAL
}

internal interface PdfBackend : Closeable {
    val pageCount: Int

    suspend fun pageSize(pageIndex: Int): PdfPageSize

    suspend fun render(
        pageIndex: Int,
        bitmap: Bitmap,
        clip: Rect,
        matrix: Matrix,
        mode: PdfRenderMode
    )
}
