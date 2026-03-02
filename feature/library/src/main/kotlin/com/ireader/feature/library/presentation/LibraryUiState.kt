package com.ireader.feature.library.presentation

import com.ireader.core.data.book.LibrarySort
import com.ireader.core.database.book.BookEntity

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED
)
