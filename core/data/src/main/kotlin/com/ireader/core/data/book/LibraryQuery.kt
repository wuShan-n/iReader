package com.ireader.core.data.book

import com.ireader.core.database.book.ReadingStatus

data class LibraryQuery(
    val keyword: String? = null,
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val statuses: Set<ReadingStatus> = emptySet(),
    val onlyFavorites: Boolean = false,
    val collectionId: Long? = null
)
