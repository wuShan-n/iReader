package com.ireader.core.database.importing

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun get(jobId: String): ImportJobEntity?

    @Query("SELECT * FROM import_jobs WHERE jobId = :jobId LIMIT 1")
    fun observe(jobId: String): Flow<ImportJobEntity?>

    @Query(
        "UPDATE import_jobs " +
            "SET status = :status, total = :total, done = :done, currentTitle = :currentTitle, " +
            "errorMessage = :errorMessage, updatedAtEpochMs = :updatedAt " +
            "WHERE jobId = :jobId"
    )
    suspend fun updateProgress(
        jobId: String,
        status: ImportStatus,
        total: Int,
        done: Int,
        currentTitle: String?,
        errorMessage: String?,
        updatedAt: Long
    )
}
