package com.ireader.feature.library.presentation

import com.ireader.core.data.book.LibraryBookItem
import com.ireader.core.data.book.CollectionItem
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.LibrarySort
import com.ireader.core.data.book.ReadingStatus

data class LibraryUiState(
    val books: List<LibraryBookItem> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val keyword: String = "",
    val statuses: Set<ReadingStatus> = emptySet(),
    val indexStates: Set<IndexState> = emptySet(),
    val onlyFavorites: Boolean = false,
    val selectedCollectionId: Long? = null,
    val collections: List<CollectionItem> = emptyList(),
    val activeImportJobId: String? = null,
    val importStatusText: String? = null
)
