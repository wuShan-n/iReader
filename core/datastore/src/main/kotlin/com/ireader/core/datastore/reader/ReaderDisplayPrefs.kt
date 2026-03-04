package com.ireader.core.datastore.reader

enum class ReaderBackgroundPreset(val storageValue: String) {
    SYSTEM("system"),
    PAPER("paper"),
    WARM("warm"),
    GREEN("green"),
    DARK("dark"),
    NAVY("navy");

    companion object {
        fun fromStorageValue(raw: String?): ReaderBackgroundPreset {
            return entries.firstOrNull { it.storageValue == raw } ?: SYSTEM
        }
    }
}

data class ReaderDisplayPrefs(
    val brightness: Float = 0.35f,
    val useSystemBrightness: Boolean = true,
    val eyeProtection: Boolean = false,
    val nightMode: Boolean = false,
    val backgroundPreset: ReaderBackgroundPreset = ReaderBackgroundPreset.SYSTEM,
    val showReadingProgress: Boolean = true,
    val fullScreenMode: Boolean = true
)
