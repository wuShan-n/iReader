package com.ireader.feature.reader.domain.usecase

import com.ireader.feature.reader.domain.ReaderProgressRepository
import com.ireader.reader.model.Locator
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveReadingProgress @Inject constructor(
    private val repository: ReaderProgressRepository
) {
    suspend operator fun invoke(bookId: Long, locator: Locator, progression: Double) {
        withContext(Dispatchers.IO) {
            repository.saveProgress(
                bookId = bookId,
                locator = locator,
                progression = progression
            )
        }
    }
}

