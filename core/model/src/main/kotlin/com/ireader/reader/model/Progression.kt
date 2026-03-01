package com.ireader.reader.model

data class Progression(
    // 0.0 ~ 1.0
    val percent: Double,
    // UI 可直接展示： "12/320" / "45%" / "Chapter 3"
    val label: String? = null,
    // 当前页/章节内页等（可选）
    val current: Int? = null,
    // 总页/总章节等（可选）
    val total: Int? = null,
)
