package com.ireader.engines.txt.internal.render

internal data class TxtPageSlice(
    val startOffset: Long,
    val endOffset: Long,
    val text: String,
    val continuesParagraph: Boolean,
    val projectedBoundaryToRawOffsets: LongArray
)
