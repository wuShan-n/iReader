package com.ireader.engines.pdf.internal.pdfium

import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import kotlin.math.max

internal class PdfOutlineIndex private constructor(
    private val starts: List<Pair<Int, String>>
) {
    fun titleForPage(pageIndex: Int): String? {
        if (starts.isEmpty()) return null
        var lo = 0
        var hi = starts.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val page = starts[mid].first
            if (page <= pageIndex) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return if (ans >= 0) starts[ans].second else null
    }

    companion object {
        fun build(outline: List<OutlineNode>): PdfOutlineIndex {
            val pairs = mutableListOf<Pair<Int, String>>()
            fun walk(node: OutlineNode) {
                val page = node.locator
                    .takeIf { it.scheme == LocatorSchemes.PDF_PAGE }
                    ?.value
                    ?.toIntOrNull()
                val title = node.title.trim()
                if (page != null && title.isNotEmpty()) {
                    pairs += max(0, page) to title
                }
                node.children.forEach(::walk)
            }
            outline.forEach(::walk)

            val sorted = pairs.sortedBy { it.first }
            val compact = mutableListOf<Pair<Int, String>>()
            for ((page, title) in sorted) {
                if (compact.isNotEmpty() && compact.last().first == page) {
                    compact[compact.lastIndex] = page to title
                } else {
                    compact += page to title
                }
            }
            return PdfOutlineIndex(compact)
        }
    }
}
