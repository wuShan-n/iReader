package com.ireader.reader.api.render

import android.graphics.Bitmap
import android.net.Uri
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator

@JvmInline
value class PageId(val value: String)

/**
 * 渲染产物统一封装：内容 + 可选链接 + 可选装饰(标注高亮等) + 性能指标
 */
data class RenderPage(
    val id: PageId,
    val locator: Locator,
    val content: RenderContent,
    val links: List<DocumentLink> = emptyList(),
    val decorations: List<Decoration> = emptyList(),
    val metrics: RenderMetrics? = null
)

sealed interface RenderContent {
    data class Text(
        val text: CharSequence,
        val mapping: TextMapping? = null
    ) : RenderContent

    /**
     * EPUB 常用：inline html 或者直接给一个可加载的 Uri
     * - Inline：适合引擎自己拼装 html
     * - Uri：适合引擎把章节写入/映射到本地资源系统
     */
    data class Html(
        val inlineHtml: String? = null,
        val contentUri: Uri? = null,
        val baseUri: Uri? = null
    ) : RenderContent

    data class BitmapPage(
        val bitmap: Bitmap
    ) : RenderContent

    /**
     * PDF 大图/高倍率缩放建议用 Tiles：避免一次性巨大 bitmap。
     */
    data class Tiles(
        val pageWidthPx: Int,
        val pageHeightPx: Int,
        val tileProvider: TileProvider
    ) : RenderContent
}

data class RenderMetrics(
    val renderTimeMs: Long,
    val cacheHit: Boolean
)


