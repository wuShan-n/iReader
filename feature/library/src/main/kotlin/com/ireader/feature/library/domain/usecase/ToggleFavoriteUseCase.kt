package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val bookRepo: BookRepo
) {
    suspend operator fun invoke(bookId: Long, current: Boolean) {
        bookRepo.setFavorite(bookId = bookId, favorite = !current)
    }
}
