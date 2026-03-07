package com.ireader.engines.common.android.reflow

data class ReflowPageSlice(
    val startOffset: Long,
    val endOffset: Long,
    val text: CharSequence,
    val continuesParagraph: Boolean = false
)
