package com.ireader.engines.pdf.internal.backend

import android.graphics.Bitmap
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.OutlineNode
import java.io.Closeable

internal data class PdfPageSize(
    val widthPt: Int,
    val heightPt: Int
)

internal data class PdfBackendCapabilities(
    val outline: Boolean,
    val links: Boolean,
    val textExtraction: Boolean,
    val search: Boolean
) {
    fun supportsFullReaderFeatures(): Boolean =
        outline && links && textExtraction && search
}

internal interface PdfBackend : Closeable {
    val capabilities: PdfBackendCapabilities

    suspend fun pageCount(): Int

    suspend fun metadata(): DocumentMetadata

    suspend fun pageSize(pageIndex: Int): PdfPageSize

    suspend fun renderRegion(
        pageIndex: Int,
        bitmap: Bitmap,
        regionLeftPx: Int,
        regionTopPx: Int,
        regionWidthPx: Int,
        regionHeightPx: Int,
        quality: RenderPolicy.Quality
    )

    suspend fun pageLinks(pageIndex: Int): List<DocumentLink>

    suspend fun outline(): List<OutlineNode>

    suspend fun pageText(pageIndex: Int): String?
}

