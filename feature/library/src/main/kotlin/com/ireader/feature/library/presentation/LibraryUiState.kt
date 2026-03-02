package com.ireader.feature.library.presentation

import com.ireader.core.data.book.LibraryBookItem
import com.ireader.core.data.book.LibrarySort

data class LibraryUiState(
    val books: List<LibraryBookItem> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val keyword: String = ""
)
