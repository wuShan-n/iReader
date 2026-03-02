package com.ireader.feature.library.presentation

import com.ireader.core.data.book.LibraryBookItem
import com.ireader.core.data.book.LibrarySort
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.collection.CollectionEntity

data class LibraryUiState(
    val books: List<LibraryBookItem> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val keyword: String = "",
    val statuses: Set<ReadingStatus> = emptySet(),
    val indexStates: Set<IndexState> = emptySet(),
    val onlyFavorites: Boolean = false,
    val selectedCollectionId: Long? = null,
    val collections: List<CollectionEntity> = emptyList(),
    val activeImportJobId: String? = null,
    val importStatusText: String? = null
)
