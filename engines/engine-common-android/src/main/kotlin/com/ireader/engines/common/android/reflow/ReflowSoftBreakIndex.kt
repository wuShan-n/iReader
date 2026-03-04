package com.ireader.engines.common.android.reflow

/**
 * Optional newline index for reflow pagination.
 *
 * Used to distinguish hard paragraph breaks from soft-wrapped line breaks.
 */
fun interface ReflowSoftBreakIndex {
    fun forEachNewlineInRange(
        startChar: Long,
        endChar: Long,
        consumer: (offset: Long, isSoft: Boolean) -> Unit
    )
}

