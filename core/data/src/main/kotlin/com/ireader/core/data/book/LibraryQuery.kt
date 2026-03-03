package com.ireader.core.data.book

import com.ireader.reader.model.BookFormat

data class LibraryQuery(
    val keyword: String? = null,
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val statuses: Set<ReadingStatus> = emptySet(),
    val indexStates: Set<IndexState> = emptySet(),
    val formats: Set<BookFormat> = emptySet(),
    val onlyFavorites: Boolean = false,
    val collectionId: Long? = null
)
