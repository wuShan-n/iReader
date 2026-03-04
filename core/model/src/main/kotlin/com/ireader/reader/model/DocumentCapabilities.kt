package com.ireader.reader.model

data class DocumentCapabilities(
    // TXT/EPUB = true
    val reflowable: Boolean,
    // PDF = true
    val fixedLayout: Boolean,
    val outline: Boolean,
    val search: Boolean,
    // 复制/分享/导出
    val textExtraction: Boolean,
    // 能否把标注映射到页面（reflow/fixed）
    val annotations: Boolean,
    // 是否支持选区能力
    val selection: Boolean,
    // 是否能提供可点击链接信息（内部/外部）
    val links: Boolean,
)
