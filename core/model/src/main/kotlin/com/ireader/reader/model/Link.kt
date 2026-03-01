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
     * fixed-layout（PDF）通常能给出 bounds；reflow 文本页不一定有
     */
    val bounds: List<NormalizedRect>? = null,
)
