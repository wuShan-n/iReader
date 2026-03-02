package com.ireader.engines.pdf.internal.backend.platform

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.backend.PdfBackendCapabilities
import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PlatformPdfBackend(
    private val descriptor: ParcelFileDescriptor,
    private val ioDispatcher: CoroutineDispatcher
) : PdfBackend {

    override val capabilities: PdfBackendCapabilities = PdfBackendCapabilities(
        outline = false,
        links = false,
        textExtraction = false,
        search = false
    )

    private val renderer = PdfRenderer(descriptor)

    override suspend fun pageCount(): Int = withContext(ioDispatcher) {
        renderer.pageCount
    }

    override suspend fun metadata(): DocumentMetadata = withContext(ioDispatcher) {
        // PdfRenderer does not expose rich metadata.
        DocumentMetadata(
            title = null,
            author = null,
            language = null,
            identifier = null,
            extra = mapOf("backend" to "platform")
        )
    }

    override suspend fun pageSize(pageIndex: Int): PdfPageSize = withContext(ioDispatcher) {
        renderer.openPage(pageIndex).use { page ->
            PdfPageSize(widthPt = page.width, heightPt = page.height)
        }
    }

    override suspend fun renderRegion(
        pageIndex: Int,
        bitmap: Bitmap,
        regionLeftPx: Int,
        regionTopPx: Int,
        regionWidthPx: Int,
        regionHeightPx: Int,
        quality: RenderPolicy.Quality
    ) = withContext(ioDispatcher) {
        renderer.openPage(pageIndex).use { page ->
            // Platform backend is a compatibility fallback; region rendering is approximated.
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    override suspend fun pageLinks(pageIndex: Int): List<DocumentLink> = emptyList()

    override suspend fun outline(): List<OutlineNode> = emptyList()

    override suspend fun pageText(pageIndex: Int): String? = null

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

