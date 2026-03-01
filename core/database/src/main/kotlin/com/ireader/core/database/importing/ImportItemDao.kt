package com.ireader.core.database.importing

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ImportItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ImportItemEntity>)

    @Query("SELECT * FROM import_items WHERE jobId = :jobId ORDER BY updatedAtEpochMs ASC")
    suspend fun list(jobId: String): List<ImportItemEntity>

    @Query(
        "SELECT * FROM import_items " +
            "WHERE jobId = :jobId AND status IN ('PENDING', 'FAILED') " +
            "ORDER BY updatedAtEpochMs ASC"
    )
    suspend fun listPendingOrFailed(jobId: String): List<ImportItemEntity>

    @Query(
        "UPDATE import_items " +
            "SET status = :status, bookId = :bookId, fingerprintSha256 = :fingerprint, " +
            "errorCode = :errorCode, errorMessage = :errorMessage, updatedAtEpochMs = :updatedAt " +
            "WHERE jobId = :jobId AND uri = :uri"
    )
    suspend fun update(
        jobId: String,
        uri: String,
        status: ImportItemStatus,
        bookId: String?,
        fingerprint: String?,
        errorCode: String?,
        errorMessage: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM import_items WHERE jobId = :jobId")
    suspend fun deleteByJob(jobId: String)
}
