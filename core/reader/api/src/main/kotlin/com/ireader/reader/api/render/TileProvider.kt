package com.ireader.reader.api.render

import android.graphics.Bitmap
import java.io.Closeable

data class TileRequest(
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val scale: Float = 1.0f,          // 缩放倍率（例如 2x）
    val quality: RenderPolicy.Quality = RenderPolicy.Quality.FINAL
)

interface TileProvider : Closeable {
    suspend fun renderTile(request: TileRequest): Bitmap
}

