@file:Suppress("TooManyFunctions")

package com.ireader.engines.pdf.internal.backend.pdfium

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.backend.PdfBackendCapabilities
import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.OutlineNode
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.api.Config
import io.legere.pdfiumandroid.api.Link
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfPageKt
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

internal class PdfiumBackend private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val document: PdfDocumentKt
) : PdfBackend {

    override val capabilities: PdfBackendCapabilities = PdfBackendCapabilities(
        outline = true,
        links = true,
        textExtraction = true,
        search = true
    )

    private val pageSizeCache = mutableMapOf<Int, PdfPageSize>()
    private val pageSizeMutex = Mutex()

    private val textCacheMutex = Mutex()
    private val textCache = object : LinkedHashMap<Int, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>): Boolean {
            return size > 16
        }
    }

    override suspend fun pageCount(): Int = document.getPageCount()

    override suspend fun metadata(): DocumentMetadata {
        val meta = document.getDocumentMeta()
        val extra = buildMap {
            meta.subject?.let { put("subject", it) }
            meta.keywords?.let { put("keywords", it) }
            meta.creator?.let { put("creator", it) }
            meta.producer?.let { put("producer", it) }
            meta.creationDate?.let { put("creationDate", it) }
            meta.modDate?.let { put("modDate", it) }
            put("backend", "pdfium")
        }
        return DocumentMetadata(
            title = meta.title,
            author = meta.author,
            language = null,
            identifier = null,
            extra = extra
        )
    }

    override suspend fun pageSize(pageIndex: Int): PdfPageSize {
        val cached = pageSizeMutex.withLock { pageSizeCache[pageIndex] }
        if (cached != null) return cached

        val measured = withPage(pageIndex) { page ->
            PdfPageSize(
                widthPt = page.getPageWidthPoint().coerceAtLeast(1),
                heightPt = page.getPageHeightPoint().coerceAtLeast(1)
            )
        }

        pageSizeMutex.withLock {
            pageSizeCache[pageIndex] = measured
        }
        return measured
    }

    override suspend fun renderRegion(
        pageIndex: Int,
        bitmap: Bitmap,
        regionLeftPx: Int,
        regionTopPx: Int,
        regionWidthPx: Int,
        regionHeightPx: Int,
        quality: RenderPolicy.Quality
    ) {
        withPage(pageIndex) { page ->
            page.renderPageBitmap(
                bitmap = bitmap,
                startX = regionLeftPx,
                startY = regionTopPx,
                drawSizeX = max(1, regionWidthPx),
                drawSizeY = max(1, regionHeightPx),
                renderAnnot = true,
                textMask = false
            )
        }
    }

    override suspend fun pageLinks(pageIndex: Int): List<DocumentLink> {
        val pageSize = pageSize(pageIndex)
        return withPage(pageIndex) { page ->
            page.getPageLinks().mapNotNull { link ->
                link.toDocumentLink(pageSize)
            }
        }
    }

    override suspend fun outline(): List<OutlineNode> {
        return document.getTableOfContents().mapNotNull(::mapBookmark)
    }

    override suspend fun pageText(pageIndex: Int): String? {
        val cached = textCacheMutex.withLock { textCache[pageIndex] }
        if (cached != null) return cached

        val extracted = withPage(pageIndex) { page ->
            withTextPage(page) { textPage ->
                val charCount = textPage.textPageCountChars()
                if (charCount <= 0) {
                    ""
                } else {
                    val raw = textPage.textPageGetText(0, charCount).orEmpty()
                    if (raw.indexOf('\u0000') >= 0) raw.replace("\u0000", "") else raw
                }
            }
        }

        textCacheMutex.withLock {
            textCache[pageIndex] = extracted
        }
        return extracted
    }

    override fun close() {
        runCatching { document.close() }
        runCatching { descriptor.close() }
    }

    private suspend fun <T> withPage(pageIndex: Int, block: suspend (PdfPageKt) -> T): T {
        val page = document.openPage(pageIndex)
            ?: error("Cannot open PDF page: $pageIndex")
        return try {
            block(page)
        } finally {
            runCatching { page.close() }
        }
    }

    private suspend fun <T> withTextPage(page: PdfPageKt, block: suspend (PdfTextPageKt) -> T): T {
        val textPage = page.openTextPage()
        return try {
            block(textPage)
        } finally {
            runCatching { textPage.close() }
        }
    }

    private fun Link.toDocumentLink(size: PdfPageSize): DocumentLink? {
        val uriValue = uri
        val destinationPage = destPageIdx
        val target = when {
            !uriValue.isNullOrBlank() -> LinkTarget.External(uriValue)
            destinationPage != null && destinationPage >= 0 -> LinkTarget.Internal(
                locator = Locator(
                    scheme = LocatorSchemes.PDF_PAGE,
                    value = destinationPage.toString()
                )
            )
            else -> null
        } ?: return null

        val rect = bounds.toNormalized(size.widthPt, size.heightPt)
        return DocumentLink(
            target = target,
            title = null,
            bounds = listOf(rect)
        )
    }

    private fun RectF.toNormalized(pageWidth: Int, pageHeight: Int): NormalizedRect {
        val width = pageWidth.coerceAtLeast(1).toFloat()
        val height = pageHeight.coerceAtLeast(1).toFloat()

        val x0 = (left / width).coerceIn(0f, 1f)
        val x1 = (right / width).coerceIn(0f, 1f)

        val lowY = min(top, bottom)
        val highY = max(top, bottom)
        val yTop = (1f - (highY / height)).coerceIn(0f, 1f)
        val yBottom = (1f - (lowY / height)).coerceIn(0f, 1f)

        return NormalizedRect(
            left = min(x0, x1),
            top = min(yTop, yBottom),
            right = max(x0, x1),
            bottom = max(yTop, yBottom)
        )
    }

    private fun mapBookmark(bookmark: Bookmark): OutlineNode? {
        val title = bookmark.title?.takeIf { it.isNotBlank() } ?: return null
        val page = bookmark.pageIdx.toInt().coerceAtLeast(0)
        return OutlineNode(
            title = title,
            locator = Locator(
                scheme = LocatorSchemes.PDF_PAGE,
                value = page.toString()
            ),
            children = bookmark.children.mapNotNull(::mapBookmark)
        )
    }

    companion object {
        suspend fun open(
            descriptor: ParcelFileDescriptor,
            password: String?,
            ioDispatcher: CoroutineDispatcher
        ): PdfiumBackend {
            val core = PdfiumCoreKt(
                dispatcher = ioDispatcher,
                config = Config()
            )
            val document = core.newDocument(descriptor, password)
            return PdfiumBackend(
                descriptor = descriptor,
                document = document
            )
        }
    }
}
