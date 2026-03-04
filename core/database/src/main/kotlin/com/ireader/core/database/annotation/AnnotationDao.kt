package com.ireader.core.database.annotation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query(
        """
        SELECT * FROM annotations
        WHERE documentId = :documentId
        ORDER BY updatedAtEpochMs DESC
        """
    )
    fun observeByDocumentId(documentId: String): Flow<List<AnnotationEntity>>

    @Query(
        """
        SELECT * FROM annotations
        WHERE documentId = :documentId
        ORDER BY updatedAtEpochMs DESC
        """
    )
    suspend fun listByDocumentId(documentId: String): List<AnnotationEntity>

    @Query(
        """
        SELECT * FROM annotations
        WHERE documentId = :documentId
          AND anchorType = :anchorType
        ORDER BY updatedAtEpochMs DESC
        """
    )
    suspend fun listByDocumentIdAndAnchorType(
        documentId: String,
        anchorType: String
    ): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnnotationEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM annotations WHERE documentId = :documentId AND id = :id)")
    suspend fun exists(documentId: String, id: String): Boolean

    @Query("DELETE FROM annotations WHERE documentId = :documentId AND id = :id")
    suspend fun deleteById(documentId: String, id: String): Int
}
