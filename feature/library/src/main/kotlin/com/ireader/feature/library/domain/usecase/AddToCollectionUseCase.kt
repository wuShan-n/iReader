package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import javax.inject.Inject

class AddToCollectionUseCase @Inject constructor(
    private val bookRepo: BookRepo
) {
    suspend operator fun invoke(bookId: Long, collectionName: String) {
        val collectionId = bookRepo.createCollection(collectionName)
        bookRepo.addToCollection(bookId = bookId, collectionId = collectionId)
    }
}
