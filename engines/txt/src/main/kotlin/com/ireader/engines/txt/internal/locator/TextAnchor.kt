package com.ireader.engines.txt.internal.locator

internal enum class TextAnchorAffinity {
    FORWARD,
    BACKWARD
}

internal data class TextAnchor(
    val utf16Offset: Long,
    val blockId: Int,
    val affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD,
    val revision: Int
)
