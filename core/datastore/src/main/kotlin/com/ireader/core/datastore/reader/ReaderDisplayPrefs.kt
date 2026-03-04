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

enum class TapZonePreset(val storageValue: String) {
    CLASSIC_3_ZONE("classic_3_zone"),
    SAFE_CENTER("safe_center"),
    LEFT_HAND("left_hand"),
    RIGHT_HAND("right_hand");

    companion object {
        fun fromStorageValue(raw: String?): TapZonePreset {
            return entries.firstOrNull { it.storageValue == raw } ?: CLASSIC_3_ZONE
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
    val fullScreenMode: Boolean = true,
    val tapZonePreset: TapZonePreset = TapZonePreset.CLASSIC_3_ZONE,
    val preventAccidentalTurn: Boolean = true
)
