package com.ireader.core.database.book

import androidx.room.Embedded

data class LibraryBookRow(
    @Embedded val book: BookEntity,
    val progression: Double?,
    val progressUpdatedAtEpochMs: Long?
)
