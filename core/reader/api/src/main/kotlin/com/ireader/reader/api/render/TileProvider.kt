package com.ireader.reader.api.render

import android.graphics.Bitmap
import java.io.Closeable

data class TileRequest(
    // Region in rendered page-space pixels (already includes fit/rotation/config zoom).
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    // Additional sampling scale for current viewport gesture scale (for example 2x).
    val scale: Float = 1.0f,
    val quality: RenderPolicy.Quality = RenderPolicy.Quality.FINAL
)

interface TileProvider : Closeable {
    suspend fun renderTile(request: TileRequest): Bitmap
}

