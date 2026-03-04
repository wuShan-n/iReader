package com.ireader.feature.reader.ui

internal enum class ReaderTopActionIcon {
    Search,
    Note
}

internal data class ReaderNightModeEntryPolicy(
    val showBottomBarToggle: Boolean,
    val showSettingsDockToggle: Boolean,
    val showFullSettingsToggle: Boolean
)

internal object ReaderChromeDefaults {
    val topSearchIcon: ReaderTopActionIcon = ReaderTopActionIcon.Search
    val topNotesIcon: ReaderTopActionIcon = ReaderTopActionIcon.Note
    val nightModeEntryPolicy: ReaderNightModeEntryPolicy = ReaderNightModeEntryPolicy(
        showBottomBarToggle = true,
        showSettingsDockToggle = false,
        showFullSettingsToggle = false
    )
}
