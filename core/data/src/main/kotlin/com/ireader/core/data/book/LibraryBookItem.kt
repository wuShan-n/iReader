package com.ireader.core.data.book

data class LibraryBookItem(
    val book: BookRecord,
    val progression: Double,
    val progressUpdatedAtEpochMs: Long?
)
