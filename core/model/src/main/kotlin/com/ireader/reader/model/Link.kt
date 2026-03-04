package com.ireader.reader.model

sealed interface LinkTarget {
    data class Internal(
        val locator: Locator,
    ) : LinkTarget

    data class External(
        val url: String,
    ) : LinkTarget
}

data class DocumentLink(
    val target: LinkTarget,
    val title: String? = null,
    /**
     * reflow 链接可选提供文本范围，便于文本页点击命中。
     */
    val range: LocatorRange? = null,
    /**
     * fixed-layout（PDF）通常能给出 bounds；reflow 文本页不一定有
     */
    val bounds: List<NormalizedRect>? = null,
)
