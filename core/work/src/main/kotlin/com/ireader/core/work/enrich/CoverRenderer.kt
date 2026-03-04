package com.ireader.core.work.enrich

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.TileRequest

object CoverRenderer {
    suspend fun renderCoverBitmap(
        page: RenderPage,
        desiredWidth: Int,
        desiredHeight: Int,
        titleFallback: String
    ): Bitmap {
        return when (val content = page.content) {
            is RenderContent.BitmapPage -> scaleTo(content.bitmap, desiredWidth, desiredHeight)
            is RenderContent.Tiles -> {
                val scale = minScale(
                    desiredWidth.toFloat() / content.pageWidthPx.toFloat(),
                    desiredHeight.toFloat() / content.pageHeightPx.toFloat()
                )
                val tile = runCatching {
                    content.tileProvider.renderTile(
                        TileRequest(
                            leftPx = 0,
                            topPx = 0,
                            widthPx = content.pageWidthPx,
                            heightPx = content.pageHeightPx,
                            scale = scale
                        )
                    )
                }.getOrNull()

                if (tile != null) {
                    scaleTo(tile, desiredWidth, desiredHeight)
                } else {
                    placeholder(desiredWidth, desiredHeight, titleFallback)
                }
            }

            is RenderContent.Text,
            RenderContent.Embedded -> placeholder(desiredWidth, desiredHeight, titleFallback)
        }
    }

    private fun scaleTo(src: Bitmap, width: Int, height: Int): Bitmap {
        if (src.width == width && src.height == height) {
            return src
        }
        return Bitmap.createScaledBitmap(src, width, height, true)
    }

    private fun minScale(a: Float, b: Float): Float = if (a < b) a else b

    fun placeholderBitmap(width: Int, height: Int, title: String): Bitmap {
        return placeholder(width, height, title)
    }

    private fun placeholder(width: Int, height: Int, title: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (width * 0.06f).coerceAtLeast(24f)
        }

        val lines = breakLines(
            text = title.ifBlank { "Untitled" },
            maxCharsPerLine = 18,
            maxLines = 6
        )
        val padding = width * 0.08f
        var y = height * 0.25f
        for (line in lines) {
            canvas.drawText(line, padding, y, paint)
            y += paint.textSize * 1.25f
        }

        return bitmap
    }

    private fun breakLines(text: String, maxCharsPerLine: Int, maxLines: Int): List<String> {
        val result = ArrayList<String>(maxLines)
        var index = 0
        while (index < text.length && result.size < maxLines) {
            val end = (index + maxCharsPerLine).coerceAtMost(text.length)
            result += text.substring(index, end)
            index = end
        }
        return result
    }
}
