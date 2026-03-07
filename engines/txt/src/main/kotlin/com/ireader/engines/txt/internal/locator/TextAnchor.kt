package com.ireader.engines.txt.internal.locator

internal enum class TextAnchorAffinity {
    FORWARD,
    BACKWARD;

    val storageCode: String
        get() = when (this) {
            FORWARD -> "f"
            BACKWARD -> "b"
        }

    companion object {
        fun fromStorageCode(raw: String): TextAnchorAffinity? {
            return when (raw.lowercase()) {
                "f" -> FORWARD
                "b" -> BACKWARD
                else -> null
            }
        }
    }
}

internal data class TextAnchor(
    val utf16Offset: Long,
    val blockId: Int,
    val affinity: TextAnchorAffinity = TextAnchorAffinity.FORWARD,
    val revision: Int
)
