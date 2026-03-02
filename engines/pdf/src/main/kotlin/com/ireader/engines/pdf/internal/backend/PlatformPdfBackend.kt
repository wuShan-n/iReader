package com.ireader.engines.pdf.internal.backend

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.ireader.engines.pdf.internal.cache.PageSizeCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PlatformPdfBackend(
    pfd: ParcelFileDescriptor
) : PdfBackend {
    private val renderer = PdfRenderer(pfd)
    private val pageMutex = Mutex()
    private val sizeCache = PageSizeCache(maxEntries = 64)

    override val pageCount: Int
        get() = renderer.pageCount

    override suspend fun pageSize(pageIndex: Int): PdfPageSize {
        sizeCache.get(pageIndex)?.let { return it }
        return pageMutex.withLock {
            sizeCache.get(pageIndex)?.let { return@withLock it }
            renderer.openPage(pageIndex).use { page ->
                PdfPageSize(page.width, page.height).also { sizeCache.put(pageIndex, it) }
            }
        }
    }

    override suspend fun render(
        pageIndex: Int,
        bitmap: Bitmap,
        clip: Rect,
        matrix: Matrix,
        mode: PdfRenderMode
    ) {
        pageMutex.withLock {
            renderer.openPage(pageIndex).use { page ->
                val rendererMode = when (mode) {
                    PdfRenderMode.DRAFT -> PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    PdfRenderMode.FINAL -> PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                }
                page.render(bitmap, clip, matrix, rendererMode)
            }
        }
    }

    override fun close() {
        renderer.close()
    }
}
