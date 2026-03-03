package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.ReadingStatus
import javax.inject.Inject

class UpdateReadingStatusUseCase @Inject constructor(
    private val bookRepo: BookRepo
) {
    suspend operator fun invoke(bookId: Long, status: ReadingStatus) {
        bookRepo.setReadingStatus(bookId = bookId, status = status)
    }
}
