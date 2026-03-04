package com.ireader.feature.reader.ui

internal enum class ReaderTopActionIcon {
    Search,
    Note
}

internal object ReaderChromeDefaults {
    val topSearchIcon: ReaderTopActionIcon = ReaderTopActionIcon.Search
    val topNotesIcon: ReaderTopActionIcon = ReaderTopActionIcon.Note
}
