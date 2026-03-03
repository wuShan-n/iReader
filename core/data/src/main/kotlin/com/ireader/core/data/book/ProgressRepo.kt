package com.ireader.core.data.book

import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ProgressRecord(
    val bookId: Long,
    val locatorJson: String,
    val progression: Double,
    val updatedAtEpochMs: Long
)

@Singleton
class ProgressRepo @Inject constructor(
    private val progressDao: ProgressDao
) {
    suspend fun getByBookId(bookId: Long): ProgressRecord? = progressDao.getByBookId(bookId)?.toRecord()

    fun observeByBookId(bookId: Long): Flow<ProgressRecord?> = progressDao.observeByBookId(bookId).map { it?.toRecord() }

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

private fun ProgressEntity.toRecord(): ProgressRecord =
    ProgressRecord(
        bookId = bookId,
        locatorJson = locatorJson,
        progression = progression,
        updatedAtEpochMs = updatedAtEpochMs
    )
