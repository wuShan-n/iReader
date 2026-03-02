package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LibrarySort
import com.ireader.core.database.book.BookEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LoadLibraryUseCase @Inject constructor(
    private val bookRepo: BookRepo
) {
    operator fun invoke(sort: LibrarySort): Flow<List<BookEntity>> = bookRepo.observeLibrary(sort)
}
