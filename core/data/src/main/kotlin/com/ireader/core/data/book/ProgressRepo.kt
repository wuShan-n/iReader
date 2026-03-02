package com.ireader.core.data.book

import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ProgressRepo @Inject constructor(
    private val progressDao: ProgressDao
) {
    suspend fun getByBookId(bookId: Long): ProgressEntity? = progressDao.getByBookId(bookId)

    fun observeByBookId(bookId: Long): Flow<ProgressEntity?> = progressDao.observeByBookId(bookId)

    suspend fun upsert(bookId: Long, locatorJson: String, progression: Double, updatedAtEpochMs: Long) {
        progressDao.upsert(
            ProgressEntity(
                bookId = bookId,
                locatorJson = locatorJson,
                progression = progression.coerceIn(0.0, 1.0),
                updatedAtEpochMs = updatedAtEpochMs
            )
        )
    }

    suspend fun deleteByBookId(bookId: Long) {
        progressDao.deleteByBookId(bookId)
    }
}
