package com.ireader.reader.model

/**
 * scheme + value 统一表达位置。
 * - TXT: txt.offset -> value=字符偏移(建议) 或字节偏移(需固定编码策略)
 * - EPUB: epub.cfi  -> value=CFI 字符串
 * - PDF: pdf.page   -> value=页索引(0-based 建议) 或 1-based(需统一)
 */
data class Locator(
    val scheme: String,
    val value: String,
    val extras: Map<String, String> = emptyMap(),
)

data class LocatorRange(
    val start: Locator,
    val end: Locator,
    val extras: Map<String, String> = emptyMap(),
)

object LocatorSchemes {
    const val TXT_OFFSET = "txt.offset"
    const val EPUB_CFI = "epub.cfi"
    const val PDF_PAGE = "pdf.page"

    /**
     * 可选：当你对 reflow 做“稳定分页”缓存时使用（与 LayoutConstraints+RenderConfig 强绑定）
     */
    const val REFLOW_PAGE = "reflow.page"
}

/**
 * 可选：Locator 归一化接口（同一引擎内部建议实现）
 * 比如把 "page=12" 的各种写法统一成 0-based，或者把 EPUB href#anchor 解析成 CFI。
 */
interface LocatorNormalizer {
    suspend fun normalize(locator: Locator): Locator
}
