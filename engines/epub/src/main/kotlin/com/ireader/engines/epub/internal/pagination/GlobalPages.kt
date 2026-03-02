package com.ireader.engines.epub.internal.pagination

internal data class GlobalPages(
    val sig: Int,
    val pagesBySpine: IntArray,
    val prefix: IntArray,
    val total: Int
)
