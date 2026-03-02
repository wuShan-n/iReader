package com.ireader.engines.pdf.internal.pdfium

import android.graphics.Rect
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import com.ireader.engines.pdf.internal.cache.IntLruCache
import com.ireader.engines.pdf.internal.render.PdfLinkRaw
import com.ireader.engines.pdf.internal.util.useSafely
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.OutlineNode
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.api.Link
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfPageKt
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal class PdfiumDocument private constructor(
    @Suppress("unused")
    private val core: PdfiumCoreKt,
    private val doc: PdfDocumentKt,
    val pageCount: Int
) : Closeable {

    private var outlineCache: List<OutlineNode>? = null
    private var outlineIndexCache: PdfOutlineIndex? = null
    private val linkNormCache = IntLruCache<List<PdfLinkRaw>>(maxEntries = 128)
    private val textCache = IntLruCache<String>(maxEntries = 16)

    val supportsText: Boolean = true

    companion object {
        suspend fun open(
            pfd: ParcelFileDescriptor,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            password: String?
        ): PdfiumDocument {
            val core = PdfiumCoreKt(dispatcher = dispatcher)
            val doc = if (password.isNullOrEmpty()) {
                core.newDocument(pfd)
            } else {
                core.newDocument(pfd, password)
            }
            val pageCount = doc.getPageCount()
            return PdfiumDocument(core = core, doc = doc, pageCount = pageCount)
        }
    }

    suspend fun outline(): List<OutlineNode> {
        outlineCache?.let { return it }
        val toc = doc.getTableOfContents()
        val mapped = toc.map { it.toOutlineNode() }
        outlineCache = mapped
        return mapped
    }

    suspend fun sectionTitleForPage(pageIndex: Int): String? {
        val index = outlineIndexCache ?: PdfOutlineIndex.build(outline()).also { outlineIndexCache = it }
        return index.titleForPage(pageIndex)
    }

    suspend fun pageLinksNormalized(pageIndex: Int, rotationDegrees: Int): List<PdfLinkRaw> {
        val rotate = pdfiumRotateIndex(rotationDegrees)
        val key = (pageIndex shl 2) + rotate
        linkNormCache.get(key)?.let { return it }

        val base = 10_000
        val links = mutableListOf<PdfLinkRaw>()
        withPage(pageIndex) { page ->
            val pageLinks = page.getPageLinks()
            for (link in pageLinks) {
                val target = link.toLinkTarget() ?: continue
                val mapped = page.mapRectToDevice(
                    startX = 0,
                    startY = 0,
                    sizeX = base,
                    sizeY = base,
                    rotate = rotate,
                    coords = link.bounds
                )
                val normalized = rectToNormalized(mapped, base)
                links += PdfLinkRaw(
                    target = target,
                    bounds = RectF(
                        normalized.left,
                        normalized.top,
                        normalized.right,
                        normalized.bottom
                    )
                )
            }
        }

        linkNormCache.put(key, links)
        return links
    }

    suspend fun pageText(pageIndex: Int): String {
        textCache.get(pageIndex)?.let { return it }
        val text = withTextPage(pageIndex) { _, textPage ->
            val count = textPage.textPageCountChars().coerceAtLeast(0)
            if (count == 0) return@withTextPage ""
            textPage.textPageGetText(0, count).orEmpty()
        }
        textCache.put(pageIndex, text)
        return text
    }

    suspend fun <T> withPage(pageIndex: Int, block: suspend (PdfPageKt) -> T): T {
        val page = requireNotNull(doc.openPage(pageIndex)) { "openPage($pageIndex) failed" }
        return page.useSafely { p -> block(p) }
    }

    suspend fun <T> withTextPage(
        pageIndex: Int,
        block: suspend (PdfPageKt, PdfTextPageKt) -> T
    ): T {
        val page = requireNotNull(doc.openPage(pageIndex)) { "openPage($pageIndex) failed" }
        return page.useSafely { p ->
            val textPage = requireNotNull(p.openTextPage()) { "openTextPage($pageIndex) failed" }
            textPage.useSafely { tp -> block(p, tp) }
        }
    }

    fun evictAllCaches() {
        outlineCache = null
        outlineIndexCache = null
        linkNormCache.evictAll()
        textCache.evictAll()
    }

    override fun close() {
        runCatching { doc.close() }
    }

    private fun Bookmark.toOutlineNode(): OutlineNode {
        val page = pageIdx.toInt().coerceAtLeast(0)
        return OutlineNode(
            title = title.orEmpty(),
            locator = Locator(LocatorSchemes.PDF_PAGE, page.toString()),
            children = children.orEmpty().map { child -> child.toOutlineNode() }
        )
    }

    private fun Link.toLinkTarget(): LinkTarget? {
        val uriValue = uri?.takeIf { it.isNotBlank() }
        if (uriValue != null) return LinkTarget.External(uriValue)

        val targetPage = destPageIdx?.takeIf { it >= 0 } ?: return null
        return LinkTarget.Internal(Locator(LocatorSchemes.PDF_PAGE, targetPage.toString()))
    }
}

internal fun pdfiumRotateIndex(rotationDegrees: Int): Int {
    val normalized = ((rotationDegrees % 360) + 360) % 360
    return when (normalized) {
        90 -> 1
        180 -> 2
        270 -> 3
        else -> 0
    }
}

internal fun rectToNormalized(device: Rect, base: Int): NormalizedRect {
    val l = (device.left.toFloat() / base).coerceIn(0f, 1f)
    val t = (device.top.toFloat() / base).coerceIn(0f, 1f)
    val r = (device.right.toFloat() / base).coerceIn(0f, 1f)
    val b = (device.bottom.toFloat() / base).coerceIn(0f, 1f)
    return NormalizedRect(
        left = minOf(l, r),
        top = minOf(t, b),
        right = maxOf(l, r),
        bottom = maxOf(t, b)
    )
}
