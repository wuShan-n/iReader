package com.ireader.engines.epub.internal.pagination

import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

internal class GlobalPageMapper(
    private val container: EpubContainer,
    private val paginator: ReflowPaginator
) {
    fun totalPages(
        constraints: LayoutConstraints?,
        config: RenderConfig.ReflowText
    ): Int {
        var total = 0
        for (index in 0 until container.spineCount) {
            total += paginator.pageCount(index, constraints, config)
        }
        return total.coerceAtLeast(1)
    }

    fun locateByPercent(
        percent: Double,
        constraints: LayoutConstraints?,
        config: RenderConfig.ReflowText
    ): Pair<Int, Int> {
        val safe = percent.coerceIn(0.0, 1.0)
        val total = totalPages(constraints, config)
        val targetGlobalPage = (safe * (total - 1)).toInt().coerceIn(0, total - 1)

        var acc = 0
        for (spine in 0 until container.spineCount) {
            val pages = paginator.pageCount(spine, constraints, config)
            if (targetGlobalPage < acc + pages) {
                return spine to (targetGlobalPage - acc)
            }
            acc += pages
        }

        val lastSpine = (container.spineCount - 1).coerceAtLeast(0)
        return lastSpine to 0
    }
}
