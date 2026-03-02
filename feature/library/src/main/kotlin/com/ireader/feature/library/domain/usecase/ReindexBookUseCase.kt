package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookIndexer
import javax.inject.Inject

class ReindexBookUseCase @Inject constructor(
    private val bookIndexer: BookIndexer
) {
    suspend operator fun invoke(bookId: Long) {
        bookIndexer.reindex(bookId)
    }
}
