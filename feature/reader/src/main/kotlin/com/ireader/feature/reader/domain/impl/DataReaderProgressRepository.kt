package com.ireader.feature.reader.domain.impl

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LocatorJsonCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.database.book.ReadingStatus
import com.ireader.feature.reader.domain.ReaderProgressRepository
import com.ireader.reader.model.Locator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataReaderProgressRepository @Inject constructor(
    private val progressRepo: ProgressRepo,
    private val bookRepo: BookRepo
) : ReaderProgressRepository {

    override suspend fun getLastLocator(bookId: Long): Locator? {
        val progress = progressRepo.getByBookId(bookId) ?: return null
        return LocatorJsonCodec.decode(progress.locatorJson)
    }

    override suspend fun saveProgress(bookId: Long, locator: Locator, progression: Double) {
        val normalized = progression.coerceIn(0.0, 1.0)
        val now = System.currentTimeMillis()
        progressRepo.upsert(
            bookId = bookId,
            locatorJson = LocatorJsonCodec.encode(locator),
            progression = normalized,
            updatedAtEpochMs = now
        )

        val status = when {
            normalized >= 0.99 -> ReadingStatus.FINISHED
            normalized > 0.0 -> ReadingStatus.READING
            else -> ReadingStatus.UNREAD
        }
        bookRepo.setReadingStatus(bookId = bookId, status = status)
    }
}

