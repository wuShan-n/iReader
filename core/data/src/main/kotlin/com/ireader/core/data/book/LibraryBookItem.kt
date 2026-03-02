package com.ireader.core.data.book

import com.ireader.core.database.book.BookEntity

data class LibraryBookItem(
    val book: BookEntity,
    val progression: Double,
    val progressUpdatedAtEpochMs: Long?
)
