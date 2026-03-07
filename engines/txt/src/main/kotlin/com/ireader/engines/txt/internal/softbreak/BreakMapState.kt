package com.ireader.engines.txt.internal.softbreak

internal enum class BreakMapState(val storageCode: Int) {
    HARD_PARAGRAPH(0),
    SOFT_JOIN(1),
    SOFT_SPACE(2),
    PRESERVE(3),
    UNKNOWN(4);

    val isSoft: Boolean
        get() = this == SOFT_JOIN || this == SOFT_SPACE

    val emitsVisibleNewline: Boolean
        get() = this == HARD_PARAGRAPH || this == PRESERVE || this == UNKNOWN

    companion object {
        fun fromStorageCode(code: Int): BreakMapState {
            return entries.firstOrNull { it.storageCode == code } ?: HARD_PARAGRAPH
        }
    }
}
