package com.ireader.engines.epub.internal.pagination

import com.ireader.engines.epub.internal.cache.SimpleLruCache
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.engines.epub.internal.text.XhtmlTextExtractor
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

internal class ReflowPaginator(
    private val container: EpubContainer
) {
    private val lengthCache = SimpleLruCache<Int, Int>(maxSize = 64)

    fun pageCount(
        spineIndex: Int,
        constraints: LayoutConstraints?,
        config: RenderConfig.ReflowText
    ): Int {
        if (constraints == null) return 1

        val textLength = lengthCache.getOrPut(spineIndex) {
            val file = File(container.rootDir, container.spinePath(spineIndex))
            runCatching { XhtmlTextExtractor.extract(file) }
                .getOrElse { runCatching { file.readText() }.getOrDefault("") }
                .length
        }

        val charsPerPage = charsPerPage(constraints, config, isCjk = textLength in 1..20_000)
            .coerceAtLeast(300)
        return ceil(textLength.toDouble() / charsPerPage.toDouble())
            .toInt()
            .coerceAtLeast(1)
            .coerceAtMost(5000)
    }

    fun charsPerPage(
        constraints: LayoutConstraints?,
        config: RenderConfig.ReflowText,
        isCjk: Boolean
    ): Int {
        if (constraints == null) return Int.MAX_VALUE

        val fontPx = config.fontSizeSp * constraints.density * constraints.fontScale
        val linePx = max(1f, fontPx * config.lineHeightMult)
        val paddingPx = config.pagePaddingDp * constraints.density

        val usableW = (constraints.viewportWidthPx - (paddingPx * 2f)).coerceAtLeast(200f)
        val usableH = (constraints.viewportHeightPx - (paddingPx * 2f)).coerceAtLeast(200f)

        val linesPerPage = (usableH / linePx).toInt().coerceAtLeast(8)
        val charWidth = if (isCjk) max(6f, fontPx * 0.95f) else max(6f, fontPx * 0.55f)
        val charsPerLine = (usableW / charWidth).toInt().coerceAtLeast(12)

        return (charsPerLine * linesPerPage * 0.9f).toInt().coerceAtLeast(400)
    }
}
