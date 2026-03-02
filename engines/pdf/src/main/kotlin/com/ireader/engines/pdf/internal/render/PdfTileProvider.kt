package com.ireader.engines.pdf.internal.render

import android.graphics.Bitmap
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.cache.TileCache
import com.ireader.engines.pdf.internal.cache.TileCacheKey
import com.ireader.engines.pdf.internal.cache.TileInflight
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TileProvider
import com.ireader.reader.api.render.TileRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.math.roundToInt

internal class PdfTileProvider(
    private val pageIndex: Int,
    private val renderConfig: RenderConfig.FixedPage,
    private val backend: PdfBackend,
    private val cache: TileCache,
    private val inflight: TileInflight,
    private val config: PdfEngineConfig,
    private val scope: CoroutineScope
) : TileProvider {

    override suspend fun renderTile(request: TileRequest): Bitmap {
        val scale = request.scale.coerceAtLeast(0.5f)
        val scaledWidth = (request.widthPx * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (request.heightPx * scale).roundToInt().coerceAtLeast(1)
        val key = TileCacheKey(
            pageIndex = pageIndex,
            leftPx = request.leftPx,
            topPx = request.topPx,
            widthPx = request.widthPx,
            heightPx = request.heightPx,
            scaleMilli = (scale * 1000f).roundToInt(),
            quality = request.quality,
            rotationDegrees = renderConfig.rotationDegrees,
            zoomBucketMilli = zoomBucketMilli(renderConfig.zoom)
        )

        val cached = cache.get(key)
        if (cached != null && !cached.isRecycled) return cached

        val rendered = inflight.getOrAwait(key) {
            val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            val regionLeft = (request.leftPx * scale).roundToInt().coerceAtLeast(0)
            val regionTop = (request.topPx * scale).roundToInt().coerceAtLeast(0)
            backend.renderRegion(
                pageIndex = pageIndex,
                bitmap = bitmap,
                regionLeftPx = regionLeft,
                regionTopPx = regionTop,
                regionWidthPx = scaledWidth,
                regionHeightPx = scaledHeight,
                quality = request.quality
            )
            bitmap
        }

        cache.put(key, rendered)
        return rendered
    }

    override fun close() {
        scope.cancel()
    }
}

