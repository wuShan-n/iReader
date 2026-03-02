package com.ireader.engines.txt.internal.pagination

internal data class PageSlice(
    val startOffset: Long,
    val endOffset: Long,
    val text: CharSequence
)

