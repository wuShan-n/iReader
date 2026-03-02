package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookMaintenanceScheduler
import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.book.IndexState
import javax.inject.Inject

class ReindexBookUseCase @Inject constructor(
    private val bookRepo: BookRepo,
    private val scheduler: BookMaintenanceScheduler
) {
    suspend operator fun invoke(bookId: Long) {
        if (bookId <= 0L) return
        bookRepo.setIndexState(bookId = bookId, state = IndexState.PENDING, error = null)
        scheduler.enqueueReindex(listOf(bookId))
    }
}
