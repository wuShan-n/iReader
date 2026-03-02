package com.ireader.engines.pdf.internal.render

import android.graphics.Bitmap
import android.graphics.Rect
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.engines.pdf.internal.backend.PdfRenderMode
import com.ireader.engines.pdf.internal.cache.TieredTileCache
import com.ireader.engines.pdf.internal.cache.TileInflight
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.TileProvider
import com.ireader.reader.api.render.TileRequest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class PdfTileProvider(
    private val backend: PdfBackend,
    private val pageIndex: Int,
    private val basePage: PdfPageSize,
    private val baseScale: Float,
    private val rotationDegrees: Int,
    private val allowCache: Boolean,
    private val cacheDraft: Boolean,
    private val cache: TieredTileCache,
    private val inflight: TileInflight,
    private val ioDispatcher: CoroutineDispatcher,
    private val isValid: () -> Boolean
) : TileProvider {

    private val closed = AtomicBoolean(false)

    override suspend fun renderTile(request: TileRequest): Bitmap = withContext(ioDispatcher) {
        coroutineContext.ensureActive()
        if (closed.get() || !isValid()) throw CancellationException("stale tile provider")

        val scale = max(0.05f, baseScale * request.scale)
        val canCache = allowCache && (request.quality == RenderPolicy.Quality.FINAL || cacheDraft)
        val key = if (canCache) buildKey(request, scale) else ""

        if (canCache) {
            cache.get(key, request.quality)?.let { return@withContext it }
            return@withContext inflight.getOrAwait(key) {
                cache.get(key, request.quality) ?: renderInternal(request, scale, key, canCache)
            }
        }

        renderInternal(request, scale, key = "", canCacheThis = false)
    }

    private suspend fun renderInternal(
        request: TileRequest,
        scale: Float,
        key: String,
        canCacheThis: Boolean
    ): Bitmap {
        coroutineContext.ensureActive()

        val bitmapConfig = when (request.quality) {
            RenderPolicy.Quality.DRAFT -> Bitmap.Config.RGB_565
            RenderPolicy.Quality.FINAL -> Bitmap.Config.ARGB_8888
        }

        val width = request.widthPx.coerceAtLeast(1)
        val height = request.heightPx.coerceAtLeast(1)
        val bytesPerPixel = if (bitmapConfig == Bitmap.Config.RGB_565) 2 else 4
        val estimatedBytes = width.toLong() * height.toLong() * bytesPerPixel

        val heapMax = Runtime.getRuntime().maxMemory().coerceAtLeast(128L * 1024 * 1024)
        if (estimatedBytes > heapMax / 10L) {
            if (allowCache) cache.evictAll()
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        try {
            val bitmap = Bitmap.createBitmap(width, height, bitmapConfig)
            val clip = Rect(0, 0, bitmap.width, bitmap.height)
            val matrix = buildPdfTileMatrix(
                basePage = basePage,
                scale = scale,
                rotationDegrees = rotationDegrees,
                leftPx = request.leftPx,
                topPx = request.topPx
            )
            val mode = when (request.quality) {
                RenderPolicy.Quality.DRAFT -> PdfRenderMode.DRAFT
                RenderPolicy.Quality.FINAL -> PdfRenderMode.FINAL
            }
            backend.render(pageIndex, bitmap, clip, matrix, mode)
            if (canCacheThis && key.isNotEmpty() && bitmap.width > 1 && bitmap.height > 1) {
                cache.put(key, bitmap, request.quality)
            }
            return bitmap
        } catch (ce: CancellationException) {
            throw ce
        } catch (oom: OutOfMemoryError) {
            if (allowCache) cache.evictAll()
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun buildKey(request: TileRequest, scale: Float): String {
        val quantizedScale = ((scale * 100f).toInt() / 5) * 5 // 0.05 step
        return "p=$pageIndex|l=${request.leftPx}|t=${request.topPx}|w=${request.widthPx}" +
            "|h=${request.heightPx}|s=$quantizedScale|r=$rotationDegrees|q=${request.quality}"
    }

    override fun close() {
        closed.set(true)
    }
}
