package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.files.storage.BookStorage
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeleteBookUseCase @Inject constructor(
    private val bookRepo: BookRepo,
    private val storage: BookStorage
) {
    suspend operator fun invoke(bookId: String) = withContext(Dispatchers.IO) {
        bookRepo.deleteById(bookId)
        runCatching { storage.deleteBookFiles(bookId) }
    }
}
