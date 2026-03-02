package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LibraryBookItem
import com.ireader.core.data.book.LibraryQuery
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LoadLibraryUseCase @Inject constructor(
    private val bookRepo: BookRepo
) {
    operator fun invoke(query: LibraryQuery): Flow<List<LibraryBookItem>> = bookRepo.observeLibrary(query)
}
